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

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSPropertyAccessor
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.Location
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.NonExistLocation
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.symbol.impl.toFunctionKSModifiers
import com.google.devtools.ksp.symbol.impl.toKSModifiers
import org.jetbrains.kotlin.descriptors.PropertyAccessorDescriptor

abstract class KSPropertyAccessorDescriptorImpl(val descriptor: PropertyAccessorDescriptor) : KSPropertyAccessor {
    override val origin: Origin = Origin.CLASS

    override val receiver: KSPropertyDeclaration by lazy {
        KSPropertyDeclarationDescriptorImpl.getCached(descriptor.correspondingProperty)
    }

    override val location: Location = NonExistLocation

    override val annotations: List<KSAnnotation> by lazy {
        descriptor.annotations.map { KSAnnotationDescriptorImpl.getCached(it) }
    }

    override val modifiers: Set<Modifier> by lazy {
        val modifiers = mutableSetOf<Modifier>()
        modifiers.addAll(descriptor.toKSModifiers())
        modifiers.addAll(descriptor.toFunctionKSModifiers())
        modifiers
    }
}