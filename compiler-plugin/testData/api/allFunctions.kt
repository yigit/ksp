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

// WITH_RUNTIME
// TEST PROCESSOR: AllFunctionsProcessor
// EXPECTED:
// class: KotlinInterfaceWithProperty
// <init>(): C
// <init>(): JavaImplOfKotlinInterface
// <init>(kotlin.Int): JavaWithConstructor
// <init>(kotlin.Int): JavaWithConstructorInLib
// a
// aFromC
// aFromC
// bFromC
// bar(): kotlin.Boolean
// baz(kotlin.String,kotlin.String(hasDefault),kotlin.String(hasDefault)): kotlin.Boolean
// cFromC
// cFromC
// class: C
// class: Data
// class: Foo
// class: JavaImplOfKotlinInterface
// class: JavaWithConstructor
// class: JavaWithConstructorInLib
// component1(): kotlin.String
// contains(kotlin.Number): kotlin.Boolean
// containsAll(kotlin.collections.Collection): kotlin.Boolean
// copy(kotlin.String(hasDefault)): Data
// equals(kotlin.Any): kotlin.Boolean
// equals(kotlin.Any): kotlin.Boolean
// equals(kotlin.Any): kotlin.Boolean
// equals(kotlin.Any): kotlin.Boolean
// equals(kotlin.Any): kotlin.Boolean
// equals(kotlin.Any): kotlin.Boolean
// equals(kotlin.Any): kotlin.Boolean
// forEach(java.util.function.Consumer): kotlin.Unit
// get(kotlin.Int): kotlin.Number
// hashCode(): kotlin.Int
// hashCode(): kotlin.Int
// hashCode(): kotlin.Int
// hashCode(): kotlin.Int
// hashCode(): kotlin.Int
// hashCode(): kotlin.Int
// hashCode(): kotlin.Int
// indexOf(kotlin.Number): kotlin.Int
// isEmpty(): kotlin.Boolean
// iterator(): kotlin.collections.Iterator
// javaListFun(): kotlin.collections.List
// javaListFun(): kotlin.collections.MutableCollection
// javaPrivateFun(): kotlin.Unit
// javaStrFun(): kotlin.String
// javaStrFun(): kotlin.String
// lastIndexOf(kotlin.Number): kotlin.Int
// listIterator(): kotlin.collections.ListIterator
// listIterator(kotlin.Int): kotlin.collections.ListIterator
// method1(kotlin.Int): kotlin.Unit
// method1(kotlin.Int): kotlin.Unit
// parallelStream(): java.util.stream.Stream
// size
// spliterator(): java.util.Spliterator
// stream(): java.util.stream.Stream
// subList(kotlin.Int,kotlin.Int): kotlin.collections.List
// toString(): kotlin.String
// toString(): kotlin.String
// toString(): kotlin.String
// toString(): kotlin.String
// toString(): kotlin.String
// toString(): kotlin.String
// toString(): kotlin.String
// x
// x
// END
// MODULE: lib
// FILE: JavaWithConstructorInLib.java
class JavaWithConstructorInLib {
    JavaWithConstructorInLib(int constructorArg1) {
    }
    void method1(int arg1) {}
}
// FILE: KotlinInLib.kt
class KotlinClassInLib(constructorArg1:Int) {
    fun method1(arg1: Int) {}
}
// MODULE: mainModule(lib)
// FILE: a.kt
abstract class Foo : C(), List<out Number> {
    override fun javaListFun(): List<Int> {
        throw java.lang.IllegalStateException()
    }

    fun bar(): Boolean {
        return false
    }

    fun baz(input: String, input2: String? = null, input3: String = ""): Boolean {
        return false
    }
}

data class Data(val a: String) {
    override fun equals(other: Any?): Boolean {
        return false
    }
}

// FILE: C.java
import java.util.Collection;

class C {
    public int aFromC = 1;
    private int bFromC = 2;
    protected int cFromC = 3;
    private void javaPrivateFun() {

    }

    protected Collection<Integer> javaListFun() {
        return Arrays.asList(1,2,3)
    }

    public String javaStrFun() {
        return "str"
    }
}

// FILE: KotlinInterfaceWithProperty.kt
interface KotlinInterfaceWithProperty {
    var x:Int
}

// FILE: JavaImplOfKotlinInterface.java
class JavaImplOfKotlinInterface implements KotlinInterfaceWithProperty {
    public int getX() {
        return 1;
    }
    public void setX(int value) {
    }
}
// FILE: JavaWithConstructor.java
class JavaWithConstructor {
    JavaWithConstructor(int constructorArg1) {
    }
    void method1(int arg1) {}
}