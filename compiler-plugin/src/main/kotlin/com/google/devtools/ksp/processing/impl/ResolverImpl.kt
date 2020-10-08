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


package com.google.devtools.ksp.processing.impl

import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.processing.KSBuiltIns
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSClassifierReference
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFile
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSFunctionType
import com.google.devtools.ksp.symbol.KSName
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.KSVisitorVoid
import com.google.devtools.ksp.symbol.Variance
import com.google.devtools.ksp.symbol.impl.binary.KSClassDeclarationDescriptorImpl
import com.google.devtools.ksp.symbol.impl.binary.KSFunctionDeclarationDescriptorImpl
import com.google.devtools.ksp.symbol.impl.binary.KSPropertyDeclarationDescriptorImpl
import com.google.devtools.ksp.symbol.impl.binary.KSTypeParameterDescriptorImpl
import com.google.devtools.ksp.symbol.impl.binary.KSTypeReferenceDescriptorImpl
import com.google.devtools.ksp.symbol.impl.findPsi
import com.google.devtools.ksp.symbol.impl.java.KSClassDeclarationJavaImpl
import com.google.devtools.ksp.symbol.impl.java.KSFileJavaImpl
import com.google.devtools.ksp.symbol.impl.java.KSFunctionDeclarationJavaImpl
import com.google.devtools.ksp.symbol.impl.java.KSPropertyDeclarationJavaImpl
import com.google.devtools.ksp.symbol.impl.java.KSTypeReferenceJavaImpl
import com.google.devtools.ksp.symbol.impl.kotlin.KSClassDeclarationImpl
import com.google.devtools.ksp.symbol.impl.kotlin.KSFileImpl
import com.google.devtools.ksp.symbol.impl.kotlin.KSFunctionDeclarationImpl
import com.google.devtools.ksp.symbol.impl.kotlin.KSFunctionTypeDeclarationImpl
import com.google.devtools.ksp.symbol.impl.kotlin.KSFunctionTypeFromKsType
import com.google.devtools.ksp.symbol.impl.kotlin.KSFunctionTypeImpl
import com.google.devtools.ksp.symbol.impl.kotlin.KSNameImpl
import com.google.devtools.ksp.symbol.impl.kotlin.KSPropertyDeclarationImpl
import com.google.devtools.ksp.symbol.impl.kotlin.KSPropertyDeclarationParameterImpl
import com.google.devtools.ksp.symbol.impl.kotlin.KSTypeAliasImpl
import com.google.devtools.ksp.symbol.impl.kotlin.KSTypeArgumentLiteImpl
import com.google.devtools.ksp.symbol.impl.kotlin.KSTypeImpl
import com.google.devtools.ksp.symbol.impl.kotlin.KSTypeReferenceImpl
import com.google.devtools.ksp.symbol.impl.kotlin.getKSTypeCached
import com.google.devtools.ksp.symbol.impl.synthetic.KSConstructorSyntheticImpl
import com.google.devtools.ksp.symbol.impl.synthetic.KSTypeReferenceSyntheticImpl
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiType
import com.intellij.psi.PsiTypeParameter
import com.intellij.psi.impl.source.PsiClassReferenceType
import org.jetbrains.kotlin.builtins.KotlinBuiltIns
import org.jetbrains.kotlin.codegen.ClassBuilderMode
import org.jetbrains.kotlin.codegen.state.KotlinTypeMapper
import org.jetbrains.kotlin.container.ComponentProvider
import org.jetbrains.kotlin.container.get
import org.jetbrains.kotlin.descriptors.CallableMemberDescriptor
import org.jetbrains.kotlin.descriptors.ClassDescriptor
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor
import org.jetbrains.kotlin.descriptors.FunctionDescriptor
import org.jetbrains.kotlin.descriptors.MemberDescriptor
import org.jetbrains.kotlin.descriptors.ModuleDescriptor
import org.jetbrains.kotlin.descriptors.PropertyDescriptor
import org.jetbrains.kotlin.descriptors.TypeAliasDescriptor
import org.jetbrains.kotlin.descriptors.TypeParameterDescriptor
import org.jetbrains.kotlin.descriptors.ValueDescriptor
import org.jetbrains.kotlin.descriptors.annotations.AnnotationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.annotations.BuiltInAnnotationDescriptor
import org.jetbrains.kotlin.descriptors.annotations.CompositeAnnotations
import org.jetbrains.kotlin.descriptors.resolveClassByFqName
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.load.java.components.TypeUsage
import org.jetbrains.kotlin.load.java.lazy.JavaResolverComponents
import org.jetbrains.kotlin.load.java.lazy.LazyJavaResolverContext
import org.jetbrains.kotlin.load.java.lazy.ModuleClassResolver
import org.jetbrains.kotlin.load.java.lazy.TypeParameterResolver
import org.jetbrains.kotlin.load.java.lazy.descriptors.LazyJavaTypeParameterDescriptor
import org.jetbrains.kotlin.load.java.lazy.types.JavaTypeResolver
import org.jetbrains.kotlin.load.java.lazy.types.toAttributes
import org.jetbrains.kotlin.load.java.structure.impl.JavaClassImpl
import org.jetbrains.kotlin.load.java.structure.impl.JavaFieldImpl
import org.jetbrains.kotlin.load.java.structure.impl.JavaMethodImpl
import org.jetbrains.kotlin.load.java.structure.impl.JavaTypeImpl
import org.jetbrains.kotlin.load.java.structure.impl.JavaTypeParameterImpl
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtAnnotationEntry
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtFunction
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiUtil
import org.jetbrains.kotlin.psi.KtStubbedPsiUtil
import org.jetbrains.kotlin.psi.KtTypeAlias
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.resolve.AnnotationResolver
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.BindingTrace
import org.jetbrains.kotlin.resolve.BodyResolver
import org.jetbrains.kotlin.resolve.LazyTopDownAnalyzer
import org.jetbrains.kotlin.resolve.calls.inference.components.NewTypeSubstitutor
import org.jetbrains.kotlin.resolve.calls.inference.components.composeWith
import org.jetbrains.kotlin.resolve.calls.inference.returnTypeOrNothing
import org.jetbrains.kotlin.resolve.calls.inference.substitute
import org.jetbrains.kotlin.resolve.calls.smartcasts.DataFlowInfo
import org.jetbrains.kotlin.resolve.constants.ConstantValue
import org.jetbrains.kotlin.resolve.constants.evaluate.ConstantExpressionEvaluator
import org.jetbrains.kotlin.resolve.descriptorUtil.getAllSuperClassifiers
import org.jetbrains.kotlin.resolve.descriptorUtil.isExtension
import org.jetbrains.kotlin.resolve.jvm.multiplatform.JavaActualAnnotationArgumentExtractor
import org.jetbrains.kotlin.resolve.lazy.DeclarationScopeProvider
import org.jetbrains.kotlin.resolve.lazy.ForceResolveUtil
import org.jetbrains.kotlin.resolve.lazy.ResolveSession
import org.jetbrains.kotlin.resolve.multiplatform.findActuals
import org.jetbrains.kotlin.resolve.multiplatform.findExpects
import org.jetbrains.kotlin.resolve.scopes.LexicalScope
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.types.IndexedParametersSubstitution
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.KotlinTypeFactory
import org.jetbrains.kotlin.types.KotlinTypeFactory.simpleTypeWithNonTrivialMemberScope
import org.jetbrains.kotlin.types.SimpleType
import org.jetbrains.kotlin.types.SubstitutionUtils
import org.jetbrains.kotlin.types.TypeProjection
import org.jetbrains.kotlin.types.TypeProjectionImpl
import org.jetbrains.kotlin.types.TypeSubstitutor
import org.jetbrains.kotlin.types.replace
import org.jetbrains.kotlin.types.typeUtil.asTypeProjection
import org.jetbrains.kotlin.types.typeUtil.replaceArgumentsWithStarProjections
import org.jetbrains.kotlin.types.typeUtil.substitute
import org.jetbrains.kotlin.types.withAbbreviation
import org.jetbrains.kotlin.util.containingNonLocalDeclaration

class ResolverImpl(
    val module: ModuleDescriptor,
    files: Collection<KtFile>,
    javaFiles: Collection<PsiJavaFile>,
    val bindingTrace: BindingTrace,
    val project: Project,
    componentProvider: ComponentProvider
) : Resolver {
    val ksFiles: List<KSFile>
    val psiDocumentManager = PsiDocumentManager.getInstance(project)
    val javaActualAnnotationArgumentExtractor = JavaActualAnnotationArgumentExtractor()
    private val nameToKSMap: MutableMap<KSName, KSClassDeclaration>

    private val typeMapper = KotlinTypeMapper(
        BindingContext.EMPTY, ClassBuilderMode.LIGHT_CLASSES,
        JvmProtoBufUtil.DEFAULT_MODULE_NAME,
        KotlinTypeMapper.LANGUAGE_VERSION_SETTINGS_DEFAULT// TODO use proper LanguageVersionSettings
    )

    companion object {
        lateinit var resolveSession: ResolveSession
        lateinit var bodyResolver: BodyResolver
        lateinit var constantExpressionEvaluator: ConstantExpressionEvaluator
        lateinit var declarationScopeProvider: DeclarationScopeProvider
        lateinit var topDownAnalyzer: LazyTopDownAnalyzer
        lateinit var instance: ResolverImpl
        lateinit var annotationResolver: AnnotationResolver
        lateinit var moduleClassResolver: ModuleClassResolver
        lateinit var javaTypeResolver: JavaTypeResolver
        lateinit var lazyJavaResolverContext: LazyJavaResolverContext
    }

    init {
        resolveSession = componentProvider.get()
        bodyResolver = componentProvider.get()
        declarationScopeProvider = componentProvider.get()
        topDownAnalyzer = componentProvider.get()
        constantExpressionEvaluator = componentProvider.get()
        annotationResolver = resolveSession.annotationResolver

        ksFiles = files.map { KSFileImpl.getCached(it) } + javaFiles.map { KSFileJavaImpl.getCached(it) }
        val javaResolverComponents = componentProvider.get<JavaResolverComponents>()
        lazyJavaResolverContext = LazyJavaResolverContext(javaResolverComponents, TypeParameterResolver.EMPTY) { null }
        javaTypeResolver = lazyJavaResolverContext.typeResolver
        moduleClassResolver = lazyJavaResolverContext.components.moduleClassResolver
        instance = this

        nameToKSMap = mutableMapOf()

        val visitor = object : KSVisitorVoid() {
            override fun visitFile(file: KSFile, data: Unit) {
                file.declarations.map { it.accept(this, data) }
            }

            override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: Unit) {
                val qualifiedName = classDeclaration.qualifiedName
                if (qualifiedName != null) {
                    nameToKSMap[qualifiedName] = classDeclaration
                }
                classDeclaration.declarations.map { it.accept(this, data) }
            }
        }
        ksFiles.map { it.accept(visitor, Unit) }


    }

    override fun getAllFiles(): List<KSFile> {
        return ksFiles
    }

    override fun getClassDeclarationByName(name: KSName): KSClassDeclaration? {
        nameToKSMap[name]?.let { return it }

        return (module.resolveClassByFqName(FqName(name.asString()), NoLookupLocation.FROM_BUILTINS)
            ?: module.resolveClassByFqName(FqName(name.asString()), NoLookupLocation.FROM_DESERIALIZATION))
            ?.let {
                val psi = it.findPsi()
                if (psi != null) {
                    when (psi) {
                        is KtClassOrObject -> KSClassDeclarationImpl.getCached(psi)
                        is PsiClass -> KSClassDeclarationJavaImpl.getCached(psi)
                        else -> throw IllegalStateException("unexpected psi: ${psi.javaClass}")
                    }
                } else {
                    KSClassDeclarationDescriptorImpl.getCached(it)
                }
            }
    }

    override fun getSymbolsWithAnnotation(annotationName: String): List<KSAnnotated> {
        val ksName = KSNameImpl.getCached(annotationName)

        val visitor = object : BaseVisitor() {
            val symbols = mutableSetOf<KSAnnotated>()
            override fun visitAnnotated(annotated: KSAnnotated, data: Unit) {
                if (annotated.annotations.any {
                        val annotationType = it.annotationType
                        (annotationType.element as? KSClassifierReference)?.referencedName()
                            .let { it == null || it == ksName.getShortName() }
                                && annotationType.resolve().declaration.qualifiedName == ksName
                    }) {
                    symbols.add(annotated)
                }
            }

            override fun visitClassDeclaration(type: KSClassDeclaration, data: Unit) {
                visitAnnotated(type, data)
                super.visitClassDeclaration(type, data)
            }

            override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Unit) {
                visitAnnotated(function, data)
                super.visitFunctionDeclaration(function, data)
            }

            override fun visitPropertyDeclaration(property: KSPropertyDeclaration, data: Unit) {
                visitAnnotated(property, data)
                super.visitPropertyDeclaration(property, data)
            }

            override fun visitTypeParameter(typeParameter: KSTypeParameter, data: Unit) {
                visitAnnotated(typeParameter, data)
                super.visitTypeParameter(typeParameter, data)
            }

            override fun visitTypeReference(typeReference: KSTypeReference, data: Unit) {
                visitAnnotated(typeReference, data)
                super.visitTypeReference(typeReference, data)
            }

        }

        for (file in ksFiles) {
            file.accept(visitor, Unit)
        }
        return visitor.symbols.toList()
    }

    override fun getKSNameFromString(name: String): KSName {
        return KSNameImpl.getCached(name)
    }

    override fun createKSTypeReferenceFromKSType(type: KSType): KSTypeReference {
        return KSTypeReferenceSyntheticImpl.getCached(type)
    }

    override fun mapToJvmSignature(declaration: KSDeclaration): String {
        return when (declaration) {
            is KSClassDeclaration -> resolveClassDeclaration(declaration)?.let { typeMapper.mapType(it).descriptor }
                ?: ""
            is KSFunctionDeclaration -> resolveFunctionDeclaration(declaration)?.let { typeMapper.mapAsmMethod(it).descriptor }
                ?: ""
            is KSPropertyDeclaration -> resolvePropertyDeclaration(declaration)?.let {
                typeMapper.mapFieldSignature(it.type, it) ?: typeMapper.mapType(it).descriptor
            } ?: ""
            else -> ""
        }
    }

    fun evaluateConstant(expression: KtExpression?, expectedType: KotlinType): ConstantValue<*>? {
        return expression?.let { constantExpressionEvaluator.evaluateToConstantValue(it, bindingTrace, expectedType) }
    }

    fun resolveDeclaration(declaration: KtDeclaration): DeclarationDescriptor? {
        return if (KtPsiUtil.isLocal(declaration)) {
            resolveDeclarationForLocal(declaration)
            bindingTrace.bindingContext.get(BindingContext.DECLARATION_TO_DESCRIPTOR, declaration)
        } else {
            resolveSession.resolveToDescriptor(declaration)
        }
    }

    // TODO: Resolve Java variables is not supported by this function. Not needed currently.
    fun resolveJavaDeclaration(psi: PsiElement): DeclarationDescriptor? {
        return when (psi) {
            is PsiClass -> moduleClassResolver.resolveClass(JavaClassImpl(psi))
            is PsiMethod -> moduleClassResolver.resolveClass(JavaMethodImpl(psi).containingClass)
                ?.unsubstitutedMemberScope!!.getDescriptorsFiltered().single { it.findPsi() == psi } as FunctionDescriptor
            is PsiField -> moduleClassResolver.resolveClass(JavaFieldImpl(psi).containingClass)
                ?.unsubstitutedMemberScope!!.getDescriptorsFiltered().single { it.findPsi() == psi } as CallableMemberDescriptor
            else -> throw IllegalStateException("unhandled psi element kind: ${psi.javaClass}")
        }
    }

    fun resolveClassDeclaration(classDeclaration: KSClassDeclaration): ClassDescriptor? {
        return when (classDeclaration) {
            is KSClassDeclarationImpl -> resolveDeclaration(classDeclaration.ktClassOrObject)
            is KSClassDeclarationDescriptorImpl -> classDeclaration.descriptor
            is KSClassDeclarationJavaImpl -> resolveJavaDeclaration(classDeclaration.psi)
            else -> throw IllegalStateException("unexpected class: ${classDeclaration.javaClass}")
        } as ClassDescriptor?
    }

    fun resolveFunctionDeclaration(function: KSFunctionDeclaration): FunctionDescriptor? {
        return when (function) {
            is KSFunctionDeclarationImpl -> resolveDeclaration(function.ktFunction)
            is KSFunctionDeclarationDescriptorImpl -> function.descriptor
            is KSFunctionDeclarationJavaImpl -> resolveJavaDeclaration(function.psi)
            is KSConstructorSyntheticImpl -> resolveClassDeclaration(function.ksClassDeclaration)?.unsubstitutedPrimaryConstructor
            else -> throw IllegalStateException("unexpected class: ${function.javaClass}")
        } as FunctionDescriptor?
    }

    fun resolvePropertyDeclaration(property: KSPropertyDeclaration): PropertyDescriptor? {
        return when (property) {
            is KSPropertyDeclarationImpl -> resolveDeclaration(property.ktProperty)
            is KSPropertyDeclarationParameterImpl -> resolveDeclaration(property.ktParameter)
            is KSPropertyDeclarationDescriptorImpl -> property.descriptor
            is KSPropertyDeclarationJavaImpl -> resolveJavaDeclaration(property.psi)
            else -> throw IllegalStateException("unexpected class: ${property.javaClass}")
        } as PropertyDescriptor?
    }

    fun resolveJavaType(psi: PsiType): KotlinType {
        val javaType = JavaTypeImpl.create(psi)
        return javaTypeResolver.transformJavaType(javaType, TypeUsage.COMMON.toAttributes())
    }

    fun KotlinType.expandNonRecursively(): KotlinType =
        (constructor.declarationDescriptor as? TypeAliasDescriptor)?.expandedType?.withAbbreviation(this as SimpleType) ?: this

    fun TypeProjection.expand(): TypeProjection {
        val expandedType = type.expand()
        return if (expandedType == type) this else substitute { expandedType }
    }

    // TODO: Is this the most efficient way?
    fun KotlinType.expand(): KotlinType =
        replace(arguments.map { it.expand() }).expandNonRecursively()

    fun KtTypeReference.lookup(): KotlinType? =
        bindingTrace.get(BindingContext.ABBREVIATED_TYPE, this)?.expand() ?: bindingTrace.get(BindingContext.TYPE, this)

    fun resolveUserType(type: KSTypeReference): KSType {
        when (type) {
            is KSTypeReferenceImpl -> {
                val typeReference = type.ktTypeReference
                typeReference.lookup()?.let {
                    return getKSTypeCached(it, type.element.typeArguments, type.annotations)
                }
                KtStubbedPsiUtil.getContainingDeclaration(typeReference)?.let { containingDeclaration ->
                    resolveDeclaration(containingDeclaration)?.let {
                        // TODO: only resolve relevant branch.
                        ForceResolveUtil.forceResolveAllContents(it)
                    }
                    // TODO: Fix resolution look up to avoid fallback to file scope.
                    typeReference.lookup()?.let {
                        return getKSTypeCached(it, type.element.typeArguments, type.annotations)
                    }
                }
                val scope = resolveSession.fileScopeProvider.getFileResolutionScope(typeReference.containingKtFile)
                return resolveSession.typeResolver.resolveType(scope, typeReference, bindingTrace, false).let {
                    getKSTypeCached(it, type.element.typeArguments, type.annotations)
                }
            }
            is KSTypeReferenceDescriptorImpl -> {
                return getKSTypeCached(type.kotlinType)
            }
            is KSTypeReferenceJavaImpl -> {
                val psi = (type.psi as? PsiClassReferenceType)?.resolve()
                if (psi is PsiTypeParameter) {
                    val containingDeclaration = if (psi.owner is PsiClass) {
                        moduleClassResolver.resolveClass(JavaClassImpl(psi.owner as PsiClass))
                    } else {
                        moduleClassResolver.resolveClass(
                            JavaMethodImpl(psi.owner as PsiMethod).containingClass
                        )?.unsubstitutedMemberScope!!.getDescriptorsFiltered().single { it.findPsi() == psi.owner } as FunctionDescriptor
                    } as DeclarationDescriptor
                    return getKSTypeCached(
                        LazyJavaTypeParameterDescriptor(
                            lazyJavaResolverContext,
                            JavaTypeParameterImpl(psi),
                            psi.index,
                            containingDeclaration
                        ).defaultType
                    )

                } else {
                    return getKSTypeCached(resolveJavaType(type.psi), type.element.typeArguments, type.annotations)
                }
            }
            else -> throw IllegalStateException()
        }
    }

    fun findDeclaration(kotlinType: KotlinType): KSDeclaration {
        val descriptor = kotlinType.constructor.declarationDescriptor
        val psi = descriptor?.findPsi()
        return if (psi != null && psi !is KtTypeParameter) {
            when (psi) {
                is KtClassOrObject -> KSClassDeclarationImpl.getCached(psi)
                is PsiClass -> KSClassDeclarationJavaImpl.getCached(psi)
                is KtTypeAlias -> KSTypeAliasImpl.getCached(psi)
                else -> throw IllegalStateException()
            }
        } else {
            when (descriptor) {
                is ClassDescriptor -> KSClassDeclarationDescriptorImpl.getCached(descriptor)
                is TypeParameterDescriptor -> KSTypeParameterDescriptorImpl.getCached(descriptor)
                else -> throw IllegalStateException()
            }
        }
    }

    // Finds closest non-local scope.
    fun KtElement.findLexicalScope(): LexicalScope {
        return containingNonLocalDeclaration()?.let {
            resolveSession.declarationScopeProvider.getResolutionScopeForDeclaration(it)
        } ?: resolveSession.fileScopeProvider.getFileResolutionScope(this.containingKtFile)
    }

    fun resolveAnnotationEntry(ktAnnotationEntry: KtAnnotationEntry): AnnotationDescriptor? {
        bindingTrace.get(BindingContext.ANNOTATION, ktAnnotationEntry)?.let { return it }
        val containingDeclaration = KtStubbedPsiUtil.getContainingDeclaration(ktAnnotationEntry)
        return if (containingDeclaration?.let { KtPsiUtil.isLocal(containingDeclaration) } == true) {
            resolveDeclarationForLocal(containingDeclaration)
            bindingTrace.get(BindingContext.ANNOTATION, ktAnnotationEntry)
        } else {
            ktAnnotationEntry.findLexicalScope().let { scope ->
                annotationResolver.resolveAnnotationsWithArguments(scope, listOf(ktAnnotationEntry), bindingTrace)
                bindingTrace.get(BindingContext.ANNOTATION, ktAnnotationEntry)
            }
        }
    }

    fun resolveDeclarationForLocal(localDeclaration: KtDeclaration) {
        var declaration = KtStubbedPsiUtil.getContainingDeclaration(localDeclaration) ?: return
        while (KtPsiUtil.isLocal(declaration))
            declaration = KtStubbedPsiUtil.getContainingDeclaration(declaration)!!

        val containingFD = resolveSession.resolveToDescriptor(declaration).also {
            ForceResolveUtil.forceResolveAllContents(it)
        }

        if (declaration is KtNamedFunction) {
            val dataFlowInfo = DataFlowInfo.EMPTY
            val scope = resolveSession.declarationScopeProvider.getResolutionScopeForDeclaration(declaration)
            bodyResolver.resolveFunctionBody(dataFlowInfo, bindingTrace, declaration, containingFD as FunctionDescriptor, scope)
        }
    }

    override fun getTypeArgument(typeRef: KSTypeReference, variance: Variance): KSTypeArgument {
        return KSTypeArgumentLiteImpl.getCached(typeRef, variance)
    }

    override fun asMemberOf(
        property: KSPropertyDeclaration,
        containing: KSType
    ) : KSType {
        val propertyDeclaredIn = property.closestClassDeclaration()
            ?: throw IllegalArgumentException("Cannot call asMemberOf with a property that is " +
                "not declared in a class or an interface")
        val declaration = resolvePropertyDeclaration(property)
        if(declaration != null && containing is KSTypeImpl) {
            if (!containing.kotlinType.isSubtypeOf(propertyDeclaredIn)) {
                throw IllegalArgumentException(
                    "$containing is not a sub type of the class/interface that contains `$property` ($propertyDeclaredIn)"
                )
            }
            val typeSubstitutor = containing.kotlinType.createTypeSubstitutor()
            val substituted = declaration.substitute(typeSubstitutor) as? ValueDescriptor
            substituted?.let {
                return KSTypeImpl.getCached(substituted.type)
            }
        }
        // if substitution fails, fallback to the type from the property
        return property.type.resolve()
    }

    override fun asMemberOf(
        function: KSFunctionDeclaration,
        containing: KSType
    ) : KSFunctionType {
        val functionDeclaredIn = function.closestClassDeclaration()
            ?: throw IllegalArgumentException("Cannot call asMemberOf with a function that is " +
                "not declared in a class or an interface")
        val declaration = resolveFunctionDeclaration(function)
        module.builtIns.getFunction(1).defaultType
        if(declaration != null && containing is KSTypeImpl) {
            if(!containing.kotlinType.isSubtypeOf(functionDeclaredIn)) {
                throw IllegalArgumentException(
                    "$containing is not a sub type of the class/interface that contains `$function` ($functionDeclaredIn)"
                )
            }
            val typeSubstitutor = containing.kotlinType.createTypeSubstitutor()
            val substituted = declaration.substitute(typeSubstitutor)
            val functionArgs = mutableListOf<TypeProjection>()
            // receiver is the first parameter but only if it exists
            if (substituted.extensionReceiverParameter != null) {
                functionArgs.add(
                    substituted.extensionReceiverParameter!!.type.asTypeProjection()
                )
            }
            // then comes types for value parameters
            functionArgs.addAll(
                substituted.valueParameters.map {
                    it.type.asTypeProjection()
                }
            )
            // finally, the last type argument is the return type
            functionArgs.add(
                substituted.returnTypeOrNothing.asTypeProjection()
            )


            val baseDesc = module.builtIns.getFunction(functionArgs.size - 1)
            val annotations = if (function.extensionReceiver != null) {
                val extensionFunctionAnntoation = BuiltInAnnotationDescriptor(
                    builtIns = module.builtIns,
                    fqName = KotlinBuiltIns.FQ_NAMES.extensionFunctionType,
                    allValueArguments = emptyMap()
                )
                CompositeAnnotations(baseDesc.defaultType.annotations,
                Annotations.create(listOf(extensionFunctionAnntoation)))
            } else {
                baseDesc.defaultType.annotations
            }

            val customMade = baseDesc.defaultType.replace(
                newArguments = functionArgs,
                newAnnotations = annotations
            )
//            val foo = KotlinTypeFactory.simpleType(
//                annotations,
//                (baseDesc as SimpleType).constructor,
//                newArguments,
//                isMarkedNullable
//            )
//            println("$containing : $function -> $customMade")
//            val customMade = KotlinTypeFactory.simpleTypeWithNonTrivialMemberScope(
//                annotations = substituted.annotations,
//                constructor = baseDesc.typeConstructor,
//                arguments = functionArgs,
//                nullable = false,
//                memberScope =  baseDesc.unsubstitutedMemberScope
//            )
//            val constructed = baseDesc.constructors.firstOrNull()?.let {
//                it.newCopyBuilder()
//                .setReturnType(substituted.returnTypeOrNothing)
//                .setTypeParameters(substituted.typeParameters)
//                .setValueParameters(substituted.valueParameters)
//                    .setExtensionReceiverParameter()
//                .build()
//            }
            if (true) {
                return KSFunctionTypeFromKsType(customMade)
            }

            return KSFunctionTypeImpl(substituted)
        }
        // if substitution fails, fallback to types inferred from the function declaration
        return KSFunctionTypeDeclarationImpl(function)
    }

    private fun KotlinType.isSubtypeOf(declaration: KSClassDeclaration): Boolean {
        val classDeclaration = resolveClassDeclaration(declaration)
        if (classDeclaration == null) {
            throw IllegalArgumentException(
                "Cannot find the declaration for class $classDeclaration"
            )
        }
        return constructor
            .declarationDescriptor
            ?.getAllSuperClassifiers()
            ?.any { it == classDeclaration } == true
    }

    override val builtIns: KSBuiltIns by lazy {
        val builtIns = module.builtIns
        object : KSBuiltIns {
            override val anyType: KSType by lazy { getKSTypeCached(builtIns.anyType) }
            override val nothingType by lazy { getKSTypeCached(builtIns.nothingType) }
            override val unitType: KSType by lazy { getKSTypeCached(builtIns.unitType) }
            override val numberType: KSType by lazy { getKSTypeCached(builtIns.numberType) }
            override val byteType: KSType by lazy { getKSTypeCached(builtIns.byteType) }
            override val shortType: KSType by lazy { getKSTypeCached(builtIns.shortType) }
            override val intType: KSType by lazy { getKSTypeCached(builtIns.intType) }
            override val longType: KSType by lazy { getKSTypeCached(builtIns.longType) }
            override val floatType: KSType by lazy { getKSTypeCached(builtIns.floatType) }
            override val doubleType: KSType by lazy { getKSTypeCached(builtIns.doubleType) }
            override val charType: KSType by lazy { getKSTypeCached(builtIns.charType) }
            override val booleanType: KSType by lazy { getKSTypeCached(builtIns.booleanType) }
            override val stringType: KSType by lazy { getKSTypeCached(builtIns.stringType) }
            override val iterableType: KSType by lazy { getKSTypeCached(builtIns.iterableType.replaceArgumentsWithStarProjections()) }
            override val annotationType: KSType by lazy { getKSTypeCached(builtIns.annotationType) }
            override val arrayType: KSType by lazy { getKSTypeCached(builtIns.array.defaultType.replaceArgumentsWithStarProjections()) }
        }
    }
}

open class BaseVisitor : KSVisitorVoid() {
    override fun visitClassDeclaration(type: KSClassDeclaration, data: Unit) {
        for (declaration in type.declarations) {
            declaration.accept(this, Unit)
        }
    }

    override fun visitFile(file: KSFile, data: Unit) {
        for (declaration in file.declarations) {
            declaration.accept(this, Unit)
        }
    }

    override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: Unit) {
        for (declaration in function.declarations) {
            declaration.accept(this, Unit)
        }
    }
}

// TODO: cross module resolution
fun DeclarationDescriptor.findExpectsInKSDeclaration(): List<KSDeclaration> =
    findExpects().map {
        it.toKSDeclaration()
    }

// TODO: cross module resolution
fun DeclarationDescriptor.findActualsInKSDeclaration(): List<KSDeclaration> =
    findActuals().map {
        it.toKSDeclaration()
    }

fun MemberDescriptor.toKSDeclaration(): KSDeclaration =
    when (val psi = findPsi()) {
        is KtClassOrObject -> KSClassDeclarationImpl.getCached(psi)
        is KtFunction -> KSFunctionDeclarationImpl.getCached(psi)
        is KtProperty -> KSPropertyDeclarationImpl.getCached((psi))
        is KtTypeAlias -> KSTypeAliasImpl.getCached(psi)
        else -> when (this) {
            is ClassDescriptor -> KSClassDeclarationDescriptorImpl.getCached(this)
            is FunctionDescriptor -> KSFunctionDeclarationDescriptorImpl.getCached(this)
            is PropertyDescriptor -> KSPropertyDeclarationDescriptorImpl.getCached(this)
            else -> throw IllegalStateException("Unknown expect/actual implementation")
        }
    }

/**
 * [NewTypeSubstitutor] handles variance better than [TypeSubstitutor] so we use it when subtituting
 * types in [ResolverImpl.asMemberOf] implementations.
 */
private fun TypeSubstitutor.toNewSubstitutor() = composeWith(
    org.jetbrains.kotlin.resolve.calls.inference.components.EmptySubstitutor
)

private fun KotlinType.createTypeSubstitutor(): NewTypeSubstitutor {
    return SubstitutionUtils.buildDeepSubstitutor(this).toNewSubstitutor()
}
