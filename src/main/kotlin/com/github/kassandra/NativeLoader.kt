package com.github.kassandra

import io.netty.util.internal.NativeLibraryLoader
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.*


public object NativeLoader {
    private var loaded: Boolean = false

    @Synchronized
    public fun init() {
        load()
    }

    @Synchronized
    public fun load() {
        if (loaded) return

        if (tryLoadFromLibraryPath()) {
            loaded = true
            return
        }

        val path = libraryPath()

        System.load(path)
        loaded = true
    }

    private fun tryLoadFromLibraryPath(): Boolean {
        try {
            System.loadLibrary(NATIVE_LIBRARY_NAME)
        } catch (_: UnsatisfiedLinkError) {
            return false
        }
        return true
    }

    private fun libraryPath(): String {
        val platform = detectPlatform()
        val ext = platform.ext
        val fileName = "${platform.prefix}${NATIVE_LIBRARY_NAME}_${platform.os.value}_${platform.arch.value}"
        val tempFile = Files.createTempFile(fileName, ext)
        NativeLibraryLoader::class.java.getResourceAsStream("/$fileName$ext").use { stream ->
            Files.copy(
                stream,
                tempFile,
                StandardCopyOption.REPLACE_EXISTING
            )
        }
        return tempFile.toString()
    }

    private enum class Os( val value: String) {
        LINUX("linux"),
        MACOS("macos"),
        WINDOWS("windows");
    }


    private enum class Arch(val value: String) {
        X86_64("x86_64"),
        AARCH64("aarch64");
    }


    private enum class Platform(val os: Os, val arch: Arch, val prefix: String, val ext: String) {
        LINUX(Os.LINUX, Arch.X86_64, "lib", ".so"),
        LINUX_AARCH64(Os.LINUX, Arch.AARCH64, "lib", ".so"),
        MACOS(Os.MACOS, Arch.X86_64, "lib", ".dylib"),
        MACOS_AARCH64(Os.MACOS, Arch.AARCH64, "lib", ".dylib"),
        WINDOWS(Os.WINDOWS, Arch.X86_64, "", ".dll");
    }


    private fun detectPlatform(): Platform {
        val os = System.getProperty("os.name").lowercase(Locale.getDefault())
        val arch = System.getProperty("os.arch").lowercase(Locale.getDefault())
        if (os.contains("linux")) {
            return if (arch == "aarch64") {
                Platform.LINUX_AARCH64
            } else Platform.LINUX
        }
        if (os.contains("mac os") || os.contains("darwin")) {
            return if (arch == "aarch64") {
                Platform.MACOS_AARCH64
            } else Platform.MACOS
        }
        if (os.lowercase(Locale.getDefault()).contains("windows")) {
            return Platform.WINDOWS
        }
        throw RuntimeException("platform not supported: $os")
    }

    private val NATIVE_LIBRARY_NAME = "kassandra_jni"
}
