/*
 * Copyright 2021 Google LLC
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
import com.google.devtools.ksp.gradle.KspGradleSubplugin.Companion.KSP_MAIN_CONFIGURATION_NAME
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet
import java.util.*

/**
 * This helper class handles communication with the android plugin.
 * It is isolated in a separate class to avoid adding dependency on the android plugin.
 * Instead, we add a compileOnly dependency to the Android Plugin, which means we can still function
 * without the Android plugin.
 */
@Suppress("UnstableApiUsage") // some android APIs are unsable.
class AndroidIntegration(
    private val kspGradleSubplugin: KspGradleSubplugin
) {
    fun applyIfAndroidProject(project: Project) {
        project.pluginManager.withPlugin("com.android.application") {
            // for android apps, we need a configuration per source set
            decorateAndroidExtension(project)
        }
        project.pluginManager.withPlugin("com.android.library") {
            // for android libraries, we need a configuration per source set
            decorateAndroidExtension(project)
        }
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

    private fun decorateAndroidExtension(project: Project) {
        @Suppress("UnstableApiUsage")
        project.extensions.configure(CommonExtension::class.java) { commonAndroidExtension ->
            kspGradleSubplugin.run {
                commonAndroidExtension.sourceSets.createKspConfigurations(project) { androidSourceSet ->
                    listOf(androidSourceSet.kspConfigurationName)
                }
            }
        }
    }
}