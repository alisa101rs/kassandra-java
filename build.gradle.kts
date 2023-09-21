import org.gradle.internal.impldep.org.apache.maven.model.ConfigurationContainer

plugins {
    kotlin("jvm") version "1.9.0"
    `java-library`
}

group = "com.github"
version = "0.1.0"

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
}

tasks.test {

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

tasks.processResources {
    dependsOn(collectLibs)
}
