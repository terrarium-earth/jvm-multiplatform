package a.b.c

import net.msrandom.classextensions.ClassExtension
import net.msrandom.classextensions.ExtensionInject
import net.msrandom.classextensions.ExtensionShadow

@ClassExtension(A::class)
class AExtension : I {
    @ExtensionShadow
    val a: Int = TODO()

/*    @ExtensionInject
    val b: Int
        get() = a*/

    @ExtensionInject
    private var c = 5

    @ExtensionShadow
    fun toBeShadowed(): Unit = TODO()

    @ExtensionInject
    override fun hi() {
        println("hi")
    }

    @ExtensionInject
    fun injected() {
        println(5)
    }
}
