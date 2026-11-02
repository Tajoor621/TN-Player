plugins { id("com.android.library") }
android {
    namespace = "com.tplayer.softwarecodecs"
    compileSdk = 34
    defaultConfig { minSdk = 21; consumerProguardFiles("consumer-rules.pro") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
dependencies {
    implementation("androidx.media3:media3-decoder:1.4.1")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    compileOnly("org.checkerframework:checker-qual:3.33.0")
}
val verifyNativeCodecs by tasks.registering {
    doLast {
        for (abi in listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64")) {
            for (library in listOf("ffmpegJNI", "vpxV2JNI", "gav1JNI")) {
                check(file("src/main/jniLibs/$abi/lib$library.so").isFile) {
                    "Missing $abi/$library. Run bash scripts/build-native-codecs.sh with the documented NDK first."
                }
            }
        }
    }
}
tasks.named("preBuild").configure { dependsOn(verifyNativeCodecs) }
