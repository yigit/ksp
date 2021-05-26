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

import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.*

open class TypeParameterReferenceProcessor: AbstractTestProcessor() {
    val results = mutableListOf<String>()

    override fun process(resolver: Resolver): List<KSAnnotated> {
        listOf(
            "lib.SelfReferencing",
            "lib.SelfReferencingJava",
            "SelfReferencing",
            "SelfReferencingJava"
        ).forEach { className ->
            val selfReferencing = resolver.getClassDeclarationByName(className) ?: error("where is $className")
            results.add("$className:")
            results.add(selfReferencing.asStarProjectedType().dumpToString(10))
        }

        return emptyList()
    }

    private fun KSType.dumpToString(depth: Int): String {
        return dump(depth).toString()
    }

    private fun KSType.dump(depth: Int): TypeNameNode? {
        if (depth < 0) return null
        if (isError) {
            TypeNameNode(
                text = "error"
            )
        }
        return TypeNameNode(
            text = toString(),
            typeArgs = this.arguments.mapIndexed { index, typeArg ->
                typeArg.dump(declaration.typeParameters[index], depth - 1)
            }.filterNotNull()
        )
    }

    private fun KSTypeArgument.dump(
        param: KSTypeParameter,
        depth: Int
    ) : TypeNameNode? {
        if (depth < 0) return null
        return if (variance == Variance.STAR) {
            param.dump(depth)
        } else {
            type!!.resolve().dump(depth)
        }
    }
    private fun KSTypeReference.dump(
        depth: Int
    ) : TypeNameNode? {
        return resolve().dump(depth)
    }

    private fun KSTypeParameter.dump(depth: Int): TypeNameNode? {
        if (depth < 0) return null
        return TypeNameNode(
            text = this.name.asString(),
            bounds = this.bounds.map {
                it.dump(depth - 1)
            }.filterNotNull().toList()
        )
    }

    private data class TypeNameNode(
        val text: String,
        val bounds: List<TypeNameNode> = emptyList(),
        val typeArgs: List<TypeNameNode> = emptyList()
    ) {
        override fun toString(): String {
            return buildString {
                appendLine(text)
                bounds.forEach {
                    appendLine(it.toString().prependIndent("> "))
                }
                typeArgs.forEach {
                    appendLine(it.toString().prependIndent("| "))
                }
            }.trim()
        }
    }

    override fun toResult(): List<String> {
        return results
    }

}
