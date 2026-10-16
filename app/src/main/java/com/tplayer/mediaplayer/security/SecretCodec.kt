package com.tplayer.mediaplayer.security

import android.content.Context
import android.os.Build
import android.security.KeyPairGeneratorSpec
import android.util.AtomicFile
import android.util.Base64
import androidx.annotation.RequiresApi
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.SecureRandom
import java.util.Calendar
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.security.auth.x500.X500Principal

interface SecretCodec {
    fun encrypt(value: String, purpose: String): String
    fun decrypt(value: String, purpose: String): String
    fun identifier(value: String): String
}

/** Randomized, authenticated encryption. A separate HMAC key identifies URLs without exposing them. */
class AesGcmCodec(private val aes: SecretKey, private val hmac: SecretKey) : SecretCodec {
    override fun encrypt(value: String, purpose: String): String {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, aes)
        cipher.updateAAD(purpose.toByteArray(Charsets.UTF_8))
        val encoded = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        return "v1." + Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + "." + Base64.encodeToString(encoded, Base64.NO_WRAP)
    }
    override fun decrypt(value: String, purpose: String): String {
        val parts = value.split('.')
        require(parts.size == 3 && parts[0] == "v1") { "Unsupported encrypted data." }
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        require(iv.size == 12) { "Invalid encrypted data." }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, aes, GCMParameterSpec(128, iv))
        cipher.updateAAD(purpose.toByteArray(Charsets.UTF_8))
        return String(cipher.doFinal(Base64.decode(parts[2], Base64.NO_WRAP)), Charsets.UTF_8)
    }
    override fun identifier(value: String): String = Mac.getInstance("HmacSHA256").run {
        init(hmac)
        doFinal(value.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it.toInt() and 255) }
    }
}

/** No plaintext fallback: invalidated or unavailable keys surface an error to the caller. */
class AndroidSecretCodec(context: Context) : SecretCodec {
    private val app = context.applicationContext
    private val delegate: SecretCodec by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        synchronized(keyLock) { if (Build.VERSION.SDK_INT >= 23) ModernKeys.codec() else legacyCodec() }
    }
    override fun encrypt(value: String, purpose: String) = delegate.encrypt(value, purpose)
    override fun decrypt(value: String, purpose: String) = delegate.decrypt(value, purpose)
    override fun identifier(value: String) = delegate.identifier(value)

    @Suppress("DEPRECATION")
    private fun legacyCodec(): SecretCodec {
        val alias = "tplayer.keys.rsa.v1"
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        val file = AtomicFile(File(app.noBackupFilesDir, "wrapped-keys.v1"))
        val existing = file.baseFile.exists()
        check(!existing || store.containsAlias(alias)) { "Saved keys are unavailable. Re-add saved sources after resetting secure data." }
        if (!store.containsAlias(alias)) {
            val start = Calendar.getInstance(); val end = Calendar.getInstance().apply { add(Calendar.YEAR, 30) }
            val spec = KeyPairGeneratorSpec.Builder(app).setAlias(alias).setKeySize(2048)
                .setSubject(X500Principal("CN=TN-Player local keys")).setSerialNumber(BigInteger.ONE)
                .setStartDate(start.time).setEndDate(end.time).build()
            KeyPairGenerator.getInstance("RSA", "AndroidKeyStore").apply { initialize(spec); generateKeyPair() }
        }
        val material: ByteArray = if (existing) {
            Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
                init(Cipher.DECRYPT_MODE, store.getKey(alias, null)); doFinal(file.readFully())
            }
        } else {
            val bytes = ByteArray(64).also { SecureRandom().nextBytes(it) }
            val encrypted = Cipher.getInstance("RSA/ECB/PKCS1Padding").run {
                init(Cipher.ENCRYPT_MODE, store.getCertificate(alias).publicKey); doFinal(bytes)
            }
            val output = file.startWrite()
            try { output.write(encrypted); file.finishWrite(output) } catch (e: Exception) { file.failWrite(output); throw e }
            bytes
        }
        check(material.size == 64) { "Invalid local key data." }
        val codec = AesGcmCodec(SecretKeySpec(material.copyOfRange(0, 32), "AES"), SecretKeySpec(material.copyOfRange(32, 64), "HmacSHA256"))
        material.fill(0)
        return codec
    }
    companion object { private val keyLock = Any() }
    @RequiresApi(23)
    private object ModernKeys {
        fun codec(): SecretCodec {
            val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val aesAlias = "tplayer.keys.aes.v1"; val hmacAlias = "tplayer.keys.hmac.v1"
            if (!store.containsAlias(aesAlias)) {
                val spec = android.security.keystore.KeyGenParameterSpec.Builder(aesAlias,
                    android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256).setRandomizedEncryptionRequired(true).build()
                KeyGenerator.getInstance("AES", "AndroidKeyStore").apply { init(spec); generateKey() }
            }
            if (!store.containsAlias(hmacAlias)) {
                val spec = android.security.keystore.KeyGenParameterSpec.Builder(hmacAlias,
                    android.security.keystore.KeyProperties.PURPOSE_SIGN or android.security.keystore.KeyProperties.PURPOSE_VERIFY)
                    .setDigests(android.security.keystore.KeyProperties.DIGEST_SHA256).setKeySize(256).build()
                KeyGenerator.getInstance("HmacSHA256", "AndroidKeyStore").apply { init(spec); generateKey() }
            }
            return AesGcmCodec(store.getKey(aesAlias, null) as SecretKey, store.getKey(hmacAlias, null) as SecretKey)
        }
    }
}
