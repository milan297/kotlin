fun test() {
    JavaClass.getNull().toBoolean() // ISE in runtime
    "123".toBoolean()               // OK
    null.toBoolean()                // Compilation error, no inspection message

    JavaClass.getNull().notInline() // OK
    JavaClass.getMy().nonExtensionInlineFun() // OK
}

fun String.notInline() {}

class My {
    inline fun nonExtensionInlineFun() {}
}