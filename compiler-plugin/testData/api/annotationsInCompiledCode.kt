// WITH_RUNTIME
// TEST PROCESSOR: AnnotationsInCompiledCodeProcessor
// EXPECTED:
// SharedAnnotation
// END
// MODULE: shared
// FILE: SharedAnnotation.kt
@Target(AnnotationTarget.FIELD)
@Retention(AnnotationRetention.BINARY)
annotation class SharedAnnotation {
}
// MODULE: base(shared)
// FILE: Base.kt
class ClassFromDependency {
    @field:SharedAnnotation
    val field2: Int = 0
}
// MODULE: main(shared, base)
// FILE: main.kt
// FILE: a.kt
class ClassInMainModule {
    @SharedAnnotation
    val field1: ClassFromDependency = 0
}