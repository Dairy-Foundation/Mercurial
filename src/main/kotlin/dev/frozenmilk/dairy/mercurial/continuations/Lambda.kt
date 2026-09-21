package dev.frozenmilk.dairy.mercurial.continuations

import dev.frozenmilk.dairy.mercurial.continuations.Continuation.Builder
import dev.frozenmilk.dairy.mercurial.environments.SpaghettiStack
import dev.frozenmilk.dairy.mercurial.getValue
import dev.frozenmilk.dairy.mercurial.setValue

sealed class Lambda<SELF : Lambda<SELF>> {
    companion object {
        @JvmStatic
        var spawning: SpaghettiStack? by ThreadLocal.withInitial { null }
    }
    internal abstract fun formals(formals: Builder<*>): SELF
    abstract val fn: Continuation.Function

    @Suppress("UNCHECKED_CAST")
    open fun spawnable(): SELF = run {
        val _ = fn
        this as SELF
    }
}
