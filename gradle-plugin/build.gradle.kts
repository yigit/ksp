import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

description = "Kotlin Symbol Processor"

val kotlinBaseVersion: String by project
val junitVersion: String by project
val googleTruthVersion: String by project

tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "1.8"
    kotlinOptions.freeCompilerArgs += "-Xjvm-default=compatibility"
}

plugins {
    kotlin("jvm")
    id("java-gradle-plugin")
    `maven-publish`
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin-api:$kotlinBaseVersion")
    implementation("org.jetbrains.kotlin:kotlin-gradle-plugin:$kotlinBaseVersion")
    compileOnly("org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinBaseVersion")
    testImplementation(gradleApi())
    testImplementation(project(":api"))
    testImplementation("junit:junit:$junitVersion")
    testImplementation("com.google.truth:truth:$googleTruthVersion")
    testImplementation(gradleTestKit())
}

tasks.named("validatePlugins").configure {
    onlyIf {
        // while traversing classpath, this hits a class not found issue.
        // Disabled until gradle kotlin version and our kotlin version matches
        // java.lang.ClassNotFoundException: org/jetbrains/kotlin/compilerRunner/KotlinLogger
        false
    }
}

gradlePlugin {
    plugins {
        create("symbol-processing-gradle-plugin") {
            id = "com.google.devtools.ksp"
            implementationClass = "com.google.devtools.ksp.gradle.KspGradleSubplugin"
            description = "Kotlin symbol processing integration for Gradle"
        }
    }
}

tasks {
    val sourcesJar by creating(Jar::class) {
        archiveClassifier.set("sources")
        from(project.sourceSets.main.get().allSource)
    }
}


publishing {
    publications {
        // the name of this publication should match the name java-gradle-plugin looks up
        // https://github.com/gradle/gradle/blob/master/subprojects/plugin-development/src/main/java/org/gradle/plugin/devel/plugins/MavenPluginPublishPlugin.java#L73
        this.create<MavenPublication>("pluginMaven") {
            artifactId = "symbol-processing-gradle-plugin"
            artifact(tasks["sourcesJar"])
            pom {
                name.set("symbol-processing-gradle-plugin")
                description.set("Kotlin symbol processing integration for Gradle")
            }
        }
    }
}

val testPropsOutDir = project.layout.buildDirectory.dir("test-config")
val writeTestPropsTask = tasks.register<WriteProperties>("prepareTestConfiguration") {
    description = "Generates a properties file with the current environment for gradle integration tests"
    this.setOutputFile(testPropsOutDir.map {
        it.file("testprops.properties")
    })
    property("kspProjectRootDir", rootProject.projectDir.absolutePath)
    property("testDataDir", project.projectDir.resolve("src/test-data").absolutePath)
    property("processorClasspath", project.tasks["compileTestKotlin"].outputs.files.asPath)
}

java {
    sourceSets {
        test {
            resources.srcDir(testPropsOutDir)
        }
    }
}

// this should not be necessary
tasks.named("compileTestKotlin").configure {
    dependsOn(writeTestPropsTask)
}