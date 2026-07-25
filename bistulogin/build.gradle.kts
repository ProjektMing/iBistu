import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    id("maven-publish")
}

java {
    withSourcesJar()
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

dependencies {
    implementation(libs.com.tencent.kona.kona.crypto)
    implementation(libs.com.tencent.kona.kona.provider)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.json)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "edu.bistu"
            artifactId = "bistulogin"
            version = "1.0.0"

            pom {
                name = "bistulogin"
                description = "BISTU SSO login library (CAS + SM2 encryption)"
            }
        }
    }
}
