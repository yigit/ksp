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

// TEST PROCESSOR: JavaModifierProcessor
// EXPECTED:
// C: PUBLIC ABSTRACT
// intFun: JAVA_SYNCHRONIZED JAVA_DEFAULT
// foo: ABSTRACT JAVA_STRICT
// staticStr: PRIVATE
// s1: FINAL JAVA_TRANSIENT
// i1: PROTECTED JAVA_STATIC JAVA_VOLATILE
// END
// FILE: a.kt
annotation class Test

@Test
class Foo : C() {

}

// FILE: C.java

public abstract class C {

    private String staticStr = "str"

    final transient String s1;

    protected static volatile int i1;

    default synchronized int intFun() {
        return 1;
    }

    abstract strictfp void foo() {}
}
