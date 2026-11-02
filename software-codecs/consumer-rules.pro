
# Proguard rules specific to the FFmpeg extension.

# This prevents the names of native methods from being obfuscated.
-keepclasseswithmembernames class * {
    native <methods>;
}

# This method is called from native code
-keep, includedescriptorclasses class androidx.media3.decoder.ffmpeg.FfmpegAudioDecoder {
  private java.nio.ByteBuffer growOutputBuffer(androidx.media3.decoder.SimpleDecoderOutputBuffer, int);
}

# Proguard rules specific to the VP9 extension.

# This prevents the names of native methods from being obfuscated.
-keepclasseswithmembernames class * {
    native <methods>;
}

# Some members of this class are being accessed from native methods. Keep them unobfuscated.
-keep class androidx.media3.decoder.VideoDecoderOutputBuffer {
    *;
}


# Proguard rules specific to the AV1 extension.

# This prevents the names of native methods from being obfuscated.
-keepclasseswithmembernames class * {
    native <methods>;
}

# Some members of this class are being accessed from native methods. Keep them unobfuscated.
-keep class androidx.media3.decoder.VideoDecoderOutputBuffer {
  *;
}
# DefaultRenderersFactory discovers these entry points by their stable names.
-keep class androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer { public <init>(...); }
-keep class androidx.media3.decoder.vp9.LibvpxVideoRenderer { public <init>(...); }
-keep class androidx.media3.decoder.av1.Libgav1VideoRenderer { public <init>(...); }
