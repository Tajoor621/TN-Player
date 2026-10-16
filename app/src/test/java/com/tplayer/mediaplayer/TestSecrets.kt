package com.tplayer.mediaplayer
import com.tplayer.mediaplayer.security.AesGcmCodec
import javax.crypto.KeyGenerator
internal fun testCodec() = AesGcmCodec(
    KeyGenerator.getInstance("AES").apply { init(256) }.generateKey(),
    KeyGenerator.getInstance("HmacSHA256").apply { init(256) }.generateKey())
