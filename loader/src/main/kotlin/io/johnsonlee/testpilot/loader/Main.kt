package io.johnsonlee.testpilot.loader

import java.io.File

/**
 * Command-line tool to test APK loading.
 *
 * Usage: java -jar loader.jar <apk-path> [activity-class]
 */
fun main(args: Array<String>) {
    if (args.isEmpty()) {
        println("Usage: testpilot <apk-path> [activity-class]")
        println()
        println("Examples:")
        println("  testpilot app.apk")
        println("  testpilot app.apk com.example.MainActivity")
        return
    }

    val apkPath = args[0]
    val apkFile = File(apkPath)

    if (!apkFile.exists()) {
        println("Error: APK file not found: $apkPath")
        return
    }

    println("=" .repeat(60))
    println("TestPilot POC - APK Loader")
    println("=".repeat(60))
    println()

    try {
        // Step 1: Load APK
        println("[1] Loading APK: ${apkFile.name}")
        val apkLoader = ApkLoader.load(apkFile)
        val contents = apkLoader.extract()

        println("    DEX files: ${contents.dexFiles.size}")
        contents.dexFiles.forEach { println("      - ${it.name}") }
        println("    Resources: ${contents.resources?.name ?: "not found"}")
        println("    Manifest:  ${contents.manifest?.name ?: "not found"}")
        println()

        // Step 2: Convert DEX
        println("[2] Converting DEX to JVM bytecode...")
        var totalClasses = 0
        var totalErrors = 0

        for (dexFile in contents.dexFiles) {
            println("    Processing: ${dexFile.name}")
            val result = DexConverter.convert(dexFile)
            totalClasses += result.classes.size
            totalErrors += result.errors.size

            if (result.errors.isNotEmpty()) {
                println("    Errors (${result.errors.size}):")
                result.errors.take(5).forEach { println("      - $it") }
                if (result.errors.size > 5) {
                    println("      ... and ${result.errors.size - 5} more")
                }
            }
        }

        println("    Total classes converted: $totalClasses")
        println("    Total conversion errors: $totalErrors")
        println()

        // Step 3: Show class list (sample)
        println("[3] Sample of converted classes:")
        val allClasses = mutableMapOf<String, ByteArray>()
        for (dexFile in contents.dexFiles) {
            val result = DexConverter.convert(dexFile)
            allClasses.putAll(result.classes)
        }

        allClasses.keys.sorted().take(20).forEach { className ->
            println("    - $className")
        }
        if (allClasses.size > 20) {
            println("    ... and ${allClasses.size - 20} more classes")
        }
        println()

        // Step 4: Bytecode rewriting info
        println("[4] Bytecode rewriting mappings:")
        val androidClasses = allClasses.keys.filter { it.startsWith("android/") }
        println("    Android framework classes found: ${androidClasses.size}")
        androidClasses.take(10).forEach { className ->
            val mapped = BytecodeRewriter().getMappedName(className)
            if (mapped != className) {
                println("      $className -> $mapped")
            }
        }
        println()

        // Step 5: Parse AndroidManifest.xml
        if (contents.manifest != null) {
            println("[5] Parsing AndroidManifest.xml...")
            try {
                val manifest = BinaryXmlParser.parse(contents.manifest!!)
                println("    Root element: ${manifest.rootElement?.name}")
                println("    Namespaces: ${manifest.namespaces.size}")
                manifest.namespaces.forEach { (prefix, uri) ->
                    println("      $prefix -> $uri")
                }

                // Show some manifest info
                manifest.rootElement?.let { root ->
                    val pkg = root.attributes.find { it.name == "package" }?.value
                    println("    Package: $pkg")

                    // Find activities
                    val application = root.children.find { it.name == "application" }
                    val activities = application?.children?.filter { it.name == "activity" } ?: emptyList()
                    println("    Activities: ${activities.size}")
                    activities.take(5).forEach { activity ->
                        val name = activity.attributes.find { it.name == "name" }?.value
                        println("      - $name")
                    }
                    if (activities.size > 5) {
                        println("      ... and ${activities.size - 5} more")
                    }
                }
            } catch (e: Exception) {
                println("    Error: ${e.message}")
            }
        }
        println()

        // Step 6: Parse resources.arsc
        if (contents.resources != null) {
            println("[6] Parsing resources.arsc...")
            try {
                val resources = ResourcesParser.parse(contents.resources!!)
                println("    Packages: ${resources.packages.size}")
                resources.packages.forEach { pkg ->
                    println("      - ${pkg.name} (id: 0x${pkg.id.toString(16)})")
                    println("        Types: ${pkg.types.size}")
                    pkg.types.take(10).forEach { type ->
                        println("          - ${type.name}: ${type.entries.size} entries")
                    }
                    if (pkg.types.size > 10) {
                        println("          ... and ${pkg.types.size - 10} more types")
                    }
                }

                // Show some sample resources
                println("    Sample resources:")
                val resourceMap = resources.buildResourceMap()
                resourceMap.entries.take(10).forEach { (id, name) ->
                    println("      0x${id.toString(16)} -> $name")
                }
                if (resourceMap.size > 10) {
                    println("      ... and ${resourceMap.size - 10} more resources")
                }
            } catch (e: Exception) {
                println("    Error: ${e.message}")
                e.printStackTrace()
            }
        }
        println()

        // Step 7: Test TestPilot SDK
        println("[7] Testing TestPilot SDK...")
        try {
            val app = TestPilot.load(apkFile)
            println("    Package: ${app.getPackageName()}")
            println("    Activities: ${app.getActivities().size}")

            val launcher = app.getLauncherActivity()
            println("    Launcher: ${launcher?.name}")

            if (launcher != null) {
                println("    Launching activity...")
                val session = app.launch()
                println("    Activity: ${session.getActivity()::class.simpleName}")
                println("    Window: ${session.getWindow().width}x${session.getWindow().height}")

                // Test lifecycle
                session.pause()
                session.resume()
                session.stop()
                session.destroy()
                println("    Lifecycle test: PASSED")
            }
        } catch (e: Exception) {
            println("    SDK Error: ${e.message}")
        }
        println()

        println("=".repeat(60))
        println("Phase 2 MVP Complete!")
        println("=".repeat(60))

    } catch (e: Exception) {
        println("Error: ${e.message}")
        e.printStackTrace()
    }
}
