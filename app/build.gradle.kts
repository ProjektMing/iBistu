plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

android {
    namespace = "edu.bistu.cs4029.ibistu"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "edu.bistu.cs4029.ibistu"
        minSdk = 35
        //noinspection OldTargetApi
        targetSdk = 36
        versionCode = 2
        versionName = "1.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    val releaseKeystorePath = System.getenv("SIGNING_KEYSTORE_PATH")

    signingConfigs {
        if (!releaseKeystorePath.isNullOrEmpty()) {
            create("release") {
                storeFile = file(releaseKeystorePath)
                storePassword = System.getenv("SIGNING_STORE_PASSWORD")
                keyAlias = System.getenv("SIGNING_KEY_ALIAS")
                keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            if (!releaseKeystorePath.isNullOrEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

val verifyReleaseKonaProviderClasses = tasks.register("verifyReleaseKonaProviderClasses") {
    group = "verification"
    description = "Verifies that R8 keeps the Kona classes loaded by the JCA provider."
    dependsOn("minifyReleaseWithR8")

    inputs.files(
        layout.buildDirectory.file("outputs/mapping/release/usage.txt"),
        layout.buildDirectory.file("outputs/mapping/release/mapping.txt"),
        layout.buildDirectory.file("outputs/mapping/release/seeds.txt")
    )

    doLast {
        val reports = inputs.files.files.associateBy { it.name }
        val usage = checkNotNull(reports["usage.txt"]).readText()
        val mapping = checkNotNull(reports["mapping.txt"]).readText()
        val seeds = checkNotNull(reports["seeds.txt"]).readText()
        val requiredClasses = listOf(
            "com.tencent.kona.sun.security.ec.ECKeyFactory",
            "com.tencent.kona.crypto.provider.SM2Cipher"
        )

        requiredClasses.forEach { className ->
            val escapedClassName = Regex.escape(className)
            check(Regex("(?m)^$escapedClassName(?:$|:)").containsMatchIn(seeds)) {
                "$className is not present in R8's kept seeds"
            }
            check(!Regex("(?m)^$escapedClassName(?:$|:)").containsMatchIn(usage)) {
                "$className was removed by R8 but is loaded by KonaCryptoProvider"
            }
            val renamedClass = Regex("(?m)^$escapedClassName -> ([^:]+):$")
                .find(mapping)
                ?.groupValues
                ?.get(1)
            check(renamedClass == null || renamedClass == className) {
                "$className was renamed to $renamedClass but KonaCryptoProvider loads it by its original name"
            }
        }
    }
}

tasks.matching { it.name == "assembleRelease" }.configureEach {
    finalizedBy(verifyReleaseKonaProviderClasses)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Navigation Compose
    implementation(libs.androidx.navigation.compose)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // BistuLogin module
    implementation(project(":bistulogin"))

    // https://github.com/Tencent/TencentKonaSMSuite
    implementation(libs.com.tencent.kona.kona.crypto)
    implementation(libs.com.tencent.kona.kona.provider)

    // OkHttp for network requests
    implementation(libs.okhttp)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(libs.kotlinx.coroutines.test)

    // Room (SQLite)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Test
    testImplementation(libs.mockk)
    androidTestImplementation(libs.mockwebserver3)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
