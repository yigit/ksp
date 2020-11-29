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


package com.google.devtools.ksp.symbol.impl

import com.google.devtools.ksp.ExceptionMessage
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.*
import org.jetbrains.kotlin.descriptors.*
import com.google.devtools.ksp.processing.impl.ResolverImpl
import com.google.devtools.ksp.symbol.*
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.impl.binary.KSClassDeclarationDescriptorImpl
import com.google.devtools.ksp.symbol.impl.binary.KSFunctionDeclarationDescriptorImpl
import com.google.devtools.ksp.symbol.impl.binary.KSPropertyDeclarationDescriptorImpl
import com.google.devtools.ksp.symbol.impl.binary.KSTypeArgumentDescriptorImpl
import com.google.devtools.ksp.symbol.impl.java.KSPropertyDeclarationJavaImpl
import com.google.devtools.ksp.symbol.impl.java.KSTypeArgumentJavaImpl
import com.google.devtools.ksp.symbol.impl.kotlin.*
import com.intellij.psi.impl.source.PsiClassImpl
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.load.java.JavaVisibilities
import org.jetbrains.kotlin.load.java.descriptors.JavaClassConstructorDescriptor
import org.jetbrains.kotlin.psi.*
import org.jetbrains.kotlin.resolve.source.getPsi
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.StarProjectionImpl
import org.jetbrains.kotlin.types.TypeProjectionImpl
import org.jetbrains.kotlin.types.replace

val jvmModifierMap = mapOf(
    JvmModifier.PUBLIC to Modifier.PUBLIC,
    JvmModifier.PRIVATE to Modifier.PRIVATE,
    JvmModifier.ABSTRACT to Modifier.ABSTRACT,
    JvmModifier.FINAL to Modifier.FINAL,
    JvmModifier.PROTECTED to Modifier.PROTECTED,
    JvmModifier.STATIC to Modifier.JAVA_STATIC,
    JvmModifier.STRICTFP to Modifier.JAVA_STRICT,
    JvmModifier.NATIVE to Modifier.JAVA_NATIVE,
    JvmModifier.SYNCHRONIZED to Modifier.JAVA_SYNCHRONIZED,
    JvmModifier.TRANSIENT to Modifier.JAVA_TRANSIENT,
    JvmModifier.VOLATILE to Modifier.JAVA_VOLATILE
)

fun KtModifierListOwner.toKSModifiers(): Set<Modifier> {
    val modifiers = mutableSetOf<Modifier>()
    val modifierList = this.modifierList ?: return modifiers
    val modifierMap = mapOf(
        KtTokens.PUBLIC_KEYWORD to Modifier.PUBLIC,
        KtTokens.PRIVATE_KEYWORD to Modifier.PRIVATE,
        KtTokens.INTERNAL_KEYWORD to Modifier.INTERNAL,
        KtTokens.PROTECTED_KEYWORD to Modifier.PROTECTED,
        KtTokens.IN_KEYWORD to Modifier.IN,
        KtTokens.OUT_KEYWORD to Modifier.OUT,
        KtTokens.OVERRIDE_KEYWORD to Modifier.OVERRIDE,
        KtTokens.LATEINIT_KEYWORD to Modifier.LATEINIT,
        KtTokens.ENUM_KEYWORD to Modifier.ENUM,
        KtTokens.SEALED_KEYWORD to Modifier.SEALED,
        KtTokens.ANNOTATION_KEYWORD to Modifier.ANNOTATION,
        KtTokens.DATA_KEYWORD to Modifier.DATA,
        KtTokens.INNER_KEYWORD to Modifier.INNER,
        KtTokens.SUSPEND_KEYWORD to Modifier.SUSPEND,
        KtTokens.TAILREC_KEYWORD to Modifier.TAILREC,
        KtTokens.OPERATOR_KEYWORD to Modifier.OPERATOR,
        KtTokens.INFIX_KEYWORD to Modifier.INFIX,
        KtTokens.INLINE_KEYWORD to Modifier.INLINE,
        KtTokens.EXTERNAL_KEYWORD to Modifier.EXTERNAL,
        KtTokens.ABSTRACT_KEYWORD to Modifier.ABSTRACT,
        KtTokens.FINAL_KEYWORD to Modifier.FINAL,
        KtTokens.OPEN_KEYWORD to Modifier.OPEN,
        KtTokens.VARARG_KEYWORD to Modifier.VARARG,
        KtTokens.NOINLINE_KEYWORD to Modifier.NOINLINE,
        KtTokens.CROSSINLINE_KEYWORD to Modifier.CROSSINLINE,
        KtTokens.REIFIED_KEYWORD to Modifier.REIFIED,
        KtTokens.EXPECT_KEYWORD to Modifier.EXPECT,
        KtTokens.ACTUAL_KEYWORD to Modifier.ACTUAL
    )
    modifiers.addAll(
        modifierMap.entries
            .filter { modifierList.hasModifier(it.key) }
            .map { it.value }
    )
    return modifiers
}

fun PsiModifierListOwner.toKSModifiers(): Set<Modifier> {
    val modifiers = mutableSetOf<Modifier>()
    modifiers.addAll(
        jvmModifierMap.entries.filter { this.hasModifier(it.key) }
            .map { it.value }
            .toSet()
    )
    if (this.modifierList?.hasExplicitModifier("default") == true) {
        modifiers.add(Modifier.JAVA_DEFAULT)
    }
    return modifiers
}

fun MemberDescriptor.toKSModifiers(): Set<Modifier> {
    (findPsi() as? PsiModifierListOwner)?.let {
        return it.toKSModifiers()
    }
    val modifiers = mutableSetOf<Modifier>()
    if (this.isActual) {
        modifiers.add(Modifier.ACTUAL)
    }
    if (this.isExpect) {
        modifiers.add(Modifier.EXPECT)
    }
    if (this.isExternal) {
        modifiers.add(Modifier.EXTERNAL)
    }
    when (this.modality) {
        Modality.SEALED -> modifiers.add(Modifier.SEALED)
        Modality.FINAL -> modifiers.add(Modifier.FINAL)
        Modality.OPEN -> modifiers.add(Modifier.OPEN)
        Modality.ABSTRACT -> modifiers.add(Modifier.ABSTRACT)
    }
    when (this.visibility) {
        Visibilities.PUBLIC -> modifiers.add(Modifier.PUBLIC)
        Visibilities.PROTECTED, JavaVisibilities.PROTECTED_AND_PACKAGE -> modifiers.add(Modifier.PROTECTED)
        Visibilities.PRIVATE -> modifiers.add(Modifier.PRIVATE)
        Visibilities.INTERNAL -> modifiers.add(Modifier.INTERNAL)
        // Since there is no modifier for package-private, use No modifier to tell if a symbol from binary is package private.
        JavaVisibilities.PACKAGE_VISIBILITY, JavaVisibilities.PROTECTED_STATIC_VISIBILITY -> Unit
        else -> throw IllegalStateException("unhandled visibility: ${this.visibility}")
    }
    return modifiers
}

fun FunctionDescriptor.toFunctionKSModifiers(): Set<Modifier> {
    val modifiers = mutableSetOf<Modifier>()
    if (this.isSuspend) {
        modifiers.add(Modifier.SUSPEND)
    }
    if (this.isTailrec) {
        modifiers.add(Modifier.TAILREC)
    }
    if (this.isInline) {
        modifiers.add(Modifier.INLINE)
    }
    if (this.isInfix) {
        modifiers.add(Modifier.INFIX)
    }
    if (this.isOperator) {
        modifiers.add(Modifier.OPERATOR)
    }
    if (this.overriddenDescriptors.isNotEmpty()) {
        modifiers.add(Modifier.OVERRIDE)
    }
    return modifiers
}

fun PsiElement.findParentDeclaration(): KSDeclaration? {
    var parent = this.parent

    while (parent != null && parent !is KtDeclaration && parent !is KtFile && parent !is PsiClass && parent !is PsiMethod && parent !is PsiJavaFile) {
        parent = parent.parent
    }
    return when (parent) {
        is KtClassOrObject -> KSClassDeclarationImpl.getCached(parent)
        is KtFile -> null
        is KtFunction -> KSFunctionDeclarationImpl.getCached(parent)
        is PsiClass -> parent.toKSDescriptorClass()
        is PsiJavaFile -> null
        is PsiMethod -> parent.toKSFunctionDeclaration()
        else -> null
    }
}

fun PsiElement.toLocation(): Location {
    val file = this.containingFile
    val document = ResolverImpl.instance.psiDocumentManager.getDocument(file) ?: return NonExistLocation
    return FileLocation(file.virtualFile.path, document.getLineNumber(this.textOffset) + 1)
}

// TODO: handle local functions/classes correctly
fun List<KtElement>.getKSDeclarations() =
    this.mapNotNull {
        when (it) {
            is KtFunction -> KSFunctionDeclarationImpl.getCached(it)
            is KtProperty -> KSPropertyDeclarationImpl.getCached(it)
            is KtClassOrObject -> KSClassDeclarationImpl.getCached(it)
            is KtTypeAlias -> KSTypeAliasImpl.getCached(it)
            else -> null
        }
    }

fun KtClassOrObject.getClassType(): ClassKind {
    return when (this) {
        is KtObjectDeclaration -> ClassKind.OBJECT
        is KtEnumEntry -> ClassKind.ENUM_ENTRY
        is KtClass -> when {
            this.isEnum() -> ClassKind.ENUM_CLASS
            this.isInterface() -> ClassKind.INTERFACE
            this.isAnnotation() -> ClassKind.ANNOTATION_CLASS
            else -> ClassKind.CLASS
        }
        else -> throw IllegalStateException("Unexpected psi type ${this.javaClass}, $ExceptionMessage")
    }
}

fun org.jetbrains.kotlin.types.Variance.toKSVariance(): Variance {
    return when (this) {
        org.jetbrains.kotlin.types.Variance.IN_VARIANCE -> Variance.CONTRAVARIANT
        org.jetbrains.kotlin.types.Variance.OUT_VARIANCE -> Variance.COVARIANT
        org.jetbrains.kotlin.types.Variance.INVARIANT -> Variance.INVARIANT
        else -> throw IllegalStateException("Unexpected variance value $this, $ExceptionMessage")
    }
}

fun KSTypeReference.toKotlinType() = (resolve() as KSTypeImpl).kotlinType

internal fun KotlinType.replaceTypeArguments(newArguments: List<KSTypeArgument>): KotlinType =
    replace(newArguments.mapIndexed { index, ksTypeArgument ->
        val variance = when (ksTypeArgument.variance) {
            Variance.INVARIANT -> org.jetbrains.kotlin.types.Variance.INVARIANT
            Variance.COVARIANT -> org.jetbrains.kotlin.types.Variance.OUT_VARIANCE
            Variance.CONTRAVARIANT -> org.jetbrains.kotlin.types.Variance.IN_VARIANCE
            Variance.STAR -> return@mapIndexed StarProjectionImpl(constructor.parameters[index])
        }

        val type = when (ksTypeArgument) {
            is KSTypeArgumentKtImpl, is KSTypeArgumentJavaImpl, is KSTypeArgumentLiteImpl -> ksTypeArgument.type!!
            is KSTypeArgumentDescriptorImpl -> return@mapIndexed ksTypeArgument.descriptor
            else -> throw IllegalStateException("Unexpected psi for type argument: ${ksTypeArgument.javaClass}, $ExceptionMessage")
        }.toKotlinType()

        TypeProjectionImpl(variance, type)
    })

internal fun FunctionDescriptor.toKSFunctionDeclaration(): KSFunctionDeclaration {
    if (this.kind != CallableMemberDescriptor.Kind.DECLARATION) return KSFunctionDeclarationDescriptorImpl.getCached(this)
    val psi = this.findPsi() ?: return KSFunctionDeclarationDescriptorImpl.getCached(this)
    // Java default constructor has a kind DECLARATION of while still being synthetic.
    if (psi is PsiClassImpl && this is JavaClassConstructorDescriptor) {
        return KSFunctionDeclarationDescriptorImpl.getCached(this)
    }
    return when (psi) {
        is KtFunction -> KSFunctionDeclarationImpl.getCached(psi)
        is PsiMethod -> KSFunctionDeclarationDescriptorImpl.getCached(this)
        else -> throw IllegalStateException("unexpected psi: ${psi.javaClass}")
    }
}

internal fun PropertyDescriptor.toKSPropertyDeclaration(): KSPropertyDeclaration {
    if (this.kind != CallableMemberDescriptor.Kind.DECLARATION) return KSPropertyDeclarationDescriptorImpl.getCached(this)
    val psi = this.findPsi() ?: return KSPropertyDeclarationDescriptorImpl.getCached(this)
    return when (psi) {
        is KtProperty -> KSPropertyDeclarationImpl.getCached(psi)
        is KtParameter -> KSPropertyDeclarationParameterImpl.getCached(psi)
        is PsiField -> KSPropertyDeclarationJavaImpl.getCached(psi)
        else -> throw IllegalStateException("unexpected psi: ${psi.javaClass}")
    }
}

internal fun DeclarationDescriptor.findPsi(): PsiElement? {
    // For synthetic members.
    if ((this is CallableMemberDescriptor) && this.kind != CallableMemberDescriptor.Kind.DECLARATION) return null
    return (this as? DeclarationDescriptorWithSource)?.source?.getPsi()
}

/**
 * @see KSFunctionDeclaration.findOverridee for docs.
 */
internal fun FunctionDescriptor.findClosestOverridee(): FunctionDescriptor? {
    // When there is an intermediate class between the overridden and our function, we might receive
    // a FAKE_OVERRIDE function which is not desired as we are trying to find the actual
    // declared method.

    // we also want to return the closes function declaration. That is either the closest
    // class / interface method OR in case of equal distance (e.g. diamon dinheritance), pick the
    // one declared first in the code.

    val queue = ArrayDeque<FunctionDescriptor>()
    queue.add(this)

    while (queue.isNotEmpty()) {
        val current = queue.removeFirst()
        val overriddenDescriptors = current.original.overriddenDescriptors
        overriddenDescriptors.firstOrNull {
            it.kind != CallableMemberDescriptor.Kind.FAKE_OVERRIDE
        }?.let {
            return it.original
        }
        // if all methods are fake, add them to the queue
        queue.addAll(overriddenDescriptors)
    }
    return null
}


internal fun PsiClass.toKSDescriptorClass() : KSClassDeclarationDescriptorImpl {
    val descriptor = ResolverImpl.instance.resolveJavaDeclaration(this)
    return when(descriptor) {
        is ClassDescriptor -> KSClassDeclarationDescriptorImpl.getCached(descriptor)
        else -> error("cannot resolve $this / $descriptor")
    }
}

internal fun PsiMethod.toKSFunctionDeclaration() : KSFunctionDeclaration {
    val descriptor = ResolverImpl.instance.resolveJavaDeclaration(this)
    return when(descriptor) {
        is FunctionDescriptor ->  descriptor.toKSFunctionDeclaration()
        else -> error("cannot resolve $this / $descriptor")
    }
}

internal fun AnnotationDescriptor.findOrigin() : Origin {
    return this.source.getPsi()?.findOrigin() ?: Origin.CLASS
}
internal fun DeclarationDescriptor.findOrigin() : Origin {
    return original.findPsi()?.findOrigin() ?: Origin.CLASS
}

internal fun PsiElement.findOrigin(): Origin {
    val fileType = containingFile?.fileType
    return when(fileType?.defaultExtension) {
        "java" -> Origin.JAVA
        "kt" -> Origin.KOTLIN
        null -> Origin.CLASS
        else -> error("cannot figure out file type: $fileType $ExceptionMessage") // Origin.CLASS
    }
}