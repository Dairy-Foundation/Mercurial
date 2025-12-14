package dev.frozenmilk.dairy.mercurial.continuations

fun interface Closure : IntoContinuation {
    fun close(
        name: String?,
        k: Continuation,
    ): Continuation

    fun close(name: String?) = close(name, Continuation.halt)

    fun close(k: Continuation) = close(null, k)

    fun close() = close(Continuation.halt)
    override fun intoContinuation() = close()

    fun named(name: String, fallback: Boolean) = named(this, name, fallback)
    fun named(name: String) = named(this, name)

    companion object {
        @JvmOverloads
        @JvmStatic
        fun named(closure: Closure, name: String, fallback: Boolean = false) = run {
            val named = name
            if (fallback) Closure { name, k -> closure.close(name ?: named, k) }
            else Closure { name, k -> closure.close(named, k) }
        }
    }
}