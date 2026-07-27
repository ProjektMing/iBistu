plugins {
    alias(libs.plugins.kotlin.jvm)
    id("maven-publish")
}

java {
    withSourcesJar()
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}

dependencies {
    implementation(libs.com.tencent.kona.kona.crypto)
    implementation(libs.com.tencent.kona.kona.provider)
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.json)

    testImplementation(libs.junit)
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
