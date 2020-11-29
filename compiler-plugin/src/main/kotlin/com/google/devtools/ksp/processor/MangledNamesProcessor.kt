package com.google.devtools.ksp.processor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyGetter
import com.google.devtools.ksp.symbol.KSPropertySetter
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.visitor.KSTopDownVisitor

@KspExperimental
@Suppress("unused") // used by the test code
class MangledNamesProcessor : AbstractTestProcessor() {
    private val results = mutableListOf<String>()
    override fun toResult() = results

    override fun process(resolver: Resolver) {
        val mangleSourceNames = mutableMapOf<String, String>()
        resolver.getAllFiles().forEach {
            it.accept(MangledNamesVisitor(resolver), mangleSourceNames)
        }
        val mangledDependencyNames = LinkedHashMap<String, String>()
        // also collect results from library dependencies to ensure we resolve module name property
        resolver.getClassDeclarationByName("libPackage.Foo")?.accept(
            MangledNamesVisitor(resolver), mangledDependencyNames
        )
        results.addAll(
            mangleSourceNames.entries.map { (decl, name) ->
                "$decl -> $name"
            }
        )
        results.addAll(
            mangledDependencyNames.entries.map { (decl, name) ->
                "$decl -> $name"
            }
        )
    }

    private class MangledNamesVisitor(
        val resolver: Resolver
    ) : KSTopDownVisitor<MutableMap<String, String>, Unit>() {
        override fun defaultHandler(node: KSNode, data: MutableMap<String, String>) {
        }

        override fun visitClassDeclaration(classDeclaration: KSClassDeclaration, data: MutableMap<String, String>) {
            if (classDeclaration.modifiers.contains(Modifier.INLINE)) {
                // do not visit inline classes
                return
            }
            // put a header for readable output
            data[classDeclaration.qualifiedName!!.asString()] = "declarations"
            super.visitClassDeclaration(classDeclaration, data)
        }

        override fun visitFunctionDeclaration(function: KSFunctionDeclaration, data: MutableMap<String, String>) {
            super.visitFunctionDeclaration(function, data)
            data[function.simpleName.asString()] = resolver.getJvmName(function)
        }

        override fun visitPropertyGetter(getter: KSPropertyGetter, data: MutableMap<String, String>) {
            super.visitPropertyGetter(getter, data)
            data["get-${getter.receiver.simpleName.asString()}"] = resolver.getJvmName(getter)
        }

        override fun visitPropertySetter(setter: KSPropertySetter, data: MutableMap<String, String>) {
            super.visitPropertySetter(setter, data)
            data["set-${setter.receiver.simpleName.asString()}"] = resolver.getJvmName(setter)
        }
    }
}
