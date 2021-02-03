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

import com.google.devtools.ksp.isLocal
import com.google.devtools.ksp.isPrivate
import com.google.devtools.ksp.processing.impl.ResolverImpl
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSExpectActual
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSPropertyGetter
import com.google.devtools.ksp.symbol.KSPropertySetter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSVisitor
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.impl.KSObjectCache
import com.google.devtools.ksp.symbol.impl.findClosestOverridee
import com.google.devtools.ksp.symbol.impl.synthetic.KSPropertyGetterSyntheticImpl
import com.google.devtools.ksp.symbol.impl.synthetic.KSPropertySetterSyntheticImpl
import com.google.devtools.ksp.symbol.impl.toKSPropertyDeclaration
import org.jetbrains.kotlin.descriptors.VariableDescriptorWithAccessors
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.psiUtil.isExtensionDeclaration

class KSPropertyDeclarationImpl private constructor(val ktProperty: KtProperty) : KSPropertyDeclaration,
    KSDeclarationImpl(ktProperty),
    KSExpectActual by KSExpectActualImpl(ktProperty) {
    companion object : KSObjectCache<KtProperty, KSPropertyDeclarationImpl>() {
        fun getCached(ktProperty: KtProperty) = cache.getOrPut(ktProperty) { KSPropertyDeclarationImpl(ktProperty) }
    }

    override val extensionReceiver: KSTypeReference? by lazy {
        if (ktProperty.isExtensionDeclaration()) {
            KSTypeReferenceImpl.getCached(ktProperty.receiverTypeReference!!)
        } else {
            null
        }
    }

    override val isMutable: Boolean by lazy {
        ktProperty.isVar
    }

    private fun shouldCreateSyntheticAccessor(): Boolean {
        return !this.isPrivate()
            && (
            !this.isLocal() && !this.modifiers.contains(Modifier.ABSTRACT)
                && (this.parentDeclaration as? KSClassDeclaration)?.classKind != ClassKind.INTERFACE
                || ((this.parentDeclaration as? KSClassDeclaration)?.classKind == ClassKind.INTERFACE && ktProperty.accessors.isNotEmpty())
            )
    }

    override val getter: KSPropertyGetter? by lazy {
        if (!shouldCreateSyntheticAccessor()) {
            null
        } else {
            val getter = ktProperty.accessors.filter { it.isGetter }.singleOrNull()
            if (getter != null) {
                KSPropertyGetterImpl.getCached(getter)
            } else {
                KSPropertyGetterSyntheticImpl.getCached(this)
            }
        }
    }

    override val setter: KSPropertySetter? by lazy {
        if (!shouldCreateSyntheticAccessor() || !ktProperty.isVar) {
            null
        } else {
            val setter = ktProperty.accessors.filter { it.isSetter }.singleOrNull()
            if (setter != null) {
                KSPropertySetterImpl.getCached(setter)
            } else {
                KSPropertySetterSyntheticImpl.getCached(this)
            }
        }
    }

    override val type: KSTypeReference by lazy {
        if (ktProperty.typeReference != null) {
            KSTypeReferenceImpl.getCached(ktProperty.typeReference!!)
        } else {
            KSTypeReferenceDeferredImpl.getCached {
                val desc = ResolverImpl.instance.resolveDeclaration(ktProperty) as? VariableDescriptorWithAccessors
                if (desc == null) {
                    KSErrorType
                } else {
                    getKSTypeCached(desc.type)
                }
            }
        }
    }

    override fun isDelegated(): Boolean = ktProperty.hasDelegate()

    override fun findOverridee(): KSPropertyDeclaration? {
        val propertyDescriptor = ResolverImpl.instance.resolvePropertyDeclaration(this)
        return propertyDescriptor?.findClosestOverridee()?.toKSPropertyDeclaration()
    }

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitPropertyDeclaration(this, data)
    }
}