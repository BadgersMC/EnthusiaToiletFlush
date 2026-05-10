plugins {
    `java-library`
    id("com.gradleup.shadow") version "8.3.5"
    kotlin("jvm")
}

description = "Paper backend companion: executes restart + bridges CheckHacks events."

dependencies {
    api(project(":common"))

    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")

    // CheckHacks is private; the bridge subscribes via reflection so we
    // do not need its types on the compile classpath. Drop a
    // CheckHacks-fork jar into ~/.m2/repository and uncomment if you want
    // type-safe binding:
    // compileOnly("me.branduzzo:CheckHacks:1.2.0")
    implementation(kotlin("stdlib-jdk8"))
}

tasks.shadowJar {
    archiveClassifier.set("")
}

tasks.build { dependsOn(tasks.shadowJar) }
repositories {
    mavenCentral()
}
kotlin {
    jvmToolchain(21)
}