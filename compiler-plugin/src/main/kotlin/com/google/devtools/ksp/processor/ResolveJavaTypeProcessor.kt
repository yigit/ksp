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


package com.google.devtools.ksp.processor

import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.visitor.KSTopDownVisitor

class ResolveJavaTypeProcessor : AbstractTestProcessor() {
    val results = mutableListOf<String>()
    val visitor = ResolveJavaTypeVisitor()

    override fun toResult(): List<String> {
        return results
    }

    override fun process(resolver: Resolver) {
        val symbol = resolver.getClassDeclarationByName(resolver.getKSNameFromString("C"))
        assert(symbol?.origin == Origin.JAVA)
        symbol!!.accept(visitor, Unit)
    }

    inner class ResolveJavaTypeVisitor : KSTopDownVisitor<Unit, Unit>() {
        override fun defaultHandler(node: KSNode, data: Unit) {
        }

        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
            classDeclaration.declarations.map { it.accept(this, Unit) }
        }

        override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Unit) {
            if (function.simpleName.asString() == "wildcardParam") {
                function.parameters[0].type.accept(this, Unit)
            }
            function.returnType?.accept(this, Unit)
        }

        override fun visitTypeReference(typeReference: KSTypeReference, data: Unit) {
            if (typeReference.origin == Origin.JAVA) {
                results.add(typeReference.render())
            }
        }
    }

    fun KSTypeReference.render(): String {
        val sb = StringBuilder(this.resolve().declaration.qualifiedName?.asString() ?: "<ERROR>")
        if (this.resolve().arguments.isNotEmpty()) {
            sb.append("<${this.resolve().arguments.map {
                val typeRender = it.type?.render()
                when (it.variance) {
                    Variance.STAR -> "*"
                    Variance.INVARIANT -> typeRender
                    Variance.CONTRAVARIANT -> "in $typeRender"
                    Variance.COVARIANT -> "out $typeRender"
                }
            }.joinToString(", ")}>")
        }
        if (this.resolve().nullability != Nullability.NOT_NULL) {
            sb.append("?")
        }
        return sb.toString()
    }
}