plugins {
    `java-library`
    kotlin("jvm")
}

description = "Shared wire protocol DTOs + codec used by velocity and paper-companion."

dependencies {
    // intentionally framework-free — domain-equivalent module
    implementation(kotlin("stdlib-jdk8"))
}
repositories {
    mavenCentral()
}
kotlin {
    jvmToolchain(21)
}