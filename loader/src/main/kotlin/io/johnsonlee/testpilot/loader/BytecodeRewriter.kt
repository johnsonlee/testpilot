package io.johnsonlee.testpilot.loader

import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.FieldVisitor
import org.objectweb.asm.Opcodes
import org.objectweb.asm.commons.ClassRemapper
import org.objectweb.asm.commons.Remapper

/**
 * Rewrites bytecode to replace android.* references with TestPilot shim classes.
 */
class BytecodeRewriter {

    /**
     * Mapping of Android classes to their TestPilot shim equivalents.
     */
    private val classMapping = mapOf(
        // Activity
        "android/app/Activity" to "io/johnsonlee/testpilot/simulator/activity/Activity",

        // View system
        "android/view/View" to "io/johnsonlee/testpilot/simulator/view/View",
        "android/view/ViewGroup" to "io/johnsonlee/testpilot/simulator/view/ViewGroup",
        "android/view/LayoutInflater" to "io/johnsonlee/testpilot/simulator/view/LayoutInflater",

        // Widgets
        "android/widget/TextView" to "io/johnsonlee/testpilot/simulator/widget/TextView",
        "android/widget/Button" to "io/johnsonlee/testpilot/simulator/widget/Button",
        "android/widget/LinearLayout" to "io/johnsonlee/testpilot/simulator/widget/LinearLayout",
        "android/widget/FrameLayout" to "io/johnsonlee/testpilot/simulator/widget/FrameLayout",

        // Content
        "android/content/Context" to "io/johnsonlee/testpilot/simulator/content/Context",

        // OS
        "android/os/Bundle" to "io/johnsonlee/testpilot/simulator/os/Bundle",

        // Graphics
        "android/graphics/Canvas" to "io/johnsonlee/testpilot/simulator/graphics/Canvas",

        // Resources
        "android/content/res/Resources" to "io/johnsonlee/testpilot/simulator/resources/Resources",
    )

    private val remapper = object : Remapper() {
        override fun map(internalName: String): String {
            // Check direct mapping
            classMapping[internalName]?.let { return it }

            // Check package-level mapping for subclasses
            if (internalName.startsWith("android/")) {
                // Try to map to testpilot package, but keep original if no mapping exists
                val testpilotName = internalName.replace("android/", "io/johnsonlee/testpilot/simulator/")
                // For now, only map known classes
                return classMapping[internalName] ?: internalName
            }

            return internalName
        }
    }

    /**
     * Rewrites a single class bytecode.
     */
    fun rewrite(bytecode: ByteArray): ByteArray {
        val reader = ClassReader(bytecode)
        // Use ClassReader as argument to ClassWriter for frame copying
        // Use COMPUTE_MAXS only to avoid frame computation errors
        val writer = ClassWriter(reader, ClassWriter.COMPUTE_MAXS)
        val remapperVisitor = ClassRemapper(writer, remapper)

        try {
            reader.accept(remapperVisitor, 0)
            return writer.toByteArray()
        } catch (e: Exception) {
            // If rewriting fails, return original bytecode
            println("Warning: Failed to rewrite class: ${e.message}")
            return bytecode
        }
    }

    /**
     * Rewrites multiple classes.
     */
    fun rewriteAll(classes: Map<String, ByteArray>): Map<String, ByteArray> {
        return classes.mapValues { (className, bytecode) ->
            try {
                rewrite(bytecode)
            } catch (e: Exception) {
                println("Warning: Failed to rewrite $className: ${e.message}")
                bytecode
            }
        }.mapKeys { (className, _) ->
            // Also remap the class name itself
            remapper.map(className)
        }
    }

    /**
     * Checks if a class name should be rewritten.
     */
    fun shouldRewrite(className: String): Boolean {
        return classMapping.containsKey(className) || className.startsWith("android/")
    }

    /**
     * Gets the mapped class name.
     */
    fun getMappedName(className: String): String {
        return remapper.map(className)
    }

    companion object {
        private val instance = BytecodeRewriter()

        fun rewrite(bytecode: ByteArray): ByteArray = instance.rewrite(bytecode)
        fun rewriteAll(classes: Map<String, ByteArray>): Map<String, ByteArray> = instance.rewriteAll(classes)
    }
}
