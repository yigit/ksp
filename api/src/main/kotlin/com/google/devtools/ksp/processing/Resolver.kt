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


package com.google.devtools.ksp.processing

import com.google.devtools.ksp.symbol.*

/**
 * [Resolver] provides [SymbolProcessor] with access to compiler details such as Symbols.
 */
interface Resolver {
    /**
     * Get all files in the module / compilation unit.
     *
     * @return files in the module.
     */
    fun getAllFiles(): List<KSFile>

    /**
     * Get all symbols with specified annotation.
     *
     * @param annotationName is the full qualified name of the annotation; using '.' as separator.
     * @return Elements annotated with the specified annotation.
     */
    fun getSymbolsWithAnnotation(annotationName: String): List<KSAnnotated>

    /**
     * Find a class in the compilation classpath for the given name.
     *
     * @param name fully qualified name of the class to be loaded; using '.' as separator.
     * @return a KSClassDeclaration, or null if not found.
     */
    fun getClassDeclarationByName(name: KSName): KSClassDeclaration?

    /**
     * Compose a type argument out of a type reference and a variance
     *
     * @param typeRef a type reference to be used in type argument
     * @param variance specifies a use-site variance
     * @return a type argument with use-site variance
     */
    fun getTypeArgument(typeRef: KSTypeReference, variance: Variance): KSTypeArgument

    /**
     * Get a [KSName] from a String.
     */
    fun getKSNameFromString(name: String): KSName

    /**
     * Create a [KSTypeReference] from a [KSType]
     */
    fun createKSTypeReferenceFromKSType(type: KSType): KSTypeReference

    /**
     * Provides built in types for convenience. For example, [KSBuiltins.anyType] is the KSType instance for class 'kotlin.Any'.
     */
    val builtIns: KSBuiltIns

    /**
     * map a declaration to jvm signature.
     */
    fun mapToJvmSignature(declaration: KSDeclaration): String

    /**
     * Returns the type of the [property] when it is viewed as member of the [containing] type.
     *
     * For instance, for the following input:
     * ```
     * class Base<T>(val x:T)
     * val foo: Base<Int>
     * val bar: Base<String>
     * ```
     * When `x` is viewed as member of `foo`, this method will return the [KSType] for `Int`
     * whereas when `x` is viewed as member of `bar`, this method will return the [KSType]
     * representing `String`.
     *
     * If the given type does not contain the [property], the [KSPropertyDeclaration.type] of the
     * [property] will be returned.
     *
     * @param property The property whose type will be returned
     * @param containing The type that contains [property]
     */
    fun asMemberOf(
        property: KSPropertyDeclaration,
        containing: KSType
    ): KSType

    /**
     * Returns the type of the [function] when it is viewed as member of the [containing] type.
     *
     * For instance, for the following input:
     * ```
     * interface Base<T> {
     *   fun f(t:T?):T
     * }
     * val foo: Base<Int>
     * val bar: Base<String>
     * ```
     * When `f()` is viewed as member of `foo`, this method will return a [KSFunctionType] where
     * the [KSFunctionType.returnType] is `Int` and the parameter `t` is of type `Int?`.
     * When `f()` is viewed as member of `bar`, this method will return a [KSFunctionType]
     * where the [KSFunctionType.returnType] is `String` and the parameter `t` is of type `String?`.
     *
     * If the function has type parameters, they'll not be resolved and can be read from
     * [KSFunctionType.typeParameters].
     *
     * If the given type does not contain the [function] or an error happens when substituting
     * types, a [KSFunctionType] that matches the description of the [function] will be returned.
     */
    fun asMemberOf(
        function: KSFunctionDeclaration,
        containing: KSType
    ): KSFunctionType
}