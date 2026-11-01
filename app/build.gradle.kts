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
    testBuildType = if (providers.gradleProperty("emroozReleaseTests").isPresent) "release" else "debug"

    defaultConfig {
        applicationId = "com.nameemrooz.journal"
        minSdk = 26
        targetSdk = 35
        versionCode = 41
        versionName = "2.3.1"
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
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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

    sourceSets.getByName("main").assets.exclude("models/shenava*/**", "models/whisper/**", "models/punctuation47/punct_cap_seg_47lang.onnx")

    androidResources {
        noCompress += listOf("onnx", "bin")
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
        jniLibs.useLegacyPackaging = false
        jniLibs.pickFirsts += "**/libonnxruntime.so"
    }
}

fun File.sha256(): String {
    val md = MessageDigest.getInstance("SHA-256")
    inputStream().buffered().use { input ->
        val buffer = ByteArray(1024 * 1024)
        while (true) {
            val count = input.read(buffer)
            if (count <= 0) break
            md.update(buffer, 0, count)
        }
    }
    return md.digest().joinToString("") { "%02x".format(it) }
}

fun downloadVerified(url: String, target: File, digest: String) {
    if (target.exists() && target.sha256() == digest) return
    target.parentFile.mkdirs()
    val temp = File(target.path + ".part")
    try {
        URL(url).openConnection().apply { connectTimeout = 30_000; readTimeout = 180_000 }
            .getInputStream().use { input -> temp.outputStream().buffered().use { input.copyTo(it) } }
        check(temp.sha256() == digest) { "Model checksum mismatch: ${target.name}" }
        temp.copyTo(target, overwrite = true)
    } finally { temp.delete() }
}

val punctuationHash = "fa630cad87cdf398422aee51c66fda532b82dc8164d9653a588277a2ea98adcb"
val punctuationDir = layout.projectDirectory.dir("src/main/assets/models/punctuation47")
val fetchPunctuationModel by tasks.registering {
    group = "model"
    inputs.file(rootProject.file("tools/quantize-punctuation.py"))
    outputs.dir(punctuationDir)
    doLast {
        val dir = punctuationDir.asFile.apply { mkdirs() }
        val model = File(dir, "punctuation-int8.onnx")
        if (!model.exists() || model.sha256() != punctuationHash) {
            val original = layout.buildDirectory.file("model-source/punct_cap_seg_47lang.onnx").get().asFile
            downloadVerified(
                "https://huggingface.co/1-800-BAD-CODE/punct_cap_seg_47_language/resolve/1b9d51f/punct_cap_seg_47lang.onnx?download=true",
                original, "640d91c06b7cc5b3e065c12a7097188378aad3bc11568ff1d72c4c0a2acb0df4")
            val python = providers.gradleProperty("emroozModelPython")
                .orElse(providers.environmentVariable("EMROOZ_MODEL_PYTHON")).getOrElse("python3")
            providers.exec {
                commandLine(python, rootProject.file("tools/quantize-punctuation.py").absolutePath,
                    original.absolutePath, model.absolutePath)
            }.result.get().assertNormalExitValue()
        }
        check(model.sha256() == punctuationHash) { "Quantized punctuation model checksum mismatch" }
        downloadVerified(
            "https://huggingface.co/1-800-BAD-CODE/punct_cap_seg_47_language/resolve/1b9d51f/spe_unigram_64k_lowercase_47lang.model?download=true",
            File(dir, "spe_unigram_64k_lowercase_47lang.model"),
            "1bc15b6e5fd80dfac9999582ce3efcad2ac1f7cf4e0e9769b329f5de9ca5af47")
        check(!File(dir, "punct_cap_seg_47lang.onnx").exists()) { "Obsolete large model must not be packaged" }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach { dependsOn(fetchPunctuationModel) }

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
