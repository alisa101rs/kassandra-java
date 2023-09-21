# Kassandra Java

This is java/kotlin wrapper of [kassandra](https://github.com/alisa101rs/kassandra) library.

The goal of this package is to provide a set of tools that makes writing unit test for cassandra easier.


# Installation 
## Kotlin DSL
Add the JitPack repository to your `build.gradle.kts`

```kotlin
allprojects {
   repositories {
      ...
      maven { url = uri("https://jitpack.io") }
   }
}
```

Add the dependency:
```kotlin
dependencies {
    implementation("com.github.kassandra:kassandra:1.0.0-SNAPSHOT")
}
```