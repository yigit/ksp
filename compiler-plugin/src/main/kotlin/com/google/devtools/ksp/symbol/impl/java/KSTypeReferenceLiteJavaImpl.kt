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

import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSReferenceElement
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSVisitor
import com.google.devtools.ksp.symbol.Location
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.NonExistLocation
import com.google.devtools.ksp.symbol.Origin
import com.google.devtools.ksp.symbol.impl.KSObjectCache

class KSTypeReferenceLiteJavaImpl private constructor(val type: KSType) : KSTypeReference {
    companion object : KSObjectCache<KSType, KSTypeReferenceLiteJavaImpl>() {
        fun getCached(type: KSType) = cache.getOrPut(type) { KSTypeReferenceLiteJavaImpl(type) }
    }

    override val origin = Origin.JAVA

    override val location: Location = NonExistLocation

    override val element: KSReferenceElement? = null

    override val annotations: List<KSAnnotation> = emptyList()

    override val modifiers: Set<Modifier> = emptySet()

    override fun resolve(): KSType {
        return type
    }

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitTypeReference(this, data)
    }

    override fun toString(): String {
        return type.toString()
    }
}