package a.b.c

class A<T> : I {
    val a: Int = 1

    fun toBeShadowed() {
        println("Shadowed!")
    }
}

interface I {
    fun hi()
}

fun main() {
    println(A<Int>().hi())
    // println(A().b)
}
