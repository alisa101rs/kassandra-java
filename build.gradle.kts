
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

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    withSourcesJar()
}

tasks.withType<JavaCompile> {
    options.encoding = "UTF-8"
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-stdlib")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.2")
    implementation("io.ktor:ktor-server-core-jvm:2.3.4")
    implementation("io.ktor:ktor-server-netty-jvm:2.3.4")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.0")

    testImplementation("io.kotest:kotest-runner-junit5:5.7.2")
    testImplementation("io.kotest:kotest-assertions-core:5.7.2")
    testImplementation("com.datastax.oss:java-driver-core:4.15.0")
}

sourceSets {
    main {
        resources {
            srcDirs("build/jni-libs")
        }
    }
}


getTasksByName("sourcesJar", false).forEach {
    it.dependsOn("copyJniLib")
}

getTasksByName("processResources", false).forEach {
    it.dependsOn("copyJniLib")
}


tasks.withType<Test>().configureEach {
    useJUnitPlatform()
}

fun jniLibOsClassifier(): String {
    val os = System.getProperty("os.name").lowercase()

    if (os.contains("linux")) {
        return "linux"
    }
    if (os.contains("mac os") || os.contains("darwin")) {
        return "macos"
    }
    if (os.contains("windows")){
        return "windows"
    }
    throw RuntimeException("platform not supported: " + System.getProperty("os.name"))
}

fun jniLibArchClassifier(): String {
    val target = rustTargetTriple()
    // Use the first part of the rust target triple as the arch classifier if set, otherwise assume "x86_64"
    return if (target != null) {
        target.split("-")[0]
    } else {
        "x86_64"
    }
}

fun rustTargetTriple(): String? =
    System.getenv("JNILIB_RUST_TARGET")

val buildJniLib = task<Exec>("buildJniLib") {
    workingDir = File("./rust")

    val args = mutableListOf("cargo", "build", "--release")
    val triple = rustTargetTriple()
    if (triple != null) {
        args += listOf("--target", triple)
    }

    commandLine(args)
}

task<Copy>("copyJniLib") {
    dependsOn(buildJniLib)

    val targetDir = rustTargetTriple() ?: ""
    from("rust/target/$targetDir/release")
    include("*.so", "*.dylib", "*.dll")
    rename(
        "^(lib)?kassandra_jni",
        "\$1kassandra_jni_${jniLibOsClassifier()}_${jniLibArchClassifier()}"
    )
    into(
        File(project.buildDir, "jni-libs")
    )
}

publishing {
    publications {
        create<MavenPublication>("maven") {

            from(components["java"])
        }
    }
}

