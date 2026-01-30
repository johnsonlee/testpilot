package io.johnsonlee.testpilot.simulator

import com.android.tools.smali.dexlib2.DexFileFactory
import com.android.tools.smali.dexlib2.Opcodes
import com.android.tools.smali.dexlib2.iface.ClassDef
import com.android.tools.smali.dexlib2.iface.DexFile
import com.android.tools.smali.dexlib2.iface.Method
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes as AsmOpcodes
import java.io.File

/**
 * Converts DEX bytecode to JVM bytecode.
 *
 * Translates Dalvik instructions to JVM instructions using DexInstructionTranslator.
 */
class DexConverter(
    private val translateInstructions: Boolean = true
) {

    data class ConversionResult(
        val classes: Map<String, ByteArray>,  // className -> bytecode
        val errors: List<String>
    )

    /**
     * Converts a DEX file to JVM class files.
     */
    fun convert(dexFile: File): ConversionResult {
        val classes = mutableMapOf<String, ByteArray>()
        val errors = mutableListOf<String>()

        try {
            val dex: DexFile = DexFileFactory.loadDexFile(dexFile, Opcodes.forApi(33))

            for (classDef in dex.classes) {
                try {
                    val className = dexTypeToJvmType(classDef.type)
                    val bytecode = convertClass(classDef)
                    classes[className] = bytecode
                } catch (e: Exception) {
                    errors.add("Failed to convert ${classDef.type}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            errors.add("Failed to load DEX file: ${e.message}")
        }

        return ConversionResult(classes, errors)
    }

    private fun convertClass(classDef: ClassDef): ByteArray {
        // Use COMPUTE_MAXS only to avoid frame calculation errors
        // COMPUTE_FRAMES can fail when the bytecode structure is complex
        val cw = ClassWriter(ClassWriter.COMPUTE_MAXS)

        val className = dexTypeToJvmType(classDef.type)
        val superName = classDef.superclass?.let { dexTypeToJvmType(it) } ?: "java/lang/Object"
        val interfaces = classDef.interfaces.map { dexTypeToJvmType(it.toString()) }.toTypedArray()

        val access = convertAccessFlags(classDef.accessFlags)

        cw.visit(
            AsmOpcodes.V17,
            access,
            className,
            null,  // signature
            superName,
            interfaces
        )

        // Convert fields
        for (field in classDef.fields) {
            val fieldAccess = convertAccessFlags(field.accessFlags)
            val fieldName = field.name
            val fieldDescriptor = field.type

            cw.visitField(fieldAccess, fieldName, fieldDescriptor, null, null)?.visitEnd()
        }

        // Convert methods
        for (method in classDef.methods) {
            convertMethod(cw, method)
        }

        cw.visitEnd()
        return cw.toByteArray()
    }

    private fun convertMethod(cw: ClassWriter, method: Method) {
        val methodAccess = convertAccessFlags(method.accessFlags)
        val methodName = method.name
        val params = method.parameterTypes.joinToString("") { it.toString() }
        val returnType = method.returnType
        val descriptor = "($params)$returnType"

        val mv = cw.visitMethod(methodAccess, methodName, descriptor, null, null)
        mv ?: return

        // Native and abstract methods should not have code
        val isNative = (methodAccess and AsmOpcodes.ACC_NATIVE) != 0
        val isAbstract = (methodAccess and AsmOpcodes.ACC_ABSTRACT) != 0

        if (isNative || isAbstract) {
            mv.visitEnd()
            return
        }

        mv.visitCode()

        val implementation = method.implementation
        if (implementation != null && translateInstructions) {
            // Translate actual DEX instructions
            try {
                val isStatic = (methodAccess and AsmOpcodes.ACC_STATIC) != 0
                val paramTypes = method.parameterTypes.map { it.toString() }
                val translator = DexInstructionTranslator(mv, isStatic, paramTypes, returnType)
                translator.translate(implementation.instructions)
            } catch (e: Exception) {
                // Fall back to stub if translation fails
                generateStubBody(mv, returnType)
            }
        } else {
            // No implementation available, generate stub
            generateStubBody(mv, returnType)
        }

        mv.visitMaxs(0, 0)  // Computed by COMPUTE_MAXS
        mv.visitEnd()
    }

    private fun generateStubBody(mv: MethodVisitor, returnType: String) {
        when (returnType) {
            "V" -> {
                // void method - just return
                mv.visitInsn(AsmOpcodes.RETURN)
            }
            "Z", "B", "C", "S", "I" -> {
                // int-like types - return 0
                mv.visitInsn(AsmOpcodes.ICONST_0)
                mv.visitInsn(AsmOpcodes.IRETURN)
            }
            "J" -> {
                // long - return 0L
                mv.visitInsn(AsmOpcodes.LCONST_0)
                mv.visitInsn(AsmOpcodes.LRETURN)
            }
            "F" -> {
                // float - return 0.0f
                mv.visitInsn(AsmOpcodes.FCONST_0)
                mv.visitInsn(AsmOpcodes.FRETURN)
            }
            "D" -> {
                // double - return 0.0
                mv.visitInsn(AsmOpcodes.DCONST_0)
                mv.visitInsn(AsmOpcodes.DRETURN)
            }
            else -> {
                // object - return null
                mv.visitInsn(AsmOpcodes.ACONST_NULL)
                mv.visitInsn(AsmOpcodes.ARETURN)
            }
        }
    }

    private fun convertAccessFlags(dexFlags: Int): Int {
        // DEX and JVM access flags are mostly compatible
        var jvmFlags = 0

        if (dexFlags and 0x0001 != 0) jvmFlags = jvmFlags or AsmOpcodes.ACC_PUBLIC
        if (dexFlags and 0x0002 != 0) jvmFlags = jvmFlags or AsmOpcodes.ACC_PRIVATE
        if (dexFlags and 0x0004 != 0) jvmFlags = jvmFlags or AsmOpcodes.ACC_PROTECTED
        if (dexFlags and 0x0008 != 0) jvmFlags = jvmFlags or AsmOpcodes.ACC_STATIC
        if (dexFlags and 0x0010 != 0) jvmFlags = jvmFlags or AsmOpcodes.ACC_FINAL
        if (dexFlags and 0x0020 != 0) jvmFlags = jvmFlags or AsmOpcodes.ACC_SYNCHRONIZED
        if (dexFlags and 0x0040 != 0) jvmFlags = jvmFlags or AsmOpcodes.ACC_BRIDGE
        if (dexFlags and 0x0080 != 0) jvmFlags = jvmFlags or AsmOpcodes.ACC_VARARGS
        if (dexFlags and 0x0100 != 0) jvmFlags = jvmFlags or AsmOpcodes.ACC_NATIVE
        if (dexFlags and 0x0200 != 0) jvmFlags = jvmFlags or AsmOpcodes.ACC_INTERFACE
        if (dexFlags and 0x0400 != 0) jvmFlags = jvmFlags or AsmOpcodes.ACC_ABSTRACT
        if (dexFlags and 0x0800 != 0) jvmFlags = jvmFlags or AsmOpcodes.ACC_STRICT

        return jvmFlags
    }

    /**
     * Converts DEX type descriptor to JVM internal name.
     * e.g., "Lcom/example/Foo;" -> "com/example/Foo"
     */
    private fun dexTypeToJvmType(dexType: String): String {
        return when {
            dexType.startsWith("L") && dexType.endsWith(";") ->
                dexType.substring(1, dexType.length - 1)
            dexType.startsWith("[") ->
                "[" + dexTypeToJvmType(dexType.substring(1))
            else -> dexType
        }
    }

    companion object {
        fun convert(dexFile: File, translateInstructions: Boolean = true): ConversionResult =
            DexConverter(translateInstructions).convert(dexFile)
    }
}
