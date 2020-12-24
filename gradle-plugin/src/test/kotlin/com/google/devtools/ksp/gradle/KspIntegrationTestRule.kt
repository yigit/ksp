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

import com.google.devtools.ksp.processing.SymbolProcessor
import org.gradle.testkit.runner.GradleRunner
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.File
import java.util.Properties
import kotlin.reflect.KClass

class KspIntegrationTestRule(
    private val tmpFolder: TemporaryFolder
) : TestWatcher() {
    lateinit var rootDir: File
    lateinit var processorModule: TestModule
    lateinit var appModule: TestModule

    fun runner(): GradleRunner = GradleRunner.create()
        .withProjectDir(rootDir)
        .withDebug(true)
        .withArguments("-Dkotlin.compiler.execution.strategy=\"in-process\"")

    fun setProcessor(processor: KClass<out SymbolProcessor>) {
        val qName = checkNotNull(processor.java.name) {
            "Must provide a class that can be loaded by qualified name"
        }
        processorModule.servicesFile.writeText("$qName\n")
    }

    fun setupAppAsJvmApp() {
        appModule.buildFile.writeText(
            """
            plugins {
                kotlin("jvm")
                id("com.google.devtools.ksp")
            }
            dependencies {
                ksp(project(":processor"))
            }
            """.trimIndent()
        )
    }

    fun addApplicationSource(name: String, contents: String) {
        val srcDir = when {
            name.endsWith(".kt") -> appModule.kotlinSourceDir
            name.endsWith(".java") -> appModule.javaSourceDir
            else -> error("must provide java or kotlin file")
        }
        srcDir.resolve(name).writeText(contents)
    }

    override fun starting(description: Description) {
        super.starting(description)
        val testConfig = TestConfig.read()
        rootDir = tmpFolder.newFolder()
        processorModule = TestModule(rootDir.resolve("processor"))
        appModule = TestModule(rootDir.resolve("app"))
        val rootSettingsFile = """
            includeBuild("${testConfig.kspProjectDir.absolutePath}")
            include("processor")
            include("app")
        """.trimIndent()
        rootDir.resolve("settings.gradle.kts").writeText(rootSettingsFile)

        val rootBuildFile = """
            plugins {
                kotlin("jvm") version "${testConfig.kotlinBaseVersion}" apply false
            }
            repositories {
                mavenCentral()
            }
            subprojects {
                repositories {
                    mavenCentral()
                }
            }
        """.trimIndent()
        rootDir.resolve("build.gradle.kts").writeText(rootBuildFile)

        val processorBuildFile = """
            plugins {
                kotlin("jvm")
            }
            dependencies {
                // notice that we use api instead of symbol-processing-api to match the module name
                implementation("com.google.devtools.ksp:api")
                implementation(files("${testConfig.processorClasspath}"))
            }
        """.trimIndent()
        processorModule.buildFile.writeText(processorBuildFile)

        rootDir.resolve("gradle.properties").writeText(
            "KSP_ARTIFACT_NAME=symbol-processing-for-tests"
        )
    }

    data class TestConfig(
        val kspProjectDir: File,
        val testDataDir: File,
        val processorClasspath: String
    ) {
        private val kspProjectProperties by lazy {
            Properties().also { props ->
                kspProjectDir.resolve("gradle.properties").inputStream().use {
                    props.load(it)
                }
            }
        }
        val kotlinBaseVersion by lazy {
            kspProjectProperties["kotlinBaseVersion"] as String
        }

        companion object {
            fun read(): TestConfig {
                val props = Properties()
                TestConfig::class.java.classLoader.getResourceAsStream("testprops.properties").use {
                    props.load(it)
                }
                return TestConfig(
                    kspProjectDir = File(props.get("kspProjectRootDir") as String),
                    testDataDir = File(props.get("testDataDir") as String),
                    processorClasspath = props.get("processorClasspath") as String
                )
            }
        }
    }

    class TestModule(
        val moduleRoot: File
    ) {
        init {
            moduleRoot.mkdirs()
        }

        val kotlinSourceDir
            get() = moduleRoot.resolve("src/main/kotlin").also {
                it.mkdirs()
            }
        val javaSourceDir
            get() = moduleRoot.resolve("src/main/java").also {
                it.mkdirs()
            }
        val servicesDir
            get() = moduleRoot.resolve("src/main/resources/META-INF/services/").also {
                it.mkdirs()
            }
        val servicesFile
            get() = servicesDir.resolve("com.google.devtools.ksp.processing.SymbolProcessor")

        val buildFile
            get() = moduleRoot.resolve("build.gradle.kts")
    }
}