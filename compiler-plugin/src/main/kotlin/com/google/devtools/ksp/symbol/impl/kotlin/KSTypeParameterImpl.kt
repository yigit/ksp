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

import com.google.devtools.ksp.ExceptionMessage
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSExpectActual
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSVisitor
import com.google.devtools.ksp.symbol.Variance
import com.google.devtools.ksp.symbol.impl.KSObjectCache
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeParameterListOwner

class KSTypeParameterImpl private constructor(
    val ktTypeParameter: KtTypeParameter,
    val owner: KtTypeParameterListOwner
) : KSTypeParameter,
    KSDeclarationImpl(ktTypeParameter),
    KSExpectActual by KSExpectActualNoImpl() {
    companion object : KSObjectCache<Pair<KtTypeParameter, KtTypeParameterListOwner>, KSTypeParameterImpl>() {
        fun getCached(ktTypeParameter: KtTypeParameter, owner: KtTypeParameterListOwner) =
            cache.getOrPut(Pair(ktTypeParameter, owner)) { KSTypeParameterImpl(ktTypeParameter, owner) }
    }

    override val name: KSName by lazy {
        KSNameImpl.getCached(ktTypeParameter.name!!)
    }

    override val isReified: Boolean by lazy {
        ktTypeParameter.modifierList?.hasModifier(KtTokens.REIFIED_KEYWORD) ?: false
    }

    override val variance: Variance by lazy {
        when {
            ktTypeParameter.modifierList == null -> Variance.INVARIANT
            ktTypeParameter.modifierList!!.hasModifier(KtTokens.OUT_KEYWORD) -> Variance.COVARIANT
            ktTypeParameter.modifierList!!.hasModifier(KtTokens.IN_KEYWORD) -> Variance.CONTRAVARIANT
            else -> Variance.INVARIANT
        }
    }

    override val bounds: List<KSTypeReference> by lazy {
        val list = mutableListOf(ktTypeParameter.extendsBound)
        list.addAll(
            owner.typeConstraints
                .filter { it.subjectTypeParameterName!!.getReferencedName() == ktTypeParameter.nameAsSafeName.asString() }
                .map { it.boundTypeReference }
        )
        list.filterNotNull().map { KSTypeReferenceImpl.getCached(it) }
    }
    override val simpleName: KSName by lazy {
        KSNameImpl.getCached(ktTypeParameter.name ?: "_")
    }

    override val typeParameters: List<KSTypeParameter> = emptyList()

    override val parentDeclaration: KSDeclaration? by lazy {
        when (owner) {
            is KtClassOrObject -> KSClassDeclarationImpl.getCached(owner)
            is KtFunction -> KSFunctionDeclarationImpl.getCached(owner)
            is KtProperty -> KSPropertyDeclarationImpl.getCached(owner)
            else -> throw IllegalStateException("Unexpected containing declaration type ${owner.javaClass}, $ExceptionMessage")
        }
    }

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitTypeParameter(this, data)
    }
}