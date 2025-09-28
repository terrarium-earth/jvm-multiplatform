expect fun expectFunction(): String

expect fun expectFunctionUnit(something: String)

expect val expectProperty: Int

expect var expectMutable: Int

expect annotation class ExpectAnnotation(val meow: Int)

expect class ExpectClass {

    val property: Int

    var mutable: Int

    fun functon()
}

expect interface ExpectItf {

    val property: Int

    var mutable: Int

    fun func()
}

expect object ExpectObj {

    val property: Int

    var mutable: Int

    fun objectFunc(meow: String)
}

expect enum class ExpectEnum {
    ONE, TWO;

    val property: Int

    fun function()
}