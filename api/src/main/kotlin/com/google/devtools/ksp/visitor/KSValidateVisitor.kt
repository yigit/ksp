/*
 * Copyright 2021 Google LLC
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
package com.google.devtools.ksp.visitor

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSDeclarationContainer
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSValueParameter

class KSValidateVisitor(private val predicate: (KSNode?, KSNode) -> Boolean) : KSDefaultVisitor<KSNode?, Boolean>() {
    private fun validateType(type: KSType): Boolean {
        return !type.isError && !type.arguments.any { it.type?.accept(this, null) == false }
    }

    override fun defaultHandler(node: KSNode, data: KSNode?): Boolean {
        return true
    }

    override fun visitDeclaration(declaration: KSDeclaration, data: KSNode?): Boolean {
        if (!predicate(data, declaration)) {
            return true
        }
        if (declaration.typeParameters.any { !it.accept(this, declaration) }) {
            return false
        }
        return this.visitAnnotated(declaration, data)
    }

    override fun visitDeclarationContainer(declarationContainer: KSDeclarationContainer, data: KSNode?): Boolean {
        return !predicate(data, declarationContainer) || declarationContainer.declarations.all {
            it.accept(
                this,
                declarationContainer
            )
        }
    }

    override fun visitTypeParameter(typeParameter: KSTypeParameter, data: KSNode?): Boolean {
        return !predicate(data, typeParameter) || typeParameter.bounds.all { it.accept(this, typeParameter) }
    }

    override fun visitAnnotated(annotated: KSAnnotated, data: KSNode?): Boolean {
        return !predicate(data, annotated) || annotated.annotations.all { it.accept(this, annotated) }
    }

    override fun visitAnnotation(annotation: KSAnnotation, data: KSNode?): Boolean {
        return !predicate(data, annotation) || annotation.annotationType.accept(this, annotation)
    }

    override fun visitTypeReference(typeReference: KSTypeReference, data: KSNode?): Boolean {
        return validateType(typeReference.resolve())
    }

    override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: KSNode?): Boolean {
        if (classDeclaration.asStarProjectedType().isError) {
            return false
        }
        if (!classDeclaration.superTypes.all { it.accept(this, classDeclaration) }) {
            return false
        }
        if (!this.visitDeclaration(classDeclaration, data)) {
            return false
        }
        if (!this.visitDeclarationContainer(classDeclaration, data)) {
            return false
        }
        return true
    }

    override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: KSNode?): Boolean {
        if (function.returnType != null && !(
            predicate(function, function.returnType!!) && function.returnType!!.accept(
                    this,
                    data
                )
            )
        ) {
            return false
        }
        if (!function.parameters.all { it.accept(this, function) }) {
            return false
        }
        if (!this.visitDeclaration(function, data)) {
            return false
        }
        if (!this.visitDeclarationContainer(function, data)) {
            return false
        }
        return true
    }

    override fun visitPropertyDeclaration(property: KSPropertyDeclaration, data: KSNode?): Boolean {
        if (predicate(property, property.type) && property.type.resolve().isError) {
            return false
        }
        if (!this.visitDeclaration(property, data)) {
            return false
        }
        return true
    }

    override fun visitValueParameter(valueParameter: KSValueParameter, data: KSNode?): Boolean {
        return valueParameter.type.accept(this, valueParameter)
    }
}
