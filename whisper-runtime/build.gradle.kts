import java.net.URL
import java.security.MessageDigest

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val whisperRevision = "2eeeba56e9edd762b4b38467bab96c2517163158" // official v1.8.3
val whisperArchiveSha256 = "089b898aa83b24a8321e0fd554eeb0967fb03dd687e27f6374c72d3363b5b429"
val whisperSource = layout.buildDirectory.dir("upstream/whisper-$whisperRevision")
val prepareWhisperSources by tasks.registering {
    group = "build setup"
    description = "Downloads the pinned official whisper.cpp source and verifies SHA-256."
    inputs.property("revision", whisperRevision)
    inputs.property("sha256", whisperArchiveSha256)
    outputs.dir(whisperSource)
    doLast {
        val archive = layout.buildDirectory.file("upstream/whisper-$whisperRevision.tar.gz").get().asFile
        archive.parentFile.mkdirs()
        fun digest(file: File): String {
            val md = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(1024 * 1024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    md.update(buffer, 0, count)
                }
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
        if (!archive.exists() || digest(archive) != whisperArchiveSha256) {
            val partial = File(archive.path + ".part")
            try {
                URL("https://codeload.github.com/ggml-org/whisper.cpp/tar.gz/$whisperRevision")
                    .openConnection().apply { connectTimeout = 30_000; readTimeout = 120_000 }
                    .getInputStream().use { input -> partial.outputStream().use { input.copyTo(it) } }
                check(digest(partial) == whisperArchiveSha256) { "whisper.cpp source checksum mismatch" }
                partial.copyTo(archive, overwrite = true)
            } finally { partial.delete() }
        }
        delete(whisperSource)
        copy {
            from(tarTree(resources.gzip(archive)))
            into(whisperSource)
            eachFile { path = path.substringAfter('/') }
            includeEmptyDirs = false
        }
        check(whisperSource.get().file("include/whisper.h").asFile.isFile)
    }
}

android {
    namespace = "com.nameemrooz.whisper"
    compileSdk = 35
    ndkVersion = "28.2.13676358"
    defaultConfig {
        minSdk = 26
        ndk { abiFilters += providers.gradleProperty("emroozAbi").getOrElse("arm64-v8a").split(",") }
        externalNativeBuild {
            cmake {
                arguments += listOf(
                    "-DWHISPER_SOURCE_DIR=${whisperSource.get().asFile.absolutePath}",
                    "-DANDROID_STL=c++_static",
                )
            }
        }
        consumerProguardFiles("consumer-rules.pro")
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }

tasks.configureEach {
    if (name.startsWith("configureCMake") || name.startsWith("buildCMake") || name == "preBuild") {
        dependsOn(prepareWhisperSources)
    }
}
