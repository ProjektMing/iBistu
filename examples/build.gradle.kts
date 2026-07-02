plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(11)
    }
}

application {
    mainClass = "example.MainKt"
}

dependencies {
    implementation(project(":bistulogin"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
}
