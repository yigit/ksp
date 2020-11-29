/*
 * Copyright 2020 Google LLC
 * Copyright 2010-2020 JetBrains s.r.o. and Kotlin Programming Language contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package com.google.devtools.ksp.symbol.impl.kotlin

import com.google.devtools.ksp.processing.impl.ResolverImpl
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.symbol.impl.KSObjectCache
import com.google.devtools.ksp.symbol.impl.binary.KSTypeArgumentDescriptorImpl
import com.google.devtools.ksp.symbol.impl.replaceTypeArguments
import org.jetbrains.kotlin.descriptors.NotFoundClasses
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.getAbbreviation
import org.jetbrains.kotlin.types.isError
import org.jetbrains.kotlin.types.typeUtil.*

class KSTypeImpl private constructor(
    val kotlinType: KotlinType,
    private val ksTypeArguments: List<KSTypeArgument>? = null,
    override val annotations: List<KSAnnotation> = listOf()
) : KSType {
    companion object : KSObjectCache<IdKey<KotlinType>, KSTypeImpl>() {
        fun getCached(
            kotlinType: KotlinType,
            ksTypeArguments: List<KSTypeArgument>? = null,
            annotations: List<KSAnnotation> = listOf()
        ): KSTypeImpl {
            return cache.getOrPut(IdKey(kotlinType)) { KSTypeImpl(kotlinType, ksTypeArguments, annotations) }
        }
    }

    override val declaration: KSDeclaration by lazy {
        ResolverImpl.instance.findDeclaration(kotlinType.getAbbreviation() ?: kotlinType)
    }

    override val nullability: Nullability by lazy {
        when (kotlinType.nullability()) {
            TypeNullability.NULLABLE -> Nullability.NULLABLE
            TypeNullability.NOT_NULL -> Nullability.NOT_NULL
            TypeNullability.FLEXIBLE -> Nullability.PLATFORM
        }
    }

    /**
     * Even though that [KSTypeArgumentDescriptorImpl] is no heavier than [ksTypeArguments], the former doesn't carry [KSAnnotation].
     */
    override val arguments: List<KSTypeArgument> by lazy {
        ksTypeArguments ?: kotlinType.arguments.map {
            // TODO where can we get this origin from?
            KSTypeArgumentDescriptorImpl.getCached(it, Origin.CLASS)
        }
    }

    override fun isAssignableFrom(that: KSType): Boolean = (that as? KSTypeImpl)?.kotlinType?.isSubtypeOf(kotlinType) == true

    // TODO: find a better way to reuse the logic in [DescriptorRendererImpl.renderFlexibleType].
    override fun isMutabilityFlexible(): Boolean {
        return kotlinType.toString().startsWith("(Mutable)")
    }

    // TODO: find a better way to reuse the logic in [DescriptorRendererImpl.renderFlexibleType].
    override fun isCovarianceFlexible(): Boolean {
        return kotlinType.toString().startsWith("Array<(out) ")
    }

    override fun replace(arguments: List<KSTypeArgument>): KSType {
        return KSTypeImpl.getCached(kotlinType.replaceTypeArguments(arguments), arguments)
    }

    override fun starProjection(): KSType {
        return KSTypeImpl.getCached(kotlinType.replaceArgumentsWithStarProjections())
    }

    private val meNullable: KSType by lazy { KSTypeImpl.getCached(kotlinType.makeNullable()) }
    override fun makeNullable(): KSType = meNullable

    private val meNotNullable: KSType by lazy { KSTypeImpl.getCached(kotlinType.makeNotNullable()) }
    override fun makeNotNullable(): KSType = meNotNullable

    override val isMarkedNullable: Boolean = kotlinType.isMarkedNullable

    override val isError: Boolean = false

    override fun equals(other: Any?): Boolean {
        if (other !is KSTypeImpl)
            return false
        return kotlinType.equals(other.kotlinType)
    }

    override fun hashCode(): Int = kotlinType.hashCode()

    override fun toString(): String = (kotlinType.getAbbreviation() ?: kotlinType).toString()
}

class IdKey<T>(private val k: T) {
    override fun equals(other: Any?): Boolean = if (other is IdKey<*>) k === other.k else false
    override fun hashCode(): Int = k.hashCode()
}

fun getKSTypeCached(
    kotlinType: KotlinType,
    ksTypeArguments: List<KSTypeArgument>? = null,
    annotations: List<KSAnnotation> = listOf()
): KSType {
    val err = kotlinType.constructor.declarationDescriptor is NotFoundClasses.MockClassDescriptor
    return if (err || kotlinType.isError) {
        KSErrorType
    } else {
        KSTypeImpl.getCached(kotlinType, ksTypeArguments, annotations)
    }
}
