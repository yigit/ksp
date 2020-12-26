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
package com.google.devtools.ksp.gradle

import com.google.common.truth.Truth.assertThat
import com.google.devtools.ksp.gradle.KspIntegrationTestRule.DependencyDeclaration.Companion.module
import com.google.devtools.ksp.gradle.processor.TestSymbolProcessor
import com.google.devtools.ksp.processing.Dependencies
import com.google.devtools.ksp.processing.Resolver
import com.google.devtools.ksp.symbol.KSClassDeclaration
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SourceSetConfigurationsTest {
    @Rule
    @JvmField
    val tmpDir = TemporaryFolder()

    @Rule
    @JvmField
    val testRule = KspIntegrationTestRule(tmpDir)

    @Test
    fun configurationsForJvmApp() {
        testRule.setupAppAsJvmApp()
        testRule.addApplicationSource("Foo.kt", "class Foo")
        val result = testRule.runner()
            .withArguments(":app:dependencies")
            .build()

        assertThat(result.output.lines()).containsAtLeast("ksp", "kspTest")
    }

    @Test
    fun configurationsForAndroidApp() {
        testRule.setupAppAsAndroidApp()
        testRule.addApplicationSource("Foo.kt", "class Foo")
        val result = testRule.runner()
            .withArguments(":app:dependencies")
            .build()

        assertThat(result.output.lines()).containsAtLeast(
            "ksp",
            "kspAndroidTest",
            "kspAndroidTestDebug",
            "kspAndroidTestRelease",
            "kspDebug",
            "kspRelease",
            "kspTest",
            "kspTestDebug",
            "kspTestRelease"
        )
    }

    @Test
    fun kspForTests() {
        testRule.setupAppAsJvmApp()
        testRule.addApplicationSource("App.kt", """
            @Suppress("app")
            class InApp {
            }
        """.trimIndent())
        testRule.addApplicationTestSource("InTest.kt", """
            @Suppress("test")
            class InTest {
                val impl = InTest_Impl()
            }
        """.trimIndent())
        class Processor : TestSymbolProcessor() {
            override fun process(resolver: Resolver) {
                resolver.getSymbolsWithAnnotation(Suppress::class.qualifiedName!!)
                    .filterIsInstance<KSClassDeclaration>()
                    .forEach {
                        if (it.simpleName.asString() == "InApp") {
                            error("should not run on the app sources")
                        }
                        val genClassName = "${it.simpleName.asString()}_Impl"
                        codeGenerator.createNewFile(Dependencies.ALL_FILES, "", genClassName).use {
                            it.writer().use {
                                it.write("class $genClassName")
                            }
                        }
                    }
            }
        }
        testRule.setProcessor(Processor::class)
        testRule.appModule.dependencies.add(
            module("kspTest", testRule.processorModule)
        )
        testRule.runner().withArguments(":app:test").build()
    }
}