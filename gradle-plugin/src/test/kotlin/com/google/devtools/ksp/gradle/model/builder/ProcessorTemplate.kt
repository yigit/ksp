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
package com.google.devtools.ksp.gradle.model.builder
import org.intellij.lang.annotations.Language
class ProcessorTemplate(
    val className : String = "TestProcessor",
    val packageName: String = "ksp.test",
    val fileName : String = "$className.kt",
    @Language("kotlin")
    val processCode: String
) {
    val qName = "$packageName.$className"
    val filePath = packageName.replace(".", "/") + "/" + fileName
    fun toCode(): String {
        return buildString {
            appendln("package $packageName")
            imports.forEach { appendln("import $it") }
            val offsetProcessCode = processCode.trimIndent().lineSequence().joinToString {
                "        $it"
            }
            appendln("""
                class ${className} : SymbolProcessor {
                    lateinit var ${Props.options}: Map<String, String>
                    lateinit var ${Props.kotlinVersion}: KotlinVersion
                    lateinit var ${Props.codeGenerator}: CodeGenerator
                    lateinit var ${Props.logger}: KSPLogger

                    override fun finish() {

                    }

                    override fun init(
                        options: Map<String, String>,
                        kotlinVersion: KotlinVersion,
                        codeGenerator: CodeGenerator,
                        logger: KSPLogger
                    ) {
                        this.${Props.options} = options
                        this.${Props.kotlinVersion} = kotlinVersion
                        this.${Props.codeGenerator} = codeGenerator
                        this.${Props.logger} = logger
                    }

                    override fun process(${Props.resolver}: Resolver) {
            """.trimIndent())
            appendln(offsetProcessCode)
            appendln("""
                    }
                }
            """.trimIndent())
        }
    }

    private val imports = listOf(
        "com.google.devtools.ksp.getClassDeclarationByName",
        "com.google.devtools.ksp.processing.CodeGenerator",
        "com.google.devtools.ksp.processing.KSPLogger",
        "com.google.devtools.ksp.processing.Resolver",
        "com.google.devtools.ksp.processing.SymbolProcessor",
    )

    object Props {
        const val options = "options"
        const val kotlinVersion = "kotlinVersion"
        const val codeGenerator = "codeGenerator"
        const val logger = "logger"
        const val resolver = "resolver"
    }

    companion object {
        fun create(
            processCode: CodeBlock
        ) : ProcessorTemplate {
            return ProcessorTemplate(processCode = processCode.build(Props))
        }
    }

    fun interface CodeBlock {
        @Language("kotlin")
        fun build(props: Props): String
    }
}