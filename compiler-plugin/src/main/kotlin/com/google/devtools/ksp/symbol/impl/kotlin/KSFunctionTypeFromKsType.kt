package com.google.devtools.ksp.symbol.impl.kotlin

import com.google.devtools.ksp.symbol.KSFunctionType
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import org.jetbrains.kotlin.builtins.getReceiverTypeFromFunctionType
import org.jetbrains.kotlin.builtins.isExtensionFunctionType
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.TypeProjection

class KSFunctionTypeFromKsType(
    private val kotlinType: KotlinType
) : KSFunctionType {
    private val hasReceiver = kotlinType.isExtensionFunctionType
    override val returnType: KSType? by lazy {
        getKSTypeCached(kotlinType.arguments.last().type)
    }
    override val parameterTypes: List<KSType?> by lazy {
        if (hasReceiver) {
            kotlinType.arguments.asSequence()
                .drop(1)
                .take(kotlinType.arguments.size - 2)
        } else {
            kotlinType.arguments.asSequence()
                .take(kotlinType.arguments.size - 1)
        }.mapTo(mutableListOf()) {
            getKSTypeCached(it.type)
        }
    }
    override val typeParameters: List<KSTypeParameter>
        get() = kotlinType.arguments.mapIndexed { index, typeProjection ->
            typeProjection.type.
        }
    override val extensionReceiverType: KSType? by lazy {
        kotlinType.getReceiverTypeFromFunctionType()?.let(::getKSTypeCached)
    }
}