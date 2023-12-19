plugins {
    kotlin("jvm") version "1.9.21"
    id("com.github.johnrengelman.shadow") version "7.1.2"
}

group = "space.moonstudio"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.codemc.org/repository/maven-public/")
}

dependencies {
    compileOnly("com.destroystokyo.paper:paper-api:1.12.2-R0.1-SNAPSHOT")
    compileOnly("fr.xephi:authme:5.6.0-SNAPSHOT")
    implementation("org.apache.httpcomponents:httpclient:4.5.14")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(8)
}

tasks {
    shadowJar {
        archiveBaseName.set("AuthMeIpsBridge")
    }
}