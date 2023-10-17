import java.io.FileOutputStream
import java.io.IOException
import java.net.URI
import kotlin.io.path.Path
import kotlin.io.path.absolutePathString
import kotlin.io.path.createDirectories
import kotlin.io.path.div
import kotlin.io.copyTo

plugins {
    kotlin("jvm") version "1.9.10"
    `java-library`
    `maven-publish`
    kotlin("plugin.serialization") version "1.9.10"
}

group = "com.github"
version = "0.2.18"

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

val sourcesJar by tasks.existing {
    dependsOn(getNativeLibs)
}

val processResources by tasks.existing {
    dependsOn(getNativeLibs)
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
    if (os.contains("windows")) {
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

val buildJniLib by tasks.creating(Exec::class) {
    workingDir = File("./rust")

    val args = mutableListOf("cargo", "build", "--release")
    val triple = rustTargetTriple()
    if (triple != null) {
        args += listOf("--target", triple)
    }

    commandLine(args)
}

val copyJniLib by tasks.creating(Copy::class) {
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

val getNativeLibs by tasks.creating {
    if (System.getenv("BUILD_NATIVE") != null) {
        dependsOn(copyJniLib)
        outputs.files(copyJniLib.outputs.files)
        return@creating
    } else {
        val base = "https://github.com/alisa101rs/kassandra-java/releases/download/v${project.version}/"
        val nativeLibs = listOf(
            "libkassandra_jni_macos_x86_64.dylib",
            "libkassandra_jni_macos_aarch64.dylib",
            "libkassandra_jni_linux_x86_64.so",
            "kassandra_jni_windows_x86_64.dll",
        )
        val output = File(project.buildDir, "jni-libs").path

        outputs.files(
            nativeLibs.mapNotNull { nativeLib ->
                try {
                    download(
                        URI("${base}${nativeLib}"),
                        Path(output)
                    )
                } catch(e: IOException) {
                    logger.error("Could not get native lib: $nativeLib")
                    null
                }
            }
        )
    }
}

val build by tasks.existing {
    dependsOn(getNativeLibs)
}

val jar by tasks.existing(Jar::class) { }

val assemble by tasks.existing {}

fun download(uri: URI, output: java.nio.file.Path): java.nio.file.Path {
    output.createDirectories()

    val fileName = uri.path.split("/").last()
    val outputFile = output / fileName

    uri.toURL().openStream()
        .buffered()
        .use { input ->
            val file = FileOutputStream(outputFile.absolutePathString())
            input.copyTo(file)
    }

    return outputFile
}

fun groovy.util.Node.addDependencyNodes(scope: String, deps: DependencySet) {
    deps.forEach {
        val dependencyNode = appendNode("dependency")
        dependencyNode.appendNode("groupId", it.group)
        dependencyNode.appendNode("artifactId", it.name)
        dependencyNode.appendNode("version", it.version)
        dependencyNode.appendNode("scope", scope)
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            artifact(jar)
            artifact(sourcesJar)

            pom {
                name = "kassandra"
                description = "JVM Kassandra bindings"
                scm {
                    url = "https://github.com/alisa101rs/kassandra-java"
                    connection = "scm:https://github.com/alisa101rs/kassandra-java.git"
                    developerConnection = "scm:git@github.com/alisa101rs/kassandra-java.git"
                }
                licenses {
                    license {
                        name = "The MIT License (MIT)"
                        url = "https://mit-license.org/"
                    }
                }
                developers {
                    developer {
                        name = "Alisa Gorelova"
                        email = "nanopro1g@gmail.com"
                    }
                }
                withXml {
                    val node = asNode().appendNode("dependencies")
                    node.addDependencyNodes(
                        "compile", project.configurations.api.get().allDependencies
                    )
                    node.addDependencyNodes(
                        "runtime", project.configurations.implementation.get().allDependencies
                    )
                }
            }

        }
    }
}

