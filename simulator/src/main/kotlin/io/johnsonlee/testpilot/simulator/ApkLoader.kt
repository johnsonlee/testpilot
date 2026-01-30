package io.johnsonlee.testpilot.simulator

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipFile

/**
 * Loads and extracts contents from an APK file.
 */
class ApkLoader(private val apkFile: File) {

    init {
        require(apkFile.exists()) { "APK file does not exist: ${apkFile.absolutePath}" }
        require(apkFile.extension == "apk") { "File is not an APK: ${apkFile.name}" }
    }

    /**
     * Extracted APK contents.
     */
    data class ApkContents(
        val dexFiles: List<File>,
        val resources: File?,
        val manifest: File?,
        val outputDir: File
    )

    /**
     * Extracts APK contents to a temporary directory.
     */
    fun extract(outputDir: File = createTempDir()): ApkContents {
        outputDir.mkdirs()

        val dexFiles = mutableListOf<File>()
        var resourcesFile: File? = null
        var manifestFile: File? = null

        ZipFile(apkFile).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                when {
                    // Extract DEX files (classes.dex, classes2.dex, etc.)
                    entry.name.matches(Regex("classes\\d*\\.dex")) -> {
                        val dexFile = File(outputDir, entry.name)
                        extractEntry(zip, entry.name, dexFile)
                        dexFiles.add(dexFile)
                    }

                    // Extract resources.arsc
                    entry.name == "resources.arsc" -> {
                        resourcesFile = File(outputDir, entry.name)
                        extractEntry(zip, entry.name, resourcesFile!!)
                    }

                    // Extract AndroidManifest.xml (binary format)
                    entry.name == "AndroidManifest.xml" -> {
                        manifestFile = File(outputDir, entry.name)
                        extractEntry(zip, entry.name, manifestFile!!)
                    }

                    // Extract res/ directory
                    entry.name.startsWith("res/") -> {
                        val resFile = File(outputDir, entry.name)
                        if (entry.isDirectory) {
                            resFile.mkdirs()
                        } else {
                            resFile.parentFile?.mkdirs()
                            extractEntry(zip, entry.name, resFile)
                        }
                    }
                }
            }
        }

        // Sort DEX files: classes.dex first, then classes2.dex, etc.
        dexFiles.sortBy { file ->
            val match = Regex("classes(\\d*)\\.dex").find(file.name)
            match?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }

        return ApkContents(
            dexFiles = dexFiles,
            resources = resourcesFile,
            manifest = manifestFile,
            outputDir = outputDir
        )
    }

    private fun extractEntry(zip: ZipFile, entryName: String, outputFile: File) {
        zip.getInputStream(zip.getEntry(entryName)).use { input ->
            FileOutputStream(outputFile).use { output ->
                input.copyTo(output)
            }
        }
    }

    private fun createTempDir(): File {
        val tempDir = File(System.getProperty("java.io.tmpdir"), "testpilot-${System.currentTimeMillis()}")
        tempDir.mkdirs()
        return tempDir
    }

    companion object {
        fun load(apkPath: String): ApkLoader = ApkLoader(File(apkPath))
        fun load(apkFile: File): ApkLoader = ApkLoader(apkFile)
    }
}
