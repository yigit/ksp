// WITH_RUNTIME
// TEST PROCESSOR: AsMemberOfProcessor
// EXPECTED:
// Child1!!
// intType: kotlin.Int!!
// baseTypeArg1: kotlin.Int!!
// baseTypeArg2: kotlin.String?
// typePair: kotlin.Pair!!<kotlin.String?, kotlin.Int!!>
// extensionProperty: kotlin.String?
// returnInt: () -> kotlin.Int!!
// returnArg1: () -> kotlin.Int!!
// returnArg1Nullable: () -> kotlin.Int?
// returnArg2: () -> kotlin.String?
// returnArg2Nullable: () -> kotlin.String?
// receiveArgs: (kotlin.Int?, kotlin.Int!!, kotlin.String?) -> kotlin.Unit!!
// receiveArgsPair: (kotlin.Pair!!<kotlin.Int!!, kotlin.String?>, kotlin.Pair?<kotlin.Int?, kotlin.String?>) -> kotlin.Unit!!
// functionArgType: <BaseTypeArg1: kotlin.Any?>(Base.functionArgType.BaseTypeArg1?) -> kotlin.String?
// functionArgTypeWithBounds: <in T: kotlin.Int!!>(Base.functionArgTypeWithBounds.T?) -> kotlin.String?
// extensionFunction: kotlin.Int!!.() -> kotlin.Int?
// Child2!!<kotlin.Any?>
// intType: kotlin.Int!!
// baseTypeArg1: kotlin.Any?
// baseTypeArg2: kotlin.Any?
// typePair: kotlin.Pair!!<kotlin.Any?, kotlin.Any?>
// extensionProperty: kotlin.Any?
// returnInt: () -> kotlin.Int!!
// returnArg1: () -> kotlin.Any?
// returnArg1Nullable: () -> kotlin.Any?
// returnArg2: () -> kotlin.Any?
// returnArg2Nullable: () -> kotlin.Any?
// receiveArgs: (kotlin.Int?, kotlin.Any?, kotlin.Any?) -> kotlin.Unit!!
// receiveArgsPair: (kotlin.Pair!!<kotlin.Any?, kotlin.Any?>, kotlin.Pair?<kotlin.Any?, kotlin.Any?>) -> kotlin.Unit!!
// functionArgType: <BaseTypeArg1: kotlin.Any?>(Base.functionArgType.BaseTypeArg1?) -> kotlin.Any?
// functionArgTypeWithBounds: <in T: kotlin.Any?>(Base.functionArgTypeWithBounds.T?) -> kotlin.Any?
// extensionFunction: kotlin.Any?.() -> kotlin.Any?
// Child2!!<kotlin.String!!>
// intType: kotlin.Int!!
// baseTypeArg1: kotlin.String!!
// baseTypeArg2: kotlin.String?
// typePair: kotlin.Pair!!<kotlin.String?, kotlin.String!!>
// extensionProperty: kotlin.String?
// returnInt: () -> kotlin.Int!!
// returnArg1: () -> kotlin.String!!
// returnArg1Nullable: () -> kotlin.String?
// returnArg2: () -> kotlin.String?
// returnArg2Nullable: () -> kotlin.String?
// receiveArgs: (kotlin.Int?, kotlin.String!!, kotlin.String?) -> kotlin.Unit!!
// receiveArgsPair: (kotlin.Pair!!<kotlin.String!!, kotlin.String?>, kotlin.Pair?<kotlin.String?, kotlin.String?>) -> kotlin.Unit!!
// functionArgType: <BaseTypeArg1: kotlin.Any?>(Base.functionArgType.BaseTypeArg1?) -> kotlin.String?
// functionArgTypeWithBounds: <in T: kotlin.String!!>(Base.functionArgTypeWithBounds.T?) -> kotlin.String?
// extensionFunction: kotlin.String!!.() -> kotlin.String?
// List#get
// listOfStrings (kotlin.Int!!) -> kotlin.String!!
// setOfStrings (kotlin.Int!!) -> kotlin.collections.List.E?
// Set#contains
// listOfStrings (kotlin.collections.Set.E?) -> kotlin.Boolean!!
// setOfStrings (kotlin.String!!) -> kotlin.Boolean!!
// JavaChild1!!
// intType: kotlin.Int!!
// typeArg1: kotlin.String
// typeArg2: kotlin.Int
// returnArg1: () -> kotlin.Int
// receiveArgs: (kotlin.String, kotlin.Int, kotlin.Int!!) -> kotlin.Unit!!
// methodArgType: <BaseTypeArg1: kotlin.Any>(JavaBase.methodArgType.BaseTypeArg1?, kotlin.Int) -> kotlin.Unit!!
// methodArgTypeWithBounds: <T: kotlin.String>(JavaBase.methodArgTypeWithBounds.T?) -> kotlin.Unit!!
// END
// FILE: Input.kt
open class Base<BaseTypeArg1, BaseTypeArg2> {
    val intType: Int = 0
    val baseTypeArg1: BaseTypeArg1 = TODO()
    val baseTypeArg2: BaseTypeArg2 = TODO()
    val typePair: Pair<BaseTypeArg2, BaseTypeArg1>  = TODO()
    fun returnInt():Int = TODO()
    fun returnArg1(): BaseTypeArg1 = TODO()
    fun returnArg1Nullable(): BaseTypeArg1? = TODO()
    fun returnArg2(): BaseTypeArg2 = TODO()
    fun returnArg2Nullable(): BaseTypeArg2? = TODO()
    fun receiveArgs(intArg:Int?, arg1: BaseTypeArg1, arg2:BaseTypeArg2):Unit = TODO()
    fun receiveArgsPair(
        pairs: Pair<BaseTypeArg1, BaseTypeArg2>,
        pairNullable: Pair<BaseTypeArg1?, BaseTypeArg2?>?,
    ):Unit = TODO()
    // intentional type argument name conflict here to ensure it does not get replaced by mistake
    fun <BaseTypeArg1> functionArgType(t:BaseTypeArg1?): BaseTypeArg2 = TODO()
    fun <in T: BaseTypeArg1> functionArgTypeWithBounds(t:T?): BaseTypeArg2 = TODO()
    fun BaseTypeArg1.extensionFunction():BaseTypeArg1? = TODO()
    val BaseTypeArg2.extensionProperty:BaseTypeArg2? = TODO()
}

open class Child1 : Base<Int, String?>() {

}

open class Child2<ChildTypeArg1> : Base<ChildTypeArg1, ChildTypeArg1?>() {

}
val child2WithString: Child2<String> = TODO()
val listOfStrings: List<String> = TODO()
val setOfStrings: Set<String> = TODO()

// FILE: JavaInput.java
class JavaBase<BaseTypeArg1, BaseTypeArg2> {
    int intType;
    BaseTypeArg1 typeArg1;
    BaseTypeArg2 typeArg2;
    BaseTypeArg2 returnArg1() {
        return null;
    }
    void receiveArgs(BaseTypeArg1 arg1, BaseTypeArg2 arg2, int intArg) {
    }
    <BaseTypeArg1> void methodArgType(BaseTypeArg1 arg1, BaseTypeArg2 arg2) {
    }
    <T extends BaseTypeArg1> void methodArgTypeWithBounds(T arg1) {
    }
}

class JavaChild1 extends JavaBase<String, Integer> {

}