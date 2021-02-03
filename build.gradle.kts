import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

plugins {
    kotlin("jvm") version "1.4.20" apply false
    id("com.diffplug.spotless") version "5.9.0"
    id("org.jlleitschuh.gradle.ktlint-idea") version "9.4.1"
}

if (!extra.has("kspVersion")) {
    val kotlinBaseVersion: String by project
    val today = LocalDateTime.now().format(DateTimeFormatter.BASIC_ISO_DATE)
    extra.set("kspVersion", "$kotlinBaseVersion-dev-experimental-$today")
}
repositories {
    // necessary to run ktlintApplyToIdea task
    mavenCentral()
}
subprojects {
    group = "com.google.devtools.ksp"
    version = rootProject.extra.get("kspVersion") as String
    repositories {
        mavenCentral()
        google()
        maven("https://dl.bintray.com/kotlin/kotlin-eap")
    }
    // spotless setup
    pluginManager.apply("com.diffplug.spotless")

    extensions.getByType<com.diffplug.gradle.spotless.SpotlessExtension>().apply {
        kotlin {
            target("src/**/*.kt")
            ktlint("0.40.0").userData(
                mapOf(
                    "max_line_length" to "120",
                    "disabled_rules" to "no-wildcard-imports",
                    "insert_final_newline" to "true"
                )
            )
            licenseHeaderFile(project.rootProject.file("scripts/copyright.txt"))
        }
    }
    tasks.withType<Jar>().configureEach {
        manifest.attributes.apply {
            put("Implementation-Vendor", "Google")
            put("Implementation-Version", project.version)
        }
    }
    pluginManager.withPlugin("maven-publish") {
        val publishExtension = extensions.getByType<PublishingExtension>()
        publishExtension.repositories {
            if (extra.has("outRepo")) {
                val outRepo = extra.get("outRepo") as String
                maven {
                    url = File(outRepo).toURI()
                }
            } else {
                mavenLocal()
            }
            maven {
                name = "test"
                url = uri("${rootProject.buildDir}/repos/test")
            }
        }
        publishExtension.publications.whenObjectAdded {
            check(this is MavenPublication) {
                "unexpected publication $this"
            }
            val publication = this
            publication.pom {
                url.set("https://goo.gle/ksp")
                licenses {
                    license {
                        name.set("The Apache License, Version 2.0")
                        url.set("http://www.apache.org/licenses/LICENSE-2.0.txt")
                    }
                }
                developers {
                    developer {
                        name.set("KSP Team")
                    }
                }
                scm {
                    connection.set("scm:git:https://github.com/google/ksp.git")
                    developerConnection.set("scm:git:https://github.com/google/ksp.git")
                    url.set("https://github.com/google/ksp")
                }
            }
        }
    }
}
