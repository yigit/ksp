package com.google.devtools.ksp.test

import org.gradle.testkit.runner.GradleRunner
import org.junit.Assert
import org.junit.Rule
import org.junit.Test
import java.io.File

class OnErrorIT {
    @Rule
    @JvmField
    val project: TemporaryTestProject = TemporaryTestProject("on-error")

    @Test
    fun testOnError() {
        val gradleRunner = GradleRunner.create().withProjectDir(project.root)

        gradleRunner.withArguments("clean", "assemble").buildAndFail().let { result ->
            val errors = result.output.split("\n").filter { it.startsWith("e: [ksp]") }
            Assert.assertEquals("e: [ksp] Error processor: errored at 2", errors.first())
            Assert.assertEquals("e: [ksp] NormalProcessor called error on 2", errors.last())
        }
    }

    @Test
    fun testOnExceptionInInit() {
        File(project.root, "workload/build.gradle.kts").appendText("\nksp { arg(\"exception\", \"init\") }\n")
        val gradleRunner = GradleRunner.create().withProjectDir(project.root)

        gradleRunner.withArguments("clean", "assemble").buildAndFail().let { result ->
            val errors = result.output.split("\n").filter { it.startsWith("e: [ksp]") }
            Assert.assertEquals("e: [ksp] java.lang.Exception: Test Exception in init", errors.first())
        }
        project.restore("workload/build.gradle.kts")
    }

    @Test
    fun testOnExceptionInProcess() {
        File(project.root, "workload/build.gradle.kts").appendText("\nksp { arg(\"exception\", \"process\") }\n")
        val gradleRunner = GradleRunner.create().withProjectDir(project.root)

        gradleRunner.withArguments("clean", "assemble").buildAndFail().let { result ->
            val errors = result.output.split("\n").filter { it.startsWith("e: [ksp]") }
            Assert.assertEquals("e: [ksp] java.lang.Exception: Test Exception in process", errors.first())
        }
        project.restore("workload/build.gradle.kts")
    }

    @Test
    fun testOnExceptionInFinish() {
        File(project.root, "workload/build.gradle.kts").appendText("\nksp { arg(\"exception\", \"finish\") }\n")
        val gradleRunner = GradleRunner.create().withProjectDir(project.root)

        gradleRunner.withArguments("clean", "assemble").buildAndFail().let { result ->
            val errors = result.output.split("\n").filter { it.startsWith("e: [ksp]") }
            Assert.assertEquals("e: [ksp] java.lang.Exception: Test Exception in finish", errors.first())
        }
        project.restore("workload/build.gradle.kts")
    }

    @Test
    fun testOnExceptionInOnError() {
        File(project.root, "workload/build.gradle.kts").appendText("\nksp { arg(\"exception\", \"error\") }\n")
        val gradleRunner = GradleRunner.create().withProjectDir(project.root)

        gradleRunner.withArguments("clean", "assemble").buildAndFail().let { result ->
            val errors = result.output.split("\n").filter { it.startsWith("e: [ksp]") }

            Assert.assertEquals("e: [ksp] Error processor: errored at 2", errors.first())
            Assert.assertEquals("e: [ksp] java.lang.Exception: Test Exception in error", errors[1])
        }
        project.restore("workload/build.gradle.kts")
    }
}
