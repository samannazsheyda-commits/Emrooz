import java.net.URL
import java.security.MessageDigest

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.kapt")
}

android {
    namespace = "com.nameemrooz.journal"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.nameemrooz.journal"
        minSdk = 26
        targetSdk = 35
        versionCode = 39
        versionName = "2.2.3"
        manifestPlaceholders["applicationLabel"] = "امروز"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        if (providers.gradleProperty("emroozPreview").isPresent) {
            applicationId = providers.gradleProperty("emroozPreviewApplicationId").getOrElse("com.nameemrooz.journal.preview")
        }

        ndk {
            abiFilters += listOf(providers.gradleProperty("emroozAbi").getOrElse("arm64-v8a"))
        }
    }

    // A private persistent preview key may be supplied without putting secrets in git.
    providers.environmentVariable("EMROOZ_PREVIEW_STORE_FILE").orNull?.let { previewStore ->
        signingConfigs.getByName("debug") {
            storeFile = file(previewStore)
            storePassword = providers.environmentVariable("EMROOZ_PREVIEW_STORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("EMROOZ_PREVIEW_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("EMROOZ_PREVIEW_KEY_PASSWORD").get()
        }
    }

    // Publishing must use a private, persistent release key, never the CI debug key.
    providers.environmentVariable("EMROOZ_RELEASE_STORE_FILE").orNull?.let { releaseStore ->
        signingConfigs.create("publication") {
            storeFile = file(releaseStore)
            storePassword = providers.environmentVariable("EMROOZ_RELEASE_STORE_PASSWORD").get()
            keyAlias = providers.environmentVariable("EMROOZ_RELEASE_KEY_ALIAS").get()
            keyPassword = providers.environmentVariable("EMROOZ_RELEASE_KEY_PASSWORD").get()
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        release {
            isDebuggable = false
            isJniDebuggable = false
            signingConfig = if (providers.gradleProperty("emroozPreview").isPresent) {
                signingConfigs.getByName("debug")
            } else {
                signingConfigs.findByName("publication")
            }
        }
    }

    compileOptions {
        sourceCompatibility = org.gradle.api.JavaVersion.VERSION_17
        targetCompatibility = org.gradle.api.JavaVersion.VERSION_17
    }

    buildFeatures { compose = true; buildConfig = true }

    androidResources {
        noCompress += "onnx"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.useLegacyPackaging = false
        jniLibs.pickFirsts += "**/libonnxruntime.so"
    }
}

val modelDir = layout.projectDirectory.dir("src/main/assets/models/shenava_v15_rnnt_int8")

data class ModelAsset(val name: String, val url: String, val sha256: String)
val modelAssets = listOf(
    ModelAsset("encoder.int8.onnx", "https://huggingface.co/Reza2kn/Shenava-Koochik-v1.5-RNNT-sherpa-onnx/resolve/main/encoder.int8.onnx?download=true", "b9d975c1af77002f83897017e3adaaa6510148c6ba332df92b8d281369f2fdd3"),
    ModelAsset("decoder.int8.onnx", "https://huggingface.co/Reza2kn/Shenava-Koochik-v1.5-RNNT-sherpa-onnx/resolve/main/decoder.int8.onnx?download=true", "0adaad326a536dfbb30c67287e909b4e6f0775fccb8c3ca47270890364824947"),
    ModelAsset("joiner.int8.onnx", "https://huggingface.co/Reza2kn/Shenava-Koochik-v1.5-RNNT-sherpa-onnx/resolve/main/joiner.int8.onnx?download=true", "e441ed265c961ff4a2436aa8bc36638e65849445e7a1f65cf263f0c310593dde"),
    ModelAsset("tokens.txt", "https://huggingface.co/Reza2kn/Shenava-Koochik-v1.5-RNNT-sherpa-onnx/resolve/main/tokens.txt?download=true", "8e192963f6e666dfa5721e5cbd4710bc1ef592460a45f08cefc94b2db16a6954")
)

fun File.sha256(): String {
    val md = MessageDigest.getInstance("SHA-256")
    inputStream().buffered().use { input ->
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val n = input.read(buffer)
            if (n <= 0) break
            md.update(buffer, 0, n)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

val fetchShenavaRnntModel by tasks.registering {
    group = "model"
    description = "Downloads and verifies Shenava Koochik v1.5 RNNT INT8."
    outputs.dir(modelDir)
    doLast {
        val dir = modelDir.asFile
        dir.mkdirs()
        modelAssets.forEach { asset ->
            val out = File(dir, asset.name)
            if (!out.exists() || out.length() < 1024) {
                val tmp = File(dir, asset.name + ".part")
                tmp.delete()
                println("Downloading ${asset.name} ...")
                URL(asset.url).openStream().use { input ->
                    tmp.outputStream().buffered(1024 * 1024).use { output -> input.copyTo(output, 1024 * 1024) }
                }
                if (!tmp.renameTo(out)) { tmp.copyTo(out, overwrite = true); tmp.delete() }
            }
            val actual = out.sha256()
            check(actual.equals(asset.sha256, ignoreCase = true)) {
                "SHA-256 mismatch for ${asset.name}: expected=${asset.sha256} actual=$actual"
            }
            println("${asset.name}: ${out.length()} bytes sha256=$actual")
        }
    }
}

val punctuationDir = layout.projectDirectory.dir("src/main/assets/models/punctuation47")
val punctuationModelUrl = "https://huggingface.co/1-800-BAD-CODE/punct_cap_seg_47_language/resolve/1b9d51f/punct_cap_seg_47lang.onnx?download=true"
val punctuationTokenizerUrl = "https://huggingface.co/1-800-BAD-CODE/punct_cap_seg_47_language/resolve/1b9d51f/spe_unigram_64k_lowercase_47lang.model?download=true"
val punctuationModelSha256 = "640d91c06b7cc5b3e065c12a7097188378aad3bc11568ff1d72c4c0a2acb0df4"

val fetchPunctuationModel by tasks.registering {
    group = "model"
    description = "Downloads the local 47-language punctuation model (includes Persian)."
    outputs.dir(punctuationDir)
    doLast {
        val dir = punctuationDir.asFile.apply { mkdirs() }
        val model = File(dir, "punct_cap_seg_47lang.onnx")
        if (!model.exists() || model.length() < 10_000_000L) {
            val tmp = File(dir, model.name + ".part")
            tmp.delete()
            URL(punctuationModelUrl).openStream().use { input ->
                tmp.outputStream().buffered(1024 * 1024).use { output -> input.copyTo(output, 1024 * 1024) }
            }
            if (!tmp.renameTo(model)) { tmp.copyTo(model, overwrite = true); tmp.delete() }
        }
        check(model.sha256().equals(punctuationModelSha256, ignoreCase = true)) {
            "SHA-256 mismatch for punctuation model: ${model.sha256()}"
        }

        val tokenizer = File(dir, "spe_unigram_64k_lowercase_47lang.model")
        if (!tokenizer.exists() || tokenizer.length() < 500_000L) {
            val tmp = File(dir, tokenizer.name + ".part")
            tmp.delete()
            URL(punctuationTokenizerUrl).openStream().use { input ->
                tmp.outputStream().buffered(1024 * 1024).use { output -> input.copyTo(output, 1024 * 1024) }
            }
            if (!tmp.renameTo(tokenizer)) { tmp.copyTo(tokenizer, overwrite = true); tmp.delete() }
        }
        check(tokenizer.sha256() == "1bc15b6e5fd80dfac9999582ce3efcad2ac1f7cf4e0e9769b329f5de9ca5af47") {
            "Punctuation tokenizer checksum mismatch"
        }
        println("punctuation model: ${model.length()} bytes sha256=${model.sha256()}")
        println("punctuation tokenizer: ${tokenizer.length()} bytes sha256=${tokenizer.sha256()}")
    }
}

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(fetchShenavaRnntModel, fetchPunctuationModel) }

kapt {
    correctErrorTypes = true
    arguments { arg("room.schemaLocation", "$projectDir/schemas") }
}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.02.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.fragment:fragment-ktx:1.8.5")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    implementation("androidx.room:room-runtime:2.8.5")
    implementation("androidx.room:room-ktx:2.8.5")
    kapt("androidx.room:room-compiler:2.8.5")

    implementation("androidx.datastore:datastore-preferences:1.1.2")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("com.github.k2-fsa:sherpa-onnx:v1.13.4")

    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.27.0")
    implementation("io.github.eix128:sentencepiece4j:1.0.2")

    // ML Kit links CCTDestination during client initialization. Keep its runtime
    // dependency; remove INTERNET in our manifest instead of breaking the SDK.
    implementation("com.google.mlkit:genai-prompt:1.0.0-beta4")

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.02.00"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
}
