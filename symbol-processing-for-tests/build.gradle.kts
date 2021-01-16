/**
 * This module is used in the integration tests of the gradle-plugin and declares a dependency on the shadow
 * configuration of the symbol-processing module.
 *
 * The test then passes an environment variable (KSP_ARTIFACT_NAME) to instruct the KspSubPlugin to refer to this
 * artifact instead of symbol-processing.
 *
 * It is necessary because the test setup uses a composite build which automatically replaces the artifact with
 * symbol-processing but that is insufficient as we need the shadowed configuration of that artifact. Since the shadow
 * configuration is not an outgoing artifact, we use this module as a trampoline.
 */
plugins {
    kotlin("jvm")
}
dependencies {
    // notice that we use api instead of symbol-processing-api to match the module name
    implementation(project(":symbol-processing", configuration = "shadow"))
}