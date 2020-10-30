package com.google.devtools.ksp.processor

import com.google.devtools.ksp.closestClassDeclaration
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import java.io.File

class AnnotationsInCompiledCodeProcessor : AbstractTestProcessor() {
    private val results = mutableListOf<String>()
    override fun toResult(): List<String> = results

    override fun process(resolver: Resolver) {
        val main = resolver.getClassDeclarationByName("ClassInMainModule")!!
        val prop = main.declarations.first {
            it.simpleName.asString() == "field1"
        } as KSPropertyDeclaration
        val propType = prop.type.resolve()
        val targetProp = propType.declaration.closestClassDeclaration()!!.getAllProperties().first()
        results.addAll(targetProp.annotations.map { it.shortName.asString() })

    }

    override fun getAdditionalDependencies(): List<File> {
        return emptyList()
//        return listOf(
//            File("/Users/yboyar/src/ksp-dependency-annotation-repro/shared/build/libs/shared.jar"),
//            File("/Users/yboyar/src/ksp-dependency-annotation-repro/baseModule/build/libs/baseModule.jar")
//        )
    }
}