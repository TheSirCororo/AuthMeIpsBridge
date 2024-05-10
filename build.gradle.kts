plugins {
    kotlin("jvm") version "1.9.24"
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "space.moonstudio"
version = "1.0.2"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.org/repository/maven-public/")
}

dependencies {
    compileOnly("com.destroystokyo.paper:paper-api:1.12.2-R0.1-SNAPSHOT")
    compileOnly("fr.xephi:authme:5.6.0-SNAPSHOT")
    implementation(kotlin("stdlib"))
    runtimeOnly("com.h2database:h2:2.2.224")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}

java {
    toolchain.languageVersion = JavaLanguageVersion.of(11)
}

tasks {
    shadowJar {
        archiveBaseName.set("AuthMeIpsBridge")
    }
}