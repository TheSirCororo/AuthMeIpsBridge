plugins {
    kotlin("jvm") version "2.3.0"
    id("com.gradleup.shadow") version "9.3.1"
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
    runtimeOnly("com.h2database:h2:2.4.240")
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