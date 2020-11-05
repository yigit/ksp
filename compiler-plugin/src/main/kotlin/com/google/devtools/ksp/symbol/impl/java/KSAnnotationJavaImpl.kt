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


package com.google.devtools.ksp.symbol.impl.java

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.impl.ResolverImpl
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.symbol.impl.KSObjectCache
import com.google.devtools.ksp.symbol.impl.binary.getAbsentDefaultArguments
import com.google.devtools.ksp.symbol.impl.kotlin.KSErrorType
import com.google.devtools.ksp.symbol.impl.kotlin.KSNameImpl
import com.google.devtools.ksp.symbol.impl.kotlin.KSTypeImpl
import com.google.devtools.ksp.symbol.impl.toLocation
import com.intellij.psi.*

class KSAnnotationJavaImpl private constructor(val psi: PsiAnnotation) : KSAnnotation {
    companion object : KSObjectCache<PsiAnnotation, KSAnnotationJavaImpl>() {
        fun getCached(psi: PsiAnnotation) = cache.getOrPut(psi) { KSAnnotationJavaImpl(psi) }
    }

    override val origin = Origin.JAVA

    override val location: Location by lazy {
        psi.toLocation()
    }

    override val annotationType: KSTypeReference by lazy {
        KSTypeReferenceLiteJavaImpl.getCached(
            KSClassDeclarationJavaImpl.getCached(psi.nameReferenceElement!!.resolve() as PsiClass).asType(emptyList())
        )
    }

    override val arguments: List<KSValueArgument> by lazy {
        val annotationConstructor =
            ((annotationType.resolve() as KSTypeImpl).kotlinType.constructor.declarationDescriptor as? ClassDescriptor)
                ?.constructors?.single()
        val presentValueArguments = psi.parameterList.attributes
            .mapIndexed { index, it ->
                // use the name in the attribute if it is explicitly specified, otherwise, fall back to index.
                val name = it.name ?: annotationConstructor?.valueParameters?.getOrNull(index)?.name?.asString()
                val value = it.value
                val calculatedValue = if (value is PsiArrayInitializerMemberValue) {
                    // Kotlin compiler do not report nulls in values hence we are dropping there as well
                    // see ConstantExpressionEvaluator.resolveAnnotationValueArguments
                    value.initializers.map {
                        calcValue(it)
                    }.toTypedArray()
                } else {
                    it.value
                }
                KSValueArgumentJavaImpl.getCached(
                    name = name?.let(KSNameImpl::getCached),
                    value = calculatedValue
                )
            }
        val presentValueArgumentNames = presentValueArguments.map { it.name?.asString() ?: "" }
        val argumentsFromDefault = annotationConstructor?.let {
            it.getAbsentDefaultArguments(presentValueArgumentNames)
        } ?: emptyList()
        presentValueArguments.plus(argumentsFromDefault)
    }

    private fun calcValue(value: PsiAnnotationMemberValue?): Any? {
        val result = value?.let { JavaPsiFacade.getInstance(value.project).constantEvaluationHelper.computeConstantExpression(value) }
        return if (result is PsiType) {
            // return null instead of KSError to resemble the kotlin implementation
            ResolverImpl.instance.getClassDeclarationByName(result.canonicalText)?.asStarProjectedType() ?: KSErrorType
        } else {
            result
        }
    }

    override val shortName: KSName by lazy {
        KSNameImpl.getCached(psi.qualifiedName!!.split(".").last())
    }

    override val useSiteTarget: AnnotationUseSiteTarget? = null

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitAnnotation(this, data)
    }

    override fun toString(): String {
        return "@${shortName.asString()}"
    }
}