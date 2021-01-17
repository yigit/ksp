package com.google.devtools.ksp.gradle

import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Helper class to deploy the project into a repository that will be used in tests.
 *
 * Because we don't have a trivial build, using composite builds might hide possible issues.
 * On the other hand, publishing before each test slows down iteration.
 *
 * This is why gradle-plugin tests can be run both as composite build tests or tests that use a prebuilt of KSP.
 */
object TestRepoInitializer {
    val VERSION = "999-SNAPSHOT"
    private var mavenRepoDir : File? = null
    private val lock = ReentrantLock()
    fun getTestRepo(config: KspIntegrationTestRule.TestConfig): File {
        return lock.withLock {
            mavenRepoDir ?: prepareTestRepo(config).also {
                mavenRepoDir = it
            }
        }
    }

    private fun prepareTestRepo(config: KspIntegrationTestRule.TestConfig): File {
        if (config.mavenRepoDir.exists()) {
            config.mavenRepoDir.deleteRecursively()
        }
        config.mavenRepoDir.mkdirs()
        GradleRunner.create()
            .withProjectDir(config.kspProjectDir)
            .withArguments(
                "publish",
                "-PoutRepo=${config.mavenRepoDir}",
                "-PkspVersion=$VERSION"
            )
            .forwardOutput()
            .build()
        return config.mavenRepoDir
    }
}