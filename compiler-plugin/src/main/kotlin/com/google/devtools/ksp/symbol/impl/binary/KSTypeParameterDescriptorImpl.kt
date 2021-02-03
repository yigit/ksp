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


package com.google.devtools.ksp.symbol.impl.binary

import com.google.devtools.ksp.ExceptionMessage
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSExpectActual
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSVisitor
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Variance
import com.google.devtools.ksp.symbol.impl.KSObjectCache
import com.google.devtools.ksp.symbol.impl.kotlin.KSExpectActualNoImpl
import com.google.devtools.ksp.symbol.impl.kotlin.KSNameImpl
import com.google.devtools.ksp.symbol.impl.toKSVariance
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.resolve.descriptorUtil.fqNameSafe

class KSTypeParameterDescriptorImpl private constructor(val descriptor: TypeParameterDescriptor) : KSTypeParameter,
    KSDeclarationDescriptorImpl(descriptor),
    KSExpectActual by KSExpectActualNoImpl() {
    companion object : KSObjectCache<TypeParameterDescriptor, KSTypeParameterDescriptorImpl>() {
        fun getCached(descriptor: TypeParameterDescriptor) =
            cache.getOrPut(descriptor) { KSTypeParameterDescriptorImpl(descriptor) }
    }

    override val bounds: List<KSTypeReference> by lazy {
        descriptor.upperBounds.map { KSTypeReferenceDescriptorImpl.getCached(it) }
    }

    override val typeParameters: List<KSTypeParameter> = emptyList()

    override val parentDeclaration: KSDeclaration? by lazy {
        when (val parent = descriptor.containingDeclaration) {
            is ClassDescriptor -> KSClassDeclarationDescriptorImpl.getCached(parent)
            is FunctionDescriptor -> KSFunctionDeclarationDescriptorImpl.getCached(parent)
            is PropertyDescriptor -> KSPropertyDeclarationDescriptorImpl.getCached(parent)
            else -> throw IllegalStateException(
                "Unexpected containing declaration for ${descriptor.fqNameSafe}, $ExceptionMessage"
            )
        }
    }

    override val modifiers: Set<Modifier> = emptySet()

    override val isReified: Boolean by lazy {
        descriptor.isReified
    }

    override val name: KSName by lazy {
        KSNameImpl.getCached(descriptor.name.asString())
    }

    override val variance: Variance = descriptor.variance.toKSVariance()

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitTypeParameter(this, data)
    }
}