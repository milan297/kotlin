// Typical true positive
inline fun String.foo() = foo(this)
fun String.notInlineFoo() = foo(this)
// Other positives
inline fun String.fooo1() = fooo()
inline fun String.fooo2() = this.fooo()
inline fun String.fooo3() = this?.foo()

// Just functions
fun foo(s: String?) {}
fun String?.fooo() {}
fun bar(s: String) {}
fun String.baz() {}

inline fun String.bar() = bar(this) // No problem
inline fun String.bazz() = baz() // No problem
inline fun String.bazzz() = this.baz() // No problem

interface My {
    inline fun String.bar() // No problem (no body)
}

inline fun String.myLength() = length // No problem (this.length)

// No problem (this.size)
inline fun <reified T> List<T>.count() {
    return size
}

// No problem (in this)
inline fun <reified T> List<T>.foo() {
    for (element in this) {}
}

// No problem (cast)
inline fun String.cast(): Any = this as Any


