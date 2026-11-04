package com.tplayer.mediaplayer.security

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.util.AtomicFile
import android.util.Base64
import com.tplayer.mediaplayer.data.LibraryItem
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/** Protects a folder inside TN-Player. It never copies, moves or encrypts the original media bytes. */
data class PrivateRoot(val uri: String, val volume: String, val path: String, val filePath: String? = null)
object PrivatePaths {
    fun volume(value: String) = if (value.lowercase() in setOf("primary", "external", "external_primary")) "primary" else value.lowercase()
    fun path(value: String) = value.replace('\\', '/').split('/').filter { it.isNotBlank() && it != "." }.also { require(".." !in it) }.joinToString("/")
    fun contains(root: PrivateRoot, volume: String, path: String): Boolean {
        if (this.volume(root.volume) != this.volume(volume)) return false
        val parent = this.path(root.path).lowercase(); val child = this.path(path).lowercase()
        return parent.isEmpty() || child == parent || child.startsWith("$parent/")
    }
    fun pinHash(pin: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(pin, salt, 150000, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1").generateSecret(spec).encoded } finally { spec.clearPassword() }
    }
}
class PrivateFolder private constructor(context: Context, private val codec: SecretCodec) {
    private val app = context.applicationContext
    private val file = AtomicFile(File(app.noBackupFilesDir, "private-folder.v1"))
    @Volatile private var loaded = false
    @Volatile private var root: PrivateRoot? = null
    private var salt = ByteArray(0); private var hash = ByteArray(0); private var failures = 0; private var blockedUntil = 0L
    private val access = MutableStateFlow(false)
    val unlocked = access.asStateFlow()
    val publicClassification = ConcurrentHashMap<String, Boolean>()
    private val lock = Any()
    @Volatile var generation = 0L; private set
    fun lock() { access.value = false; generation++ }
    fun isUnlocked() = access.value
    @Suppress("DEPRECATION")
    fun location(file: File): Pair<String, String> {
        val path = file.canonicalPath
        val primary = Environment.getExternalStorageDirectory().canonicalPath
        return when {
            path == primary || path.startsWith("$primary/") -> "primary" to path.removePrefix(primary).trim('/')
            path.startsWith("/storage/") -> path.removePrefix("/storage/").substringBefore('/') to path.removePrefix("/storage/").substringAfter('/', "")
            else -> "absolute" to path.trim('/')
        }
    }
    suspend fun configuration(): PrivateRoot? = withContext(Dispatchers.IO) { synchronized(lock) {
        if (!loaded) {
            if (file.baseFile.exists() || File(file.baseFile.path + ".bak").exists()) {
                require(file.baseFile.length() <= 256 * 1024 && File(file.baseFile.path + ".bak").length() <= 256 * 1024)
                val obj = JSONObject(codec.decrypt(file.readFully().toString(Charsets.UTF_8), "private-folder-v1"))
                require(obj.getInt("schema") == 1)
                root = PrivateRoot(obj.getString("uri"), obj.getString("volume"), obj.getString("path"), obj.optString("filePath").ifBlank { null })
                salt = Base64.decode(obj.getString("salt"), Base64.NO_WRAP); hash = Base64.decode(obj.getString("hash"), Base64.NO_WRAP)
                require(salt.size == 32 && hash.size == 32)
                failures = obj.optInt("failures"); blockedUntil = obj.optLong("blockedUntil")
            }
            loaded = true
        }
        root
    } }
    private fun persist() {
        val current = checkNotNull(root)
        val obj = JSONObject().put("schema", 1).put("uri", current.uri).put("volume", current.volume).put("path", current.path)
            .put("filePath", current.filePath.orEmpty()).put("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .put("hash", Base64.encodeToString(hash, Base64.NO_WRAP)).put("failures", failures).put("blockedUntil", blockedUntil)
        val bytes = codec.encrypt(obj.toString(), "private-folder-v1").toByteArray()
        val output = file.startWrite()
        try { output.write(bytes); file.finishWrite(output) } catch (e: Exception) { file.failWrite(output); throw e }
    }
    suspend fun configure(uri: Uri, pin: CharArray) = withContext(Dispatchers.IO) {
        require(pin.size in 6..32 && pin.all { it in '0'..'9' }) { "Use a PIN of 6–32 digits." }
        check(configuration() == null) { "Authenticate and remove the existing protection before choosing another folder." }
        val chosen = when {
            uri.scheme == "file" -> { val local = File(checkNotNull(uri.path)).canonicalFile; val identity = location(local); PrivateRoot(uri.toString(), identity.first, PrivatePaths.path(identity.second), local.path) }
            uri.authority == "com.android.externalstorage.documents" -> {
                val id = DocumentsContract.getTreeDocumentId(uri); require(id.contains(':'))
                PrivateRoot(uri.toString(), PrivatePaths.volume(id.substringBefore(':')), PrivatePaths.path(id.substringAfter(':')))
            }
            else -> error("Choose a local internal-storage or SD-card folder; opaque/cloud provider roots are not supported for private protection.")
        }
        synchronized(lock) {
            root = chosen; salt = ByteArray(32).also { SecureRandom().nextBytes(it) }; hash = PrivatePaths.pinHash(pin, salt)
            failures = 0; blockedUntil = 0
            try { persist() } catch (e: Exception) { root = null; throw e }
            loaded = true; access.value = false; publicClassification.clear(); generation++
        }
    }
    suspend fun authenticate(pin: CharArray): Boolean = withContext(Dispatchers.IO) {
        check(configuration() != null)
        synchronized(lock) {
            if (System.currentTimeMillis() < blockedUntil) return@synchronized false
            val matches = MessageDigest.isEqual(hash, PrivatePaths.pinHash(pin, salt))
            if (matches) { failures = 0; blockedUntil = 0 } else {
                failures++; if (failures >= 5) blockedUntil = System.currentTimeMillis() + (30000L shl (failures - 5).coerceAtMost(5))
            }
            persist(); access.value = matches; matches
        }
    }
    suspend fun removeProtection() = withContext(Dispatchers.IO) {
        check(isUnlocked()) { "Authenticate first." }
        synchronized(lock) { file.delete(); check(!file.baseFile.exists() && !File(file.baseFile.path + ".bak").exists()) { "Cannot remove protection record." }; root = null; loaded = true; access.value = false; publicClassification.clear(); generation++ }
    }
    fun matchesLocation(root: PrivateRoot?, volume: String, path: String): Boolean {
        if (root == null) return false
        val identity = if (path.startsWith("storage/") || path.startsWith("/storage/")) location(File("/" + path.trimStart('/'))) else volume to path
        return PrivatePaths.contains(root, identity.first, identity.second)
    }
    /** Only proven public URIs may start a thumbnail. This method performs no I/O. */
    fun hasProtection() = root != null
    fun hideThumbnail(uri: String) = !loaded || (root != null && publicClassification[uri] != true)
    suspend fun isPrivate(value: String): Boolean = withContext(Dispatchers.IO) {
        val uri = Uri.parse(value)
        if (uri.scheme == "tnprivate") return@withContext true
        val current = configuration() ?: return@withContext false.also { publicClassification[value] = true }
        if (uri.scheme !in setOf("content", "file")) return@withContext false
        val protected = try {
            when {
                uri.scheme == "file" -> location(File(checkNotNull(uri.path))).let { PrivatePaths.contains(current, it.first, it.second) }
                uri.authority == "${app.packageName}.fileprovider" && uri.pathSegments.firstOrNull() == "exports" -> {
                    val local = File(app.cacheDir, uri.pathSegments.joinToString("/")); location(local).let { PrivatePaths.contains(current, it.first, it.second) }
                }
                uri.authority == "com.android.externalstorage.documents" -> {
                    val id = if (DocumentsContract.isDocumentUri(app, uri)) DocumentsContract.getDocumentId(uri) else DocumentsContract.getTreeDocumentId(uri)
                    PrivatePaths.contains(current, id.substringBefore(':'), id.substringAfter(':', ""))
                }
                uri.authority == "media" || uri.authority == "downloads" || uri.authority == "com.android.providers.downloads.documents" -> {
                    val projection = if (android.os.Build.VERSION.SDK_INT >= 29 && uri.authority == "media") arrayOf("volume_name", "relative_path", "_display_name") else arrayOf("_data")
                    app.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                        if (!cursor.moveToFirst()) return@use true
                        if (projection.size == 1) {
                            val path = cursor.getString(0) ?: return@use true
                            location(File(path)).let { PrivatePaths.contains(current, it.first, it.second) }
                        } else PrivatePaths.contains(current, cursor.getString(0).orEmpty(), cursor.getString(1).orEmpty() + cursor.getString(2).orEmpty())
                    } ?: true
                }
                else -> true // Unknown local aliases fail closed while a private root is configured.
            }
        } catch (_: Exception) { true }
        publicClassification[value] = !protected
        protected
    }
    suspend fun publicItems(items: List<LibraryItem>): List<LibraryItem> = items.filter { !isPrivate(it.uri) }
    suspend fun authorize(uri: String): Boolean = !isPrivate(uri) || isUnlocked()
    fun privateId(uri: String) = "tnprivate:" + codec.identifier(uri)
    companion object {
        @Volatile private var instance: PrivateFolder? = null
        fun get(context: Context): PrivateFolder = instance ?: synchronized(this) {
            instance ?: PrivateFolder(context.applicationContext, AndroidSecretCodec(context.applicationContext)).also { instance = it }
        }
        fun testInstance(context: Context, codec: SecretCodec) = PrivateFolder(context, codec)
    }
}
