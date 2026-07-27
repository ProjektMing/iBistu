import org.gradle.api.tasks.compile.JavaCompile
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

tasks.withType<JavaCompile>().configureEach {
    options.release.set(11)
}

application {
    mainClass = "example.MainKt"
}

dependencies {
    implementation(project(":bistulogin"))
    implementation(libs.okhttp)
    implementation(libs.kotlinx.coroutines.core)
}
