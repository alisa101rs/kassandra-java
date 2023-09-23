
plugins {
    kotlin("jvm") version "1.9.10"
    `java-library`
    `maven-publish`
    kotlin("plugin.serialization") version "1.9.10"
}

group = "com.github"
version = "0.2.0"

repositories {
    mavenCentral()
}

kotlin {
    explicitApi()
    jvmToolchain(11)
}

val rustLib = configurations.create("rustLib")

dependencies {
    rustLib(project("rust"))

    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.2")
    implementation("io.ktor:ktor-server-core-jvm:2.3.4")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    testImplementation("io.kotest:kotest-runner-junit5:5.7.2")
    testImplementation("io.kotest:kotest-assertions-core:5.7.2")
    testImplementation("com.datastax.oss:java-driver-core:4.15.0")
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

val collectLibs = tasks.create("collectLibs") {
    dependsOn(rustLib)

    doLast {
        rustLib.resolve().forEach { artifact ->
            copy {
                from(artifact)
                into(file("${buildDir}/rustLibs/native"))
            }
        }
    }
}

sourceSets.main.configure {
    resources {
        srcDir(file("${buildDir}/rustLibs/native"))
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {

            from(components["java"])
        }
    }
}

tasks.processResources {
    dependsOn(collectLibs)
}
