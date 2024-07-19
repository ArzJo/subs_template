import ThemerConstants.ALLOW_THIRD_PARTY_SUBSTRATUM_BUILDS
import ThemerConstants.APK_SIGNATURE_PRODUCTION
import ThemerConstants.BASE_64_LICENSE_KEY
import ThemerConstants.ENFORCE_GOOGLE_PLAY_INSTALL
import ThemerConstants.SHOULD_ENCRYPT_ASSETS
import ThemerConstants.SUPPORTS_THIRD_PARTY_SYSTEMS

import Util.assets
import Util.cleanEncryptedAssets
import Util.copyEncryptedTo
import Util.generateRandomByteArray
import Util.tempAssets
import Util.twistAsset

import java.io.FileInputStream
import java.io.FileOutputStream
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.crypto.spec.IvParameterSpec

plugins {
    id("com.android.application")
    kotlin("android")
}

// Themers: DO NOT MODIFY
val secretKey = generateRandomByteArray()
val ivKey = generateRandomByteArray()

android {
    compileSdk = 34

    defaultConfig {
        // If you're planning to change up the package name, ensure you have read the readme
        // thoroughly!
        applicationId = "substratum.theme.template"
        targetSdk = 34
        // Support for Android 12+, change it according your needs.
        minSdk = 31
        // Both versions must be changed to increment on Play Store/user's devices
        versionCode = 2
        versionName = "2.0"

        // Themers: DO NOT MODIFY
        buildConfigField("boolean", "SUPPORTS_THIRD_PARTY_SYSTEMS", "$SUPPORTS_THIRD_PARTY_SYSTEMS")
        buildConfigField("boolean", "ALLOW_THIRD_PARTY_SUBSTRATUM_BUILDS", "$ALLOW_THIRD_PARTY_SUBSTRATUM_BUILDS")
        buildConfigField("byte[]", "DECRYPTION_KEY", secretKey.joinToString(prefix = "{", postfix = "}"))
        buildConfigField("byte[]", "IV_KEY", ivKey.joinToString(prefix = "{", postfix = "}"))
        resValue("string", "encryption_status", if (shouldEncrypt()) "onCompileVerify" else "false")
    }
    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")

            // Themers: DO NOT MODIFY
            buildConfigField("boolean", "ENFORCE_GOOGLE_PLAY_INSTALL", "false")
            buildConfigField("String", "BASE_64_LICENSE_KEY", "\"\"")
            buildConfigField("String", "APK_SIGNATURE_PRODUCTION", "\"\"")
        }
        getByName("release") {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")

            // Themers: DO NOT MODIFY
            buildConfigField("boolean", "ENFORCE_GOOGLE_PLAY_INSTALL", "$ENFORCE_GOOGLE_PLAY_INSTALL")
            buildConfigField("String", "BASE_64_LICENSE_KEY", "\"$BASE_64_LICENSE_KEY\"")
            buildConfigField("String", "APK_SIGNATURE_PRODUCTION", "\"$APK_SIGNATURE_PRODUCTION\"")
        }
    }
    sourceSets {
        named("main") {
            java.srcDir("src/main/kotlin")
        }
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk8", version = Constants.kotlinVersion))

    implementation("androidx.appcompat:appcompat:1.4.1")
    implementation("com.github.javiersantos:PiracyChecker:1.2.8")
}

// Themers: DO NOT MODIFY ANYTHING BELOW
tasks.register("encryptAssets") {
    if (!shouldEncrypt()) {
        println("Skipping assets encryption...")
        return@register
    }

    // Check if temp assets exist
    if (!projectDir.tempAssets.exists()) {
        println("Encrypting duplicated assets, don't worry, your original assets are safe...")

        val secretKeySpec = SecretKeySpec(secretKey, "AES")
        val ivParameterSpec = IvParameterSpec(ivKey)
        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
            .apply {
                init(Cipher.ENCRYPT_MODE, secretKeySpec, ivParameterSpec)
            }

        // Encrypt every single file in the assets dir recursively
        projectDir.assets.walkTopDown().filter { it.isFile }.forEach { file ->
            file.twistAsset("assets", "assets-temp")

            //Encrypt assets
            if (file.absolutePath.contains("overlays")) {
                FileInputStream(file).use { fis ->
                    FileOutputStream("${file.absolutePath}.enc").use { fos ->
                        fis.copyEncryptedTo(fos, cipher, bufferSize = 64)
                    }
                }
                file.delete()
            }
        }
    } else {
        throw RuntimeException("Old temporary assets found! Try and do a clean project.")
    }
}

project.afterEvaluate {
    tasks.named("preBuild") {
        dependsOn("encryptAssets")
    }
}

gradle.buildFinished {
    projectDir.cleanEncryptedAssets()
}

fun shouldEncrypt(): Boolean {
    val tasks = project.gradle.startParameter.taskNames
    return SHOULD_ENCRYPT_ASSETS && tasks.joinToString().contains("release", ignoreCase = true)
}
