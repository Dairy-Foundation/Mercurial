package dev.frozenmilk.dairy.mercurial.continuations

interface Continuation : IntoContinuation {
    val stackTrace: Array<StackTraceElement>?
    fun apply(): Continuation
    override fun intoContinuation() = this

    companion object {
        @JvmStatic
        @get:JvmName("halt")
        val halt: Continuation = object : Continuation {
            override val stackTrace = null
            override fun apply() = halt
            override fun toString() = "halt"
        }
    }
}