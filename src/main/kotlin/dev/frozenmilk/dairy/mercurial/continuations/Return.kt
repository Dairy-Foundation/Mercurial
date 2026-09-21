package dev.frozenmilk.dairy.mercurial.continuations

import dev.frozenmilk.dairy.mercurial.Mercurial
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.noop
import dev.frozenmilk.dairy.mercurial.environments.SpaghettiStack
import dev.frozenmilk.dairy.mercurial.processes.Fiber
import dev.frozenmilk.dairy.mercurial.processes.Spawnable
import dev.frozenmilk.util.collections.Cons

class Return<T>(
    val formals: Continuation.Builder<*>,
    private val _fn: () -> Continuation.Function,
) : Lambda<Return<T>>(), Continuation.Builder<T>, Spawnable<T> {
    override fun formals(formals: Continuation.Builder<*>) = Return<T>(formals, _fn)

    override fun compile(
        trace: Cons<StackTraceElement>?,
        k: Continuation,
    ) = Continuation.Call.Begin(
        trace,
        fn,
        k === Continuation.Halt,
        formals.compile(trace, Continuation.Call.Complete(k)),
    )

    override val fn: Continuation.Function
        get() = _fn()

    override fun spawnable(): Return<T> = super<Lambda>.spawnable()

    override fun spawn(flag: Fiber.SpawnFlag): Fiber<T> = run {
        check(formals == noop)
        val frame = spawning ?: SpaghettiStack(fn.trace, fn)
        spawning = null
        val fiber = Fiber<T>(frame)
        flag.applyTo(fiber)
        Mercurial.scheduler.schedule(fiber)
        fiber
    }
}
