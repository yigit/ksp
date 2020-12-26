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

import com.android.build.api.dsl.AndroidSourceSet
import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.extension.AndroidComponentsExtension
import com.google.devtools.ksp.gradle.model.builder.KspModelBuilder
import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.UnknownTaskException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskProvider
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.tooling.provider.model.ToolingModelBuilderRegistry
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.gradle.dsl.KotlinSingleTargetExtension
import org.jetbrains.kotlin.gradle.plugin.FilesSubpluginOption
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilation
import org.jetbrains.kotlin.gradle.plugin.KotlinCompilerPluginSupportPlugin
import org.jetbrains.kotlin.gradle.plugin.SubpluginArtifact
import org.jetbrains.kotlin.gradle.plugin.SubpluginOption
import org.jetbrains.kotlin.gradle.plugin.mapClasspath
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmAndroidCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinJvmCompilation
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinWithJavaCompilation
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import org.jetbrains.kotlin.incremental.ChangedFiles
import org.jetbrains.kotlin.utils.addToStdlib.ifNotEmpty
import java.io.File
import java.util.Locale
import javax.inject.Inject

class KspGradleSubplugin @Inject internal constructor(private val registry: ToolingModelBuilderRegistry) :
        KotlinCompilerPluginSupportPlugin {
    companion object {
        const val KSP_MAIN_CONFIGURATION_NAME = "ksp"
        // gradle integration tests might pass a different artifact name
        val DEFAULT_KSP_ARTIFACT_NAME = "symbol-processing"
        const val KSP_PLUGIN_ID = "com.google.devtools.ksp.symbol-processing"

        @JvmStatic
        fun getKspOutputDir(project: Project, sourceSetName: String) =
                File(project.project.buildDir, "generated/ksp/$sourceSetName")

        @JvmStatic
        fun getKspClassOutputDir(project: Project, sourceSetName: String) =
                File(getKspOutputDir(project, sourceSetName), "classes")

        @JvmStatic
        fun getKspJavaOutputDir(project: Project, sourceSetName: String) =
                File(getKspOutputDir(project, sourceSetName), "java")

        @JvmStatic
        fun getKspKotlinOutputDir(project: Project, sourceSetName: String) =
                File(getKspOutputDir(project, sourceSetName), "kotlin")

        @JvmStatic
        fun getKspResourceOutputDir(project: Project, sourceSetName: String) =
                File(getKspOutputDir(project, sourceSetName), "resources")

        @JvmStatic
        fun getKspCachesDir(project: Project, sourceSetName: String) =
                File(project.project.buildDir, "kspCaches/$sourceSetName")
    }

    lateinit var artifactName: String

    @OptIn(ExperimentalStdlibApi::class)
    private val KotlinCompilation<*>.kspConfigurationName: String
        get() {
            return if (compilationName == SourceSet.MAIN_SOURCE_SET_NAME) {
                KSP_MAIN_CONFIGURATION_NAME
            } else {
                "$KSP_MAIN_CONFIGURATION_NAME${compilationName.capitalize(Locale.US)}"
            }
        }
    private val KotlinCompilation<*>.kspConfiguration: Configuration?
        get() {
            val configName = kspConfigurationName
            return target.project.configurations.findByName(configName)
        }
    @OptIn(ExperimentalStdlibApi::class)
    private val AndroidSourceSet.kspConfigurationName: String
        get() {
            return if (name == SourceSet.MAIN_SOURCE_SET_NAME) {
                KSP_MAIN_CONFIGURATION_NAME
            } else {
                "$KSP_MAIN_CONFIGURATION_NAME${name.capitalize(Locale.US)}"
            }
        }
    override fun apply(project: Project) {
        project.extensions.create("ksp", KspExtension::class.java)
        artifactName = if (project.hasProperty("KSP_ARTIFACT_NAME")) {
            project.properties.get("KSP_ARTIFACT_NAME") as String
        } else {
            DEFAULT_KSP_ARTIFACT_NAME
        }
        project.pluginManager.withPlugin("org.jetbrains.kotlin.jvm") {
            // kotlin extension has the compilation target that we need to look for to create configurations
            decorateKotlinExtension(project)
        }
        project.pluginManager.withPlugin("com.android.application") {
            // for android apps, we need a configuration per source set
            decorateAndroidExtension(project)
        }
        project.pluginManager.withPlugin("com.android.library") {
            // for android libraries, we need a configuration per source set
            decorateAndroidExtension(project)
        }
        registry.register(KspModelBuilder())
    }

    private fun decorateKotlinExtension(project:Project) {
        project.extensions.configure(KotlinSingleTargetExtension::class.java) { kotlinExtension ->
            kotlinExtension.target.compilations.createKspConfigurations(project) { kotlinCompilation ->
                kotlinCompilation.kspConfigurationName
            }
        }
    }

    private fun decorateAndroidExtension(project:Project) {
        @Suppress("UnstableApiUsage")
        project.extensions.configure(CommonExtension::class.java) {
            it.sourceSets.createKspConfigurations(project) { androidSourceSet ->
                androidSourceSet.kspConfigurationName
            }
        }
    }

    /**
     * Creates a KSP configuration for each element in the object container.
     */
    private fun<T> NamedDomainObjectContainer<T>.createKspConfigurations(
        project: Project,
        getKspConfigurationName : (T)-> String
    ) {
        val mainConfiguration = project.configurations.maybeCreate(KSP_MAIN_CONFIGURATION_NAME)
        all {
            val kspConfigurationName = getKspConfigurationName(it)
            if (kspConfigurationName != KSP_MAIN_CONFIGURATION_NAME) {
                val existing = project.configurations.findByName(kspConfigurationName)
                if (existing == null) {
                    project.configurations.create(kspConfigurationName) {
                        it.extendsFrom(mainConfiguration)
                    }
                }
            }
        }
    }

    override fun isApplicable(kotlinCompilation: KotlinCompilation<*>): Boolean = true

    override fun applyToCompilation(kotlinCompilation: KotlinCompilation<*>): Provider<List<SubpluginOption>> {
        val project = kotlinCompilation.target.project
        val kotlinCompileProvider: TaskProvider<KotlinCompile> = project.locateTask(kotlinCompilation.compileKotlinTaskName)!!
        val javaCompile = findJavaTaskForKotlinCompilation(kotlinCompilation)?.get()
        val kspExtension = project.extensions.getByType(KspExtension::class.java)

        val kspConfiguration: Configuration = kotlinCompilation.kspConfiguration
            ?: return project.provider { emptyList() }

        val options = mutableListOf<SubpluginOption>()

        options += FilesSubpluginOption("apclasspath", kspConfiguration)

        val sourceSetName = kotlinCompilation.compilationName
        val classOutputDir = getKspClassOutputDir(project, sourceSetName)
        val javaOutputDir = getKspJavaOutputDir(project, sourceSetName)
        val kotlinOutputDir = getKspKotlinOutputDir(project, sourceSetName)
        val resourceOutputDir = getKspResourceOutputDir(project, sourceSetName)
        val cachesDir = getKspCachesDir(project, sourceSetName)
        val kspOutputDir = getKspOutputDir(project, sourceSetName)
        options += SubpluginOption("classOutputDir", classOutputDir.path)
        options += SubpluginOption("javaOutputDir", javaOutputDir.path)
        options += SubpluginOption("kotlinOutputDir", kotlinOutputDir.path)
        options += SubpluginOption("resourceOutputDir", resourceOutputDir.path)
        options += SubpluginOption("cachesDir", cachesDir.path)
        options += SubpluginOption("incremental", project.findProperty("ksp.incremental")?.toString() ?: "false")
        options += SubpluginOption("incrementalLog", project.findProperty("ksp.incremental.log")?.toString() ?: "false")
        options += SubpluginOption("projectBaseDir", project.project.projectDir.canonicalPath)
        options += SubpluginOption("kspOutputDir", kspOutputDir.path)

        kspExtension.apOptions.forEach {
            options += SubpluginOption("apoption", "${it.key}=${it.value}")
        }

        if (javaCompile != null) {
            val generatedJavaSources = javaCompile.project.fileTree(javaOutputDir)
            generatedJavaSources.include("**/*.java")
            javaCompile.source(generatedJavaSources)
            javaCompile.classpath += project.files(classOutputDir)
        }

        assert(kotlinCompileProvider.name.startsWith("compile"))
        val kspTaskName = kotlinCompileProvider.name.replaceFirst("compile", "ksp")
        val destinationDir = getKspOutputDir(project, sourceSetName)
        InternalTrampoline.KotlinCompileTaskData_register(kspTaskName, kotlinCompilation, project.provider { destinationDir })

        val kspTaskProvider = project.tasks.register(kspTaskName, KspTask::class.java) { kspTask ->
            kspTask.setDestinationDir(destinationDir)
            kspTask.mapClasspath { kotlinCompileProvider.get().classpath }
            kspTask.options = options
            kspTask.outputs.dirs(kotlinOutputDir, javaOutputDir, classOutputDir, resourceOutputDir)
            kspTask.dependsOn(kspConfiguration.buildDependencies)
        }.apply {
            configure {
                kotlinCompilation.allKotlinSourceSets.forEach { sourceSet -> it.source(sourceSet.kotlin) }
                kotlinCompilation.output.classesDirs.from(classOutputDir)
            }
        }

        kotlinCompileProvider.configure { kotlinCompile ->
            kotlinCompile.dependsOn(kspTaskProvider)
            kotlinCompile.source(kotlinOutputDir, javaOutputDir)
            kotlinCompile.classpath += project.files(classOutputDir)
        }

        return project.provider { emptyList() }
    }

    override fun getCompilerPluginId() = KSP_PLUGIN_ID
    override fun getPluginArtifact(): SubpluginArtifact =
            SubpluginArtifact(groupId = "com.google.devtools.ksp", artifactId = artifactName, version = javaClass.`package`.implementationVersion)
}

// Copied from kotlin-gradle-plugin, because they are internal.
internal inline fun <reified T : Task> Project.locateTask(name: String): TaskProvider<T>? =
        try {
            tasks.withType(T::class.java).named(name)
        } catch (e: UnknownTaskException) {
            null
        }

// Copied from kotlin-gradle-plugin, because they are internal.
internal fun findJavaTaskForKotlinCompilation(compilation: KotlinCompilation<*>): TaskProvider<out JavaCompile>? =
        when (compilation) {
          is KotlinJvmAndroidCompilation -> compilation.compileJavaTaskProvider
          is KotlinWithJavaCompilation -> compilation.compileJavaTaskProvider
          is KotlinJvmCompilation -> compilation.compileJavaTaskProvider // may be null for Kotlin-only JVM target in MPP
            else -> null
        }

open class KspTask : KspTaskJ() {
    lateinit var options: List<SubpluginOption>

    init {
        // kotlinc's incremental compilation isn't compatible with symbol processing in a few ways:
        // * It doesn't consider private / internal changes when computing dirty sets.
        // * It compiles iteratively; Sources can be compiled in different rounds.
        incremental = false
    }

    override fun setupCompilerArgs(
            args: K2JVMCompilerArguments,
            defaultsOnly: Boolean,
            ignoreClasspathResolutionErrors: Boolean
    ) {
        super.setupCompilerArgs(args, defaultsOnly, ignoreClasspathResolutionErrors)
        args.addPluginOptions(options)
    }
}

fun K2JVMCompilerArguments.addPluginOptions(options: List<SubpluginOption>) {
    fun SubpluginOption.toArg() = "plugin:${KspGradleSubplugin.KSP_PLUGIN_ID}:${key}=${value}"
    pluginOptions = (options.map { it.toArg() } + pluginOptions!!).toTypedArray()
}

fun K2JVMCompilerArguments.addChangedFiles(changedFiles: ChangedFiles) {
    if (changedFiles is ChangedFiles.Known) {
        val options = mutableListOf<SubpluginOption>()
        changedFiles.modified.ifNotEmpty { options += SubpluginOption("knownModified", map { it.path }.joinToString(":")) }
        changedFiles.removed.ifNotEmpty { options += SubpluginOption("knownRemoved", map { it.path }.joinToString(":")) }
        options.ifNotEmpty { addPluginOptions(this) }
    }
}