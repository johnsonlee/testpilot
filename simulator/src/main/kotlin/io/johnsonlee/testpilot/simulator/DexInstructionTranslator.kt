package io.johnsonlee.testpilot.simulator

import com.android.tools.smali.dexlib2.Opcode
import com.android.tools.smali.dexlib2.iface.instruction.*
import com.android.tools.smali.dexlib2.iface.instruction.formats.*
import com.android.tools.smali.dexlib2.iface.reference.*
import org.objectweb.asm.Label
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes as AsmOpcodes

/**
 * Translates DEX (Dalvik) instructions to JVM bytecode.
 *
 * DEX is register-based, JVM is stack-based. This translator:
 * 1. Maps DEX registers to JVM local variables
 * 2. Translates each DEX instruction to equivalent JVM instruction sequence
 * 3. Handles control flow (jumps, exceptions)
 */
class DexInstructionTranslator(
    private val mv: MethodVisitor,
    private val isStatic: Boolean,
    private val parameterTypes: List<String>,
    private val returnType: String,
    private val registerCount: Int
) {
    private enum class RegType { INT, LONG, FLOAT, DOUBLE, OBJECT, UNKNOWN }

    // Maps instruction offset to Label for jump targets
    private val labels = mutableMapOf<Int, Label>()

    /**
     * Tracks the last-known type of each Dalvik register so that type-polymorphic
     * instructions (if-eqz, if-eq, etc.) can emit the correct JVM opcode.
     */
    private val registerTypes = Array(registerCount) { RegType.UNKNOWN }

    /**
     * Number of Dalvik registers occupied by parameters (including `this` for instance methods).
     * Wide types (long, double) occupy 2 consecutive registers.
     */
    private val paramRegCount: Int = (if (isStatic) 0 else 1) +
        parameterTypes.fold(0) { acc, type -> acc + if (type == "J" || type == "D") 2 else 1 }

    /**
     * Number of Dalvik local (temporary) registers — those before the parameter registers.
     *
     * Dalvik register layout: [v0..v(localRegCount-1)] [p0..p(paramRegCount-1)]
     *     localRegCount = registerCount - paramRegCount
     */
    private val localRegCount: Int = registerCount - paramRegCount

    init {
        // Initialize parameter register types from method signature
        var reg = localRegCount
        if (!isStatic) {
            registerTypes[reg++] = RegType.OBJECT  // this
        }
        for (type in parameterTypes) {
            when (type) {
                "J" -> { registerTypes[reg] = RegType.LONG; reg += 2 }
                "D" -> { registerTypes[reg] = RegType.DOUBLE; reg += 2 }
                "F" -> { registerTypes[reg++] = RegType.FLOAT }
                "I", "Z", "B", "C", "S" -> { registerTypes[reg++] = RegType.INT }
                else -> { registerTypes[reg++] = RegType.OBJECT }
            }
        }
    }

    /**
     * Translates a sequence of DEX instructions.
     */
    fun translate(instructions: Iterable<Instruction>) {
        // First pass: collect all jump targets and create labels
        collectLabels(instructions)

        // Emit parameter copies: JVM puts params at fixed slots 0..paramRegCount-1,
        // but our type-split mapping uses different slots. Copy params to mapped slots.
        emitParameterCopies()

        // Second pass: translate instructions
        var offset = 0
        for (instruction in instructions) {
            // Emit label if this is a jump target
            labels[offset]?.let { mv.visitLabel(it) }

            currentOffset = offset
            translateInstruction(instruction)
            offset += instruction.codeUnits
        }
    }

    /** Position (in code units) of the instruction currently being translated. */
    private var currentOffset = 0

    /**
     * Copies parameters from their fixed JVM slots (0..paramRegCount-1) to
     * the type-split mapped slots used by all subsequent register accesses.
     */
    private fun emitParameterCopies() {
        var jvmSlot = 0
        var dalvikReg = localRegCount
        if (!isStatic) {
            mv.visitVarInsn(AsmOpcodes.ALOAD, jvmSlot)
            mv.visitVarInsn(AsmOpcodes.ASTORE, mapReg(dalvikReg, TYPE_OBJECT))
            jvmSlot++
            dalvikReg++
        }
        for (type in parameterTypes) {
            when (type) {
                "J" -> {
                    mv.visitVarInsn(AsmOpcodes.LLOAD, jvmSlot)
                    mv.visitVarInsn(AsmOpcodes.LSTORE, mapReg(dalvikReg, TYPE_LONG))
                    jvmSlot += 2; dalvikReg += 2
                }
                "D" -> {
                    mv.visitVarInsn(AsmOpcodes.DLOAD, jvmSlot)
                    mv.visitVarInsn(AsmOpcodes.DSTORE, mapReg(dalvikReg, TYPE_DOUBLE))
                    jvmSlot += 2; dalvikReg += 2
                }
                "F" -> {
                    mv.visitVarInsn(AsmOpcodes.FLOAD, jvmSlot)
                    mv.visitVarInsn(AsmOpcodes.FSTORE, mapReg(dalvikReg, TYPE_FLOAT))
                    jvmSlot++; dalvikReg++
                }
                "I", "Z", "B", "C", "S" -> {
                    mv.visitVarInsn(AsmOpcodes.ILOAD, jvmSlot)
                    mv.visitVarInsn(AsmOpcodes.ISTORE, mapReg(dalvikReg, TYPE_INT))
                    jvmSlot++; dalvikReg++
                }
                else -> {
                    mv.visitVarInsn(AsmOpcodes.ALOAD, jvmSlot)
                    mv.visitVarInsn(AsmOpcodes.ASTORE, mapReg(dalvikReg, TYPE_OBJECT))
                    jvmSlot++; dalvikReg++
                }
            }
        }
    }

    private fun collectLabels(instructions: Iterable<Instruction>) {
        var offset = 0
        for (instruction in instructions) {
            when (instruction) {
                is OffsetInstruction -> {
                    val target = offset + instruction.codeOffset
                    labels.getOrPut(target) { Label() }
                }
            }
            offset += instruction.codeUnits
        }
    }

    private fun translateInstruction(instruction: Instruction) {
        when (instruction.opcode) {
            // Constants
            Opcode.CONST_4, Opcode.CONST_16, Opcode.CONST, Opcode.CONST_HIGH16 ->
                translateConst(instruction)
            Opcode.CONST_WIDE_16, Opcode.CONST_WIDE_32, Opcode.CONST_WIDE, Opcode.CONST_WIDE_HIGH16 ->
                translateConstWide(instruction)
            Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO ->
                translateConstString(instruction)

            // Move operations
            Opcode.MOVE, Opcode.MOVE_FROM16, Opcode.MOVE_16 ->
                translateMove(instruction)
            Opcode.MOVE_WIDE, Opcode.MOVE_WIDE_FROM16, Opcode.MOVE_WIDE_16 ->
                translateMoveWide(instruction)
            Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16 ->
                translateMoveObject(instruction)
            Opcode.MOVE_RESULT ->
                translateMoveResult(instruction)
            Opcode.MOVE_RESULT_WIDE ->
                translateMoveResultWide(instruction)
            Opcode.MOVE_RESULT_OBJECT ->
                translateMoveResultObject(instruction)

            // Return operations
            Opcode.RETURN_VOID -> mv.visitInsn(AsmOpcodes.RETURN)
            Opcode.RETURN -> translateReturn(instruction)
            Opcode.RETURN_WIDE -> translateReturnWide(instruction)
            Opcode.RETURN_OBJECT -> translateReturnObject(instruction)

            // Invoke operations
            Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE ->
                translateInvokeVirtual(instruction)
            Opcode.INVOKE_SUPER, Opcode.INVOKE_SUPER_RANGE ->
                translateInvokeSuper(instruction)
            Opcode.INVOKE_DIRECT, Opcode.INVOKE_DIRECT_RANGE ->
                translateInvokeDirect(instruction)
            Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE ->
                translateInvokeStatic(instruction)
            Opcode.INVOKE_INTERFACE, Opcode.INVOKE_INTERFACE_RANGE ->
                translateInvokeInterface(instruction)

            // Field operations
            Opcode.IGET, Opcode.IGET_WIDE, Opcode.IGET_OBJECT,
            Opcode.IGET_BOOLEAN, Opcode.IGET_BYTE, Opcode.IGET_CHAR, Opcode.IGET_SHORT ->
                translateIGet(instruction)
            Opcode.IPUT, Opcode.IPUT_WIDE, Opcode.IPUT_OBJECT,
            Opcode.IPUT_BOOLEAN, Opcode.IPUT_BYTE, Opcode.IPUT_CHAR, Opcode.IPUT_SHORT ->
                translateIPut(instruction)
            Opcode.SGET, Opcode.SGET_WIDE, Opcode.SGET_OBJECT,
            Opcode.SGET_BOOLEAN, Opcode.SGET_BYTE, Opcode.SGET_CHAR, Opcode.SGET_SHORT ->
                translateSGet(instruction)
            Opcode.SPUT, Opcode.SPUT_WIDE, Opcode.SPUT_OBJECT,
            Opcode.SPUT_BOOLEAN, Opcode.SPUT_BYTE, Opcode.SPUT_CHAR, Opcode.SPUT_SHORT ->
                translateSPut(instruction)

            // New instance
            Opcode.NEW_INSTANCE -> translateNewInstance(instruction)
            Opcode.NEW_ARRAY -> translateNewArray(instruction)

            // Conditionals
            Opcode.IF_EQ, Opcode.IF_NE, Opcode.IF_LT, Opcode.IF_GE, Opcode.IF_GT, Opcode.IF_LE ->
                translateIfCmp(instruction)
            Opcode.IF_EQZ, Opcode.IF_NEZ, Opcode.IF_LTZ, Opcode.IF_GEZ, Opcode.IF_GTZ, Opcode.IF_LEZ ->
                translateIfZ(instruction)

            // Unconditional jump
            Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32 ->
                translateGoto(instruction)

            // Arithmetic
            Opcode.ADD_INT, Opcode.ADD_INT_2ADDR, Opcode.ADD_INT_LIT8, Opcode.ADD_INT_LIT16 ->
                translateBinaryOp(instruction, AsmOpcodes.IADD)
            Opcode.SUB_INT, Opcode.SUB_INT_2ADDR ->
                translateBinaryOp(instruction, AsmOpcodes.ISUB)
            Opcode.MUL_INT, Opcode.MUL_INT_2ADDR, Opcode.MUL_INT_LIT8, Opcode.MUL_INT_LIT16 ->
                translateBinaryOp(instruction, AsmOpcodes.IMUL)
            Opcode.DIV_INT, Opcode.DIV_INT_2ADDR, Opcode.DIV_INT_LIT8, Opcode.DIV_INT_LIT16 ->
                translateBinaryOp(instruction, AsmOpcodes.IDIV)

            // NOP
            Opcode.NOP -> mv.visitInsn(AsmOpcodes.NOP)

            // Throw
            Opcode.THROW -> translateThrow(instruction)

            // Check-cast
            Opcode.CHECK_CAST -> translateCheckCast(instruction)

            // Instance-of
            Opcode.INSTANCE_OF -> translateInstanceOf(instruction)

            // Array operations
            Opcode.AGET, Opcode.AGET_WIDE, Opcode.AGET_OBJECT,
            Opcode.AGET_BOOLEAN, Opcode.AGET_BYTE, Opcode.AGET_CHAR, Opcode.AGET_SHORT ->
                translateArrayGet(instruction)
            Opcode.APUT, Opcode.APUT_WIDE, Opcode.APUT_OBJECT,
            Opcode.APUT_BOOLEAN, Opcode.APUT_BYTE, Opcode.APUT_CHAR, Opcode.APUT_SHORT ->
                translateArrayPut(instruction)
            Opcode.ARRAY_LENGTH -> translateArrayLength(instruction)

            else -> {
                // Skip unsupported opcodes — registers they would have written
                // remain uninitialized, but this avoids corrupting the JVM stack.
            }
        }
    }

    // ========== Instruction translation methods ==========

    private fun translateConst(instruction: Instruction) {
        val narrow = instruction as NarrowLiteralInstruction
        val reg = (instruction as OneRegisterInstruction).registerA
        mv.visitLdcInsn(narrow.narrowLiteral)
        storeInt(reg)
    }

    private fun translateConstWide(instruction: Instruction) {
        val wide = instruction as WideLiteralInstruction
        val reg = (instruction as OneRegisterInstruction).registerA
        mv.visitLdcInsn(wide.wideLiteral)
        storeLong(reg)
    }

    private fun translateConstString(instruction: Instruction) {
        val ref = instruction as ReferenceInstruction
        val reg = (instruction as OneRegisterInstruction).registerA
        val stringRef = ref.reference as StringReference
        mv.visitLdcInsn(stringRef.string)
        storeObject(reg)
    }

    private fun translateMove(instruction: Instruction) {
        val two = instruction as TwoRegisterInstruction
        loadInt(two.registerB)
        storeInt(two.registerA)
    }

    private fun translateMoveWide(instruction: Instruction) {
        val two = instruction as TwoRegisterInstruction
        loadLong(two.registerB)
        storeLong(two.registerA)
    }

    private fun translateMoveObject(instruction: Instruction) {
        val two = instruction as TwoRegisterInstruction
        loadObject(two.registerB)
        storeObject(two.registerA)
    }

    private fun translateMoveResult(instruction: Instruction) {
        val reg = (instruction as OneRegisterInstruction).registerA
        // Result is on stack from previous invoke
        storeInt(reg)
    }

    private fun translateMoveResultWide(instruction: Instruction) {
        val reg = (instruction as OneRegisterInstruction).registerA
        storeLong(reg)
    }

    private fun translateMoveResultObject(instruction: Instruction) {
        val reg = (instruction as OneRegisterInstruction).registerA
        storeObject(reg)
    }

    private fun translateReturn(instruction: Instruction) {
        val reg = (instruction as OneRegisterInstruction).registerA
        loadInt(reg)
        mv.visitInsn(AsmOpcodes.IRETURN)
    }

    private fun translateReturnWide(instruction: Instruction) {
        val reg = (instruction as OneRegisterInstruction).registerA
        loadLong(reg)
        mv.visitInsn(AsmOpcodes.LRETURN)
    }

    private fun translateReturnObject(instruction: Instruction) {
        val reg = (instruction as OneRegisterInstruction).registerA
        loadObject(reg)
        mv.visitInsn(AsmOpcodes.ARETURN)
    }

    private fun translateInvokeVirtual(instruction: Instruction) {
        translateInvoke(instruction, AsmOpcodes.INVOKEVIRTUAL, false)
    }

    private fun translateInvokeSuper(instruction: Instruction) {
        translateInvoke(instruction, AsmOpcodes.INVOKESPECIAL, false)
    }

    private fun translateInvokeDirect(instruction: Instruction) {
        translateInvoke(instruction, AsmOpcodes.INVOKESPECIAL, false)
    }

    private fun translateInvokeStatic(instruction: Instruction) {
        translateInvoke(instruction, AsmOpcodes.INVOKESTATIC, false)
    }

    private fun translateInvokeInterface(instruction: Instruction) {
        translateInvoke(instruction, AsmOpcodes.INVOKEINTERFACE, true)
    }

    private fun translateInvoke(instruction: Instruction, opcode: Int, isInterface: Boolean) {
        val ref = instruction as ReferenceInstruction
        val methodRef = ref.reference as MethodReference

        val owner = dexTypeToJvmType(methodRef.definingClass)
        val name = methodRef.name
        val desc = buildMethodDescriptor(methodRef)

        // Load arguments
        when (instruction) {
            is FiveRegisterInstruction -> {
                val regs = listOf(
                    instruction.registerC,
                    instruction.registerD,
                    instruction.registerE,
                    instruction.registerF,
                    instruction.registerG
                ).take(instruction.registerCount)
                loadArgumentsForInvoke(regs, methodRef, opcode != AsmOpcodes.INVOKESTATIC)
            }
            is RegisterRangeInstruction -> {
                val regs = (instruction.startRegister until instruction.startRegister + instruction.registerCount).toList()
                loadArgumentsForInvoke(regs, methodRef, opcode != AsmOpcodes.INVOKESTATIC)
            }
        }

        mv.visitMethodInsn(opcode, owner, name, desc, isInterface)
    }

    private fun loadArgumentsForInvoke(regs: List<Int>, methodRef: MethodReference, hasReceiver: Boolean) {
        var regIndex = 0

        // Load receiver if needed
        if (hasReceiver && regs.isNotEmpty()) {
            loadObject(regs[regIndex++])
        }

        // Load parameters
        for (paramType in methodRef.parameterTypes) {
            if (regIndex >= regs.size) break
            when (paramType.toString()) {
                "J" -> { loadLong(regs[regIndex]); regIndex += 2 }
                "D" -> { loadDouble(regs[regIndex]); regIndex += 2 }
                "F" -> { loadFloat(regs[regIndex++]) }
                "I", "Z", "B", "C", "S" -> { loadInt(regs[regIndex++]) }
                else -> { loadObject(regs[regIndex++]) }
            }
        }
    }

    private fun translateIGet(instruction: Instruction) {
        val two = instruction as TwoRegisterInstruction
        val ref = instruction as ReferenceInstruction
        val fieldRef = ref.reference as FieldReference

        loadObject(two.registerB)  // object reference
        mv.visitFieldInsn(
            AsmOpcodes.GETFIELD,
            dexTypeToJvmType(fieldRef.definingClass),
            fieldRef.name,
            fieldRef.type
        )
        storeByType(two.registerA, fieldRef.type)
    }

    private fun translateIPut(instruction: Instruction) {
        val two = instruction as TwoRegisterInstruction
        val ref = instruction as ReferenceInstruction
        val fieldRef = ref.reference as FieldReference

        loadObject(two.registerB)  // object reference
        loadByType(two.registerA, fieldRef.type)  // value
        mv.visitFieldInsn(
            AsmOpcodes.PUTFIELD,
            dexTypeToJvmType(fieldRef.definingClass),
            fieldRef.name,
            fieldRef.type
        )
    }

    private fun translateSGet(instruction: Instruction) {
        val one = instruction as OneRegisterInstruction
        val ref = instruction as ReferenceInstruction
        val fieldRef = ref.reference as FieldReference

        mv.visitFieldInsn(
            AsmOpcodes.GETSTATIC,
            dexTypeToJvmType(fieldRef.definingClass),
            fieldRef.name,
            fieldRef.type
        )
        storeByType(one.registerA, fieldRef.type)
    }

    private fun translateSPut(instruction: Instruction) {
        val one = instruction as OneRegisterInstruction
        val ref = instruction as ReferenceInstruction
        val fieldRef = ref.reference as FieldReference

        loadByType(one.registerA, fieldRef.type)
        mv.visitFieldInsn(
            AsmOpcodes.PUTSTATIC,
            dexTypeToJvmType(fieldRef.definingClass),
            fieldRef.name,
            fieldRef.type
        )
    }

    private fun translateNewInstance(instruction: Instruction) {
        val one = instruction as OneRegisterInstruction
        val ref = instruction as ReferenceInstruction
        val typeRef = ref.reference as TypeReference

        val type = dexTypeToJvmType(typeRef.type)
        mv.visitTypeInsn(AsmOpcodes.NEW, type)
        mv.visitInsn(AsmOpcodes.DUP)
        storeObject(one.registerA)
    }

    private fun translateNewArray(instruction: Instruction) {
        val two = instruction as TwoRegisterInstruction
        val ref = instruction as ReferenceInstruction
        val typeRef = ref.reference as TypeReference

        loadInt(two.registerB)  // size
        val elementType = typeRef.type.substring(1)  // Remove leading '['
        when (elementType) {
            "I" -> mv.visitIntInsn(AsmOpcodes.NEWARRAY, AsmOpcodes.T_INT)
            "J" -> mv.visitIntInsn(AsmOpcodes.NEWARRAY, AsmOpcodes.T_LONG)
            "F" -> mv.visitIntInsn(AsmOpcodes.NEWARRAY, AsmOpcodes.T_FLOAT)
            "D" -> mv.visitIntInsn(AsmOpcodes.NEWARRAY, AsmOpcodes.T_DOUBLE)
            "Z" -> mv.visitIntInsn(AsmOpcodes.NEWARRAY, AsmOpcodes.T_BOOLEAN)
            "B" -> mv.visitIntInsn(AsmOpcodes.NEWARRAY, AsmOpcodes.T_BYTE)
            "C" -> mv.visitIntInsn(AsmOpcodes.NEWARRAY, AsmOpcodes.T_CHAR)
            "S" -> mv.visitIntInsn(AsmOpcodes.NEWARRAY, AsmOpcodes.T_SHORT)
            else -> mv.visitTypeInsn(AsmOpcodes.ANEWARRAY, dexTypeToJvmType(elementType))
        }
        storeObject(two.registerA)
    }

    private fun translateIfCmp(instruction: Instruction) {
        val opcode = instruction.opcode
        val two = instruction as TwoRegisterInstruction
        val offsetInstr = instruction as OffsetInstruction
        val target = labels[currentOffset + offsetInstr.codeOffset] ?: Label()

        val eitherObject = regType(two.registerA) == RegType.OBJECT
            || regType(two.registerB) == RegType.OBJECT

        // DEX if-eq/if-ne can compare objects (reference equality).
        // JVM requires IF_ACMPEQ/IF_ACMPNE for references, IF_ICMPEQ/IF_ICMPNE for ints.
        // LT/GE/GT/LE are always integer comparisons.
        if (eitherObject && (opcode == Opcode.IF_EQ || opcode == Opcode.IF_NE)) {
            loadObject(two.registerA)
            loadObject(two.registerB)
            val jvmOpcode = if (opcode == Opcode.IF_EQ) AsmOpcodes.IF_ACMPEQ else AsmOpcodes.IF_ACMPNE
            mv.visitJumpInsn(jvmOpcode, target)
        } else {
            loadInt(two.registerA)
            loadInt(two.registerB)
            val jvmOpcode = when (opcode) {
                Opcode.IF_EQ -> AsmOpcodes.IF_ICMPEQ
                Opcode.IF_NE -> AsmOpcodes.IF_ICMPNE
                Opcode.IF_LT -> AsmOpcodes.IF_ICMPLT
                Opcode.IF_GE -> AsmOpcodes.IF_ICMPGE
                Opcode.IF_GT -> AsmOpcodes.IF_ICMPGT
                Opcode.IF_LE -> AsmOpcodes.IF_ICMPLE
                else -> throw IllegalStateException("Unknown if opcode: $opcode")
            }
            mv.visitJumpInsn(jvmOpcode, target)
        }
    }

    private fun translateIfZ(instruction: Instruction) {
        val opcode = instruction.opcode
        val one = instruction as OneRegisterInstruction
        val offsetInstr = instruction as OffsetInstruction
        val target = labels[currentOffset + offsetInstr.codeOffset] ?: Label()

        val isObject = regType(one.registerA) == RegType.OBJECT

        // DEX if-eqz/if-nez work on both ints (== 0) and objects (== null).
        // JVM requires IFNULL/IFNONNULL for references, IFEQ/IFNE for ints.
        // LTZ/GEZ/GTZ/LEZ are always integer comparisons.
        if (isObject && (opcode == Opcode.IF_EQZ || opcode == Opcode.IF_NEZ)) {
            loadObject(one.registerA)
            val jvmOpcode = if (opcode == Opcode.IF_EQZ) AsmOpcodes.IFNULL else AsmOpcodes.IFNONNULL
            mv.visitJumpInsn(jvmOpcode, target)
        } else {
            loadInt(one.registerA)
            val jvmOpcode = when (opcode) {
                Opcode.IF_EQZ -> AsmOpcodes.IFEQ
                Opcode.IF_NEZ -> AsmOpcodes.IFNE
                Opcode.IF_LTZ -> AsmOpcodes.IFLT
                Opcode.IF_GEZ -> AsmOpcodes.IFGE
                Opcode.IF_GTZ -> AsmOpcodes.IFGT
                Opcode.IF_LEZ -> AsmOpcodes.IFLE
                else -> throw IllegalStateException("Unknown ifz opcode: $opcode")
            }
            mv.visitJumpInsn(jvmOpcode, target)
        }
    }

    private fun translateGoto(instruction: Instruction) {
        val offsetInstr = instruction as OffsetInstruction
        val target = labels[currentOffset + offsetInstr.codeOffset] ?: Label()
        mv.visitJumpInsn(AsmOpcodes.GOTO, target)
    }

    private fun translateBinaryOp(instruction: Instruction, jvmOpcode: Int) {
        when (instruction) {
            is Instruction23x -> {
                loadInt(instruction.registerB)
                loadInt(instruction.registerC)
                mv.visitInsn(jvmOpcode)
                storeInt(instruction.registerA)
            }
            is Instruction12x -> {
                loadInt(instruction.registerA)
                loadInt(instruction.registerB)
                mv.visitInsn(jvmOpcode)
                storeInt(instruction.registerA)
            }
            is Instruction22s -> {
                loadInt(instruction.registerB)
                mv.visitLdcInsn(instruction.narrowLiteral)
                mv.visitInsn(jvmOpcode)
                storeInt(instruction.registerA)
            }
            is Instruction22b -> {
                loadInt(instruction.registerB)
                mv.visitLdcInsn(instruction.narrowLiteral)
                mv.visitInsn(jvmOpcode)
                storeInt(instruction.registerA)
            }
        }
    }

    private fun translateThrow(instruction: Instruction) {
        val one = instruction as OneRegisterInstruction
        loadObject(one.registerA)
        mv.visitInsn(AsmOpcodes.ATHROW)
    }

    private fun translateCheckCast(instruction: Instruction) {
        val one = instruction as OneRegisterInstruction
        val ref = instruction as ReferenceInstruction
        val typeRef = ref.reference as TypeReference

        loadObject(one.registerA)
        mv.visitTypeInsn(AsmOpcodes.CHECKCAST, dexTypeToJvmType(typeRef.type))
        storeObject(one.registerA)
    }

    private fun translateInstanceOf(instruction: Instruction) {
        val two = instruction as TwoRegisterInstruction
        val ref = instruction as ReferenceInstruction
        val typeRef = ref.reference as TypeReference

        loadObject(two.registerB)
        mv.visitTypeInsn(AsmOpcodes.INSTANCEOF, dexTypeToJvmType(typeRef.type))
        storeInt(two.registerA)
    }

    private fun translateArrayGet(instruction: Instruction) {
        val three = instruction as Instruction23x
        loadObject(three.registerB)  // array
        loadInt(three.registerC)      // index

        val loadOpcode = when (instruction.opcode) {
            Opcode.AGET -> AsmOpcodes.IALOAD
            Opcode.AGET_WIDE -> AsmOpcodes.LALOAD
            Opcode.AGET_OBJECT -> AsmOpcodes.AALOAD
            Opcode.AGET_BOOLEAN -> AsmOpcodes.BALOAD
            Opcode.AGET_BYTE -> AsmOpcodes.BALOAD
            Opcode.AGET_CHAR -> AsmOpcodes.CALOAD
            Opcode.AGET_SHORT -> AsmOpcodes.SALOAD
            else -> AsmOpcodes.AALOAD
        }
        mv.visitInsn(loadOpcode)

        when (instruction.opcode) {
            Opcode.AGET_WIDE -> storeLong(three.registerA)
            Opcode.AGET_OBJECT -> storeObject(three.registerA)
            else -> storeInt(three.registerA)
        }
    }

    private fun translateArrayPut(instruction: Instruction) {
        val three = instruction as Instruction23x
        loadObject(three.registerB)  // array
        loadInt(three.registerC)      // index

        when (instruction.opcode) {
            Opcode.APUT_WIDE -> loadLong(three.registerA)
            Opcode.APUT_OBJECT -> loadObject(three.registerA)
            else -> loadInt(three.registerA)
        }

        val storeOpcode = when (instruction.opcode) {
            Opcode.APUT -> AsmOpcodes.IASTORE
            Opcode.APUT_WIDE -> AsmOpcodes.LASTORE
            Opcode.APUT_OBJECT -> AsmOpcodes.AASTORE
            Opcode.APUT_BOOLEAN -> AsmOpcodes.BASTORE
            Opcode.APUT_BYTE -> AsmOpcodes.BASTORE
            Opcode.APUT_CHAR -> AsmOpcodes.CASTORE
            Opcode.APUT_SHORT -> AsmOpcodes.SASTORE
            else -> AsmOpcodes.AASTORE
        }
        mv.visitInsn(storeOpcode)
    }

    private fun translateArrayLength(instruction: Instruction) {
        val two = instruction as TwoRegisterInstruction
        loadObject(two.registerB)
        mv.visitInsn(AsmOpcodes.ARRAYLENGTH)
        storeInt(two.registerA)
    }

    // ========== Helper methods ==========

    /**
     * Maps a (Dalvik register, type category) pair to a JVM local variable slot.
     *
     * Dalvik registers are untyped — the same register can hold an int at one point
     * and an object at another. JVM local variable slots are typed and the verifier
     * rejects type conflicts. To solve this, each Dalvik register gets [NUM_TYPE_SLOTS]
     * separate JVM slots (one per type category). Parameters are copied from their
     * fixed JVM slots to mapped slots at method entry (see [emitParameterCopies]).
     *
     * Layout: [param slots 0..paramRegCount-1] [mapped: reg0*5+type, reg1*5+type, ...]
     */
    private fun mapReg(dalvikReg: Int, typeSlot: Int): Int {
        return paramRegCount + dalvikReg * NUM_TYPE_SLOTS + typeSlot
    }

    private fun setRegType(reg: Int, type: RegType) {
        if (reg in registerTypes.indices) registerTypes[reg] = type
    }

    private fun regType(reg: Int): RegType =
        if (reg in registerTypes.indices) registerTypes[reg] else RegType.UNKNOWN

    private fun loadInt(reg: Int) = mv.visitVarInsn(AsmOpcodes.ILOAD, mapReg(reg, TYPE_INT))
    private fun storeInt(reg: Int) { mv.visitVarInsn(AsmOpcodes.ISTORE, mapReg(reg, TYPE_INT)); setRegType(reg, RegType.INT) }
    private fun loadLong(reg: Int) = mv.visitVarInsn(AsmOpcodes.LLOAD, mapReg(reg, TYPE_LONG))
    private fun storeLong(reg: Int) { mv.visitVarInsn(AsmOpcodes.LSTORE, mapReg(reg, TYPE_LONG)); setRegType(reg, RegType.LONG) }
    private fun loadFloat(reg: Int) = mv.visitVarInsn(AsmOpcodes.FLOAD, mapReg(reg, TYPE_FLOAT))
    private fun storeFloat(reg: Int) { mv.visitVarInsn(AsmOpcodes.FSTORE, mapReg(reg, TYPE_FLOAT)); setRegType(reg, RegType.FLOAT) }
    private fun loadDouble(reg: Int) = mv.visitVarInsn(AsmOpcodes.DLOAD, mapReg(reg, TYPE_DOUBLE))
    private fun storeDouble(reg: Int) { mv.visitVarInsn(AsmOpcodes.DSTORE, mapReg(reg, TYPE_DOUBLE)); setRegType(reg, RegType.DOUBLE) }
    private fun loadObject(reg: Int) = mv.visitVarInsn(AsmOpcodes.ALOAD, mapReg(reg, TYPE_OBJECT))
    private fun storeObject(reg: Int) { mv.visitVarInsn(AsmOpcodes.ASTORE, mapReg(reg, TYPE_OBJECT)); setRegType(reg, RegType.OBJECT) }

    private fun loadByType(reg: Int, type: String) {
        when (type) {
            "J" -> loadLong(reg)
            "D" -> loadDouble(reg)
            "F" -> loadFloat(reg)
            "I", "Z", "B", "C", "S" -> loadInt(reg)
            else -> loadObject(reg)
        }
    }

    private fun storeByType(reg: Int, type: String) {
        when (type) {
            "J" -> storeLong(reg)
            "D" -> storeDouble(reg)
            "F" -> storeFloat(reg)
            "I", "Z", "B", "C", "S" -> storeInt(reg)
            else -> storeObject(reg)
        }
    }

    private fun dexTypeToJvmType(dexType: String): String {
        return when {
            dexType.startsWith("L") && dexType.endsWith(";") ->
                dexType.substring(1, dexType.length - 1)
            dexType.startsWith("[") ->
                dexType  // Array descriptors are identical in DEX and JVM
            else -> dexType
        }
    }

    private fun buildMethodDescriptor(methodRef: MethodReference): String {
        val params = methodRef.parameterTypes.joinToString("") { it.toString() }
        return "($params)${methodRef.returnType}"
    }

    companion object {
        private const val TYPE_INT = 0
        private const val TYPE_LONG = 1
        private const val TYPE_FLOAT = 2
        private const val TYPE_DOUBLE = 3
        private const val TYPE_OBJECT = 4
        private const val NUM_TYPE_SLOTS = 5

        /** All opcodes handled by [translateInstruction]. */
        private val SUPPORTED_OPCODES: Set<Opcode> = setOf(
            Opcode.CONST_4, Opcode.CONST_16, Opcode.CONST, Opcode.CONST_HIGH16,
            Opcode.CONST_WIDE_16, Opcode.CONST_WIDE_32, Opcode.CONST_WIDE, Opcode.CONST_WIDE_HIGH16,
            Opcode.CONST_STRING, Opcode.CONST_STRING_JUMBO,
            Opcode.MOVE, Opcode.MOVE_FROM16, Opcode.MOVE_16,
            Opcode.MOVE_WIDE, Opcode.MOVE_WIDE_FROM16, Opcode.MOVE_WIDE_16,
            Opcode.MOVE_OBJECT, Opcode.MOVE_OBJECT_FROM16, Opcode.MOVE_OBJECT_16,
            Opcode.MOVE_RESULT, Opcode.MOVE_RESULT_WIDE, Opcode.MOVE_RESULT_OBJECT,
            Opcode.RETURN_VOID, Opcode.RETURN, Opcode.RETURN_WIDE, Opcode.RETURN_OBJECT,
            Opcode.INVOKE_VIRTUAL, Opcode.INVOKE_VIRTUAL_RANGE,
            Opcode.INVOKE_SUPER, Opcode.INVOKE_SUPER_RANGE,
            Opcode.INVOKE_DIRECT, Opcode.INVOKE_DIRECT_RANGE,
            Opcode.INVOKE_STATIC, Opcode.INVOKE_STATIC_RANGE,
            Opcode.INVOKE_INTERFACE, Opcode.INVOKE_INTERFACE_RANGE,
            Opcode.IGET, Opcode.IGET_WIDE, Opcode.IGET_OBJECT,
            Opcode.IGET_BOOLEAN, Opcode.IGET_BYTE, Opcode.IGET_CHAR, Opcode.IGET_SHORT,
            Opcode.IPUT, Opcode.IPUT_WIDE, Opcode.IPUT_OBJECT,
            Opcode.IPUT_BOOLEAN, Opcode.IPUT_BYTE, Opcode.IPUT_CHAR, Opcode.IPUT_SHORT,
            Opcode.SGET, Opcode.SGET_WIDE, Opcode.SGET_OBJECT,
            Opcode.SGET_BOOLEAN, Opcode.SGET_BYTE, Opcode.SGET_CHAR, Opcode.SGET_SHORT,
            Opcode.SPUT, Opcode.SPUT_WIDE, Opcode.SPUT_OBJECT,
            Opcode.SPUT_BOOLEAN, Opcode.SPUT_BYTE, Opcode.SPUT_CHAR, Opcode.SPUT_SHORT,
            Opcode.NEW_INSTANCE, Opcode.NEW_ARRAY,
            Opcode.IF_EQ, Opcode.IF_NE, Opcode.IF_LT, Opcode.IF_GE, Opcode.IF_GT, Opcode.IF_LE,
            Opcode.IF_EQZ, Opcode.IF_NEZ, Opcode.IF_LTZ, Opcode.IF_GEZ, Opcode.IF_GTZ, Opcode.IF_LEZ,
            Opcode.GOTO, Opcode.GOTO_16, Opcode.GOTO_32,
            Opcode.ADD_INT, Opcode.ADD_INT_2ADDR, Opcode.ADD_INT_LIT8, Opcode.ADD_INT_LIT16,
            Opcode.SUB_INT, Opcode.SUB_INT_2ADDR,
            Opcode.MUL_INT, Opcode.MUL_INT_2ADDR, Opcode.MUL_INT_LIT8, Opcode.MUL_INT_LIT16,
            Opcode.DIV_INT, Opcode.DIV_INT_2ADDR, Opcode.DIV_INT_LIT8, Opcode.DIV_INT_LIT16,
            Opcode.NOP, Opcode.THROW, Opcode.CHECK_CAST, Opcode.INSTANCE_OF,
            Opcode.AGET, Opcode.AGET_WIDE, Opcode.AGET_OBJECT,
            Opcode.AGET_BOOLEAN, Opcode.AGET_BYTE, Opcode.AGET_CHAR, Opcode.AGET_SHORT,
            Opcode.APUT, Opcode.APUT_WIDE, Opcode.APUT_OBJECT,
            Opcode.APUT_BOOLEAN, Opcode.APUT_BYTE, Opcode.APUT_CHAR, Opcode.APUT_SHORT,
            Opcode.ARRAY_LENGTH,
        )

        /**
         * Pre-scans instructions to check if all opcodes are translatable.
         * Methods with unsupported opcodes should use stub bodies to avoid
         * producing invalid JVM bytecode (uninitialized locals, stack corruption).
         */
        fun canTranslate(instructions: Iterable<Instruction>): Boolean =
            instructions.all { it.opcode in SUPPORTED_OPCODES }
    }
}
