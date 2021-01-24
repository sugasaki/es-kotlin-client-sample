import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") version "1.4.21"
    application
}

group = "me.atsu"
version = "1.0-SNAPSHOT"

val esKotlinVersion = "1.0.2"
val slf4jVersion = "1.7.30"
val junitVersion = "5.7.0"
val jacksonVersion = "2.12.1"
val ktorVersion = "1.4.1"

repositories {
    mavenCentral()
    jcenter()
}

dependencies {
    implementation("com.github.jillesvangurp:es-kotlin-client:$esKotlinVersion")

//    implementation("org.slf4j:slf4j-api:$slf4jVersion")
//    implementation("ch.qos.logback:logback-classic:1.2.3")
//    implementation("org.apache.logging.log4j:log4j-to-slf4j:2.14.0") // es seems to insist on log4j2
//    implementation("org.slf4j:log4j-over-slf4j:$slf4jVersion")

    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:$jacksonVersion")
    implementation("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:$jacksonVersion")

    implementation("io.ktor:ktor-server-netty:$ktorVersion")
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-jackson:$ktorVersion")
}

tasks.withType<KotlinCompile>() {
    kotlinOptions.jvmTarget = "11"
}

application {
    mainClassName = "MainKt"
}

