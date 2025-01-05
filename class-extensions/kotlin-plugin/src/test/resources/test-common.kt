package a.b.c

class A : I {
    val a: Int = 1

    fun toBeShadowed() {
        println("Shadowed!")
    }
}

interface I {
    fun hi()
}

fun main() {
    println(A().hi())
    // println(A().b)
}
