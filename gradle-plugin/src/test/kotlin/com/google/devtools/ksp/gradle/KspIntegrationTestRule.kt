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
    private val tmpFolder: TemporaryFolder,
    private val useCompositeBuild: Boolean = false // maybe make this configurable too?
) : TestWatcher() {
    lateinit var rootDir: File
    lateinit var processorModule: TestModule
    lateinit var appModule: TestModule

    val kspApiCoordinates = if (useCompositeBuild) {
        "com.google.devtools.ksp:api"
    } else {
        "com.google.devtools.ksp:symbol-processing-api:${TestRepoInitializer.VERSION}"
    }

    fun runner(): GradleRunner {
        processorModule.writeBuildFile()
        appModule.writeBuildFile()
        return GradleRunner.create()
            .withProjectDir(rootDir)
            .withArguments("-Dkotlin.compiler.execution.strategy=\"in-process\"")
    }

    fun setProcessor(processor: KClass<out SymbolProcessor>) {
        val qName = checkNotNull(processor.java.name) {
            "Must provide a class that can be loaded by qualified name"
        }
        processorModule.servicesFile.writeText("$qName\n")
    }

    fun setupAppAsJvmApp() {
        appModule.plugins.add(PluginDeclaration.kotlin("jvm"))
        appModule.plugins.add(PluginDeclaration.id("com.google.devtools.ksp"))
    }

    fun setupAppAsAndroidApp() {
        appModule.plugins.add(PluginDeclaration.id("com.android.application"))
        appModule.plugins.add(PluginDeclaration.kotlin("android"))
        appModule.plugins.add(PluginDeclaration.id("com.google.devtools.ksp"))
        appModule.buildFileAdditions.add("""
            android {
                compileSdkVersion="android-29"
            }
        """.trimIndent())
        appModule.moduleRoot.resolve("src/main/AndroidManifest.xml")
            .also {
                it.parentFile.mkdirs()
            }.writeText(
            """
            <?xml version="1.0" encoding="utf-8"?>
            <manifest xmlns:android="http://schemas.android.com/apk/res/android"
                package="com.example.kspandroidtestapp">
            </manifest>
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

    fun addApplicationTestSource(name: String, contents: String) {
        val srcDir = when {
            name.endsWith(".kt") -> appModule.kotlinTestSourceDir
            name.endsWith(".java") -> appModule.javaTestSourceDir
            else -> error("must provide java or kotlin file")
        }
        srcDir.resolve(name).writeText(contents)
    }

    fun addAndroidTestSource(name: String, contents: String) {
        val srcDir = when {
            name.endsWith(".kt") -> appModule.kotlinAndroidTestSourceDir
            name.endsWith(".java") -> appModule.javaAndroidTestSourceDir
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
        val localTestRepo = if (useCompositeBuild) {
            ""
        } else {
            "maven(\"${TestRepoInitializer.getTestRepo(testConfig)}\")"
        }
        val rootSettingsFile = if(useCompositeBuild) {
            """
                includeBuild("${testConfig.kspProjectDir.absolutePath}")
                include("processor")
                include("app")
                pluginManagement {
                    repositories {
                        gradlePluginPortal()
                        google()
                    }
                }
            """.trimIndent()
        } else {
            """
                include("processor")
                include("app")
                pluginManagement {
                    repositories {
                        $localTestRepo
                        gradlePluginPortal()
                        google()
                    }
                }
            """.trimIndent()
        }
        rootDir.resolve("settings.gradle.kts").writeText(rootSettingsFile)

        val rootBuildFile = """
            plugins {
                kotlin("jvm") version "${testConfig.kotlinBaseVersion}" apply false
                kotlin("android") version "${testConfig.kotlinBaseVersion}" apply false
                id("com.android.application") version "${testConfig.androidBaseVersion}" apply false
                id("com.google.devtools.ksp") version "${TestRepoInitializer.VERSION}" apply false
            }
            repositories {
                $localTestRepo
                mavenCentral()
                google()
            }
            configurations.all {
                resolutionStrategy.eachDependency {
                    if (requested.group == "org.jetbrains.kotlin") {
                        useVersion("1.4.20")
                    }
                }
            }
            subprojects {
                repositories {
                    $localTestRepo
                    mavenCentral()
                    google()
                }
                configurations.all {
                    resolutionStrategy.eachDependency {
                        if (requested.group == "org.jetbrains.kotlin") {
                            useVersion("1.4.20")
                        }
                    }
                }
            }
        """.trimIndent()
        rootDir.resolve("build.gradle.kts").writeText(rootBuildFile)

        processorModule.plugins.add(PluginDeclaration.kotlin("jvm"))
        processorModule.dependencies.add(DependencyDeclaration.artifact("implementation", kspApiCoordinates))
        processorModule.dependencies.add(
            DependencyDeclaration.files(
                "implementation",
                testConfig.processorClasspath
            )
        )
        if (useCompositeBuild) {
            rootDir.resolve("gradle.properties").writeText(
                "KSP_ARTIFACT_NAME=symbol-processing-for-tests"
            )
        }
    }

    data class TestConfig(
        val kspProjectDir: File,
        val testDataDir: File,
        val processorClasspath: String,
        val mavenRepoDir: File
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

        val androidBaseVersion by lazy {
            kspProjectProperties["agpBaseVersion"] as String
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
                    processorClasspath = props.get("processorClasspath") as String,
                    mavenRepoDir = File(props.get("mavenRepoDir") as String),
                )
            }
        }
    }

    class TestModule(
        val moduleRoot: File,
        plugins: List<PluginDeclaration> = emptyList(),
        dependencies: List<DependencyDeclaration> = emptyList()
    ) {
        val plugins = LinkedHashSet(plugins)
        val dependencies = LinkedHashSet(dependencies)
        val buildFileAdditions = LinkedHashSet<String>()
        val name = moduleRoot.name

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

        val kotlinTestSourceDir
            get() = moduleRoot.resolve("src/test/kotlin").also {
                it.mkdirs()
            }
        val javaTestSourceDir
            get() = moduleRoot.resolve("src/test/java").also {
                it.mkdirs()
            }

        val kotlinAndroidTestSourceDir
            get() = moduleRoot.resolve("src/androidTest/kotlin").also {
                it.mkdirs()
            }
        val javaAndroidTestSourceDir
            get() = moduleRoot.resolve("src/androidTest/java").also {
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

        fun writeBuildFile() {
            val contents = buildString {
                appendln("plugins {")
                plugins.forEach { plugin ->
                    appendln(plugin.toCode().prependIndent("    "))
                }
                appendln("}")
                appendln("dependencies {")
                dependencies.forEach { dependency ->
                    appendln(dependency.toCode().prependIndent("    "))
                }
                appendln("}")
                buildFileAdditions.forEach {
                    appendln(it)
                }
            }
            buildFile.writeText(contents)
        }
    }

    data class DependencyDeclaration private constructor(
        val configuration: String,
        val dependency: String
    ) {
        fun toCode() = "${configuration}($dependency)"

        companion object {
            fun module(configuration: String, module: TestModule) =
                DependencyDeclaration(configuration, "project(\":${module.name}\")")

            fun artifact(configuration: String, coordinates: String) =
                DependencyDeclaration(configuration, "\"$coordinates\"")

            fun files(configuration: String, path: String) =
                DependencyDeclaration(configuration, "files(\"$path\")")
        }

    }

    data class PluginDeclaration private constructor(
        val text: String
    ) {
        fun toCode() = text

        companion object {
            fun id(id: String) = PluginDeclaration("id(\"$id\")")
            fun kotlin(id: String) = PluginDeclaration("kotlin(\"$id\")")
        }
    }
}