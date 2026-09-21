package dev.frozenmilk.dairy.mercurial.continuations

import dev.frozenmilk.dairy.mercurial.continuations.Continuation.Builder
import dev.frozenmilk.dairy.mercurial.continuations.Continuation.IfElse
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.value
import dev.frozenmilk.dairy.mercurial.Tracing.traced
import dev.frozenmilk.util.collections.Cons
import org.jetbrains.annotations.Contract
import java.util.function.BooleanSupplier

data class IfThen<T>(
    val prior: IfThen<T>?,
    val trace: StackTraceElement?,
    val cond: Builder<Boolean>,
    val t: Builder<T>,
) : Builder<Any?> {
    @Contract(pure = true)
    fun elseIfThen(ifThen: IfThen<T>): IfThen<T> =
        IfThen(
            ifThen.prior?.let(::elseIfThen) ?: this,
            ifThen.trace,
            ifThen.cond,
            ifThen.t,
        )

    @Contract(pure = true)
    fun elseIfThen(
        cond: BooleanSupplier,
        t: Builder<T>,
    ) = traced { trace ->
        IfThen(
            this,
            trace,
            value.b(cond),
            t,
        )
    }

    @Contract(pure = true)
    fun elseIfThen(
        cond: Builder<Boolean>,
        t: Builder<T>,
    ) = traced { trace ->
        IfThen(
            this,
            trace,
            cond,
            t,
        )
    }

    @Contract(pure = true)
    fun elseThen(f: Builder<T>): Builder<T> = traced { trace ->
        val f = cond.then(IfElse.Builder(trace, t, f))
        prior?.elseThen(f) ?: f
    }

    fun compile(
        f: Continuation,
        trace: Cons<StackTraceElement>?,
        k: Continuation,
    ): Continuation = run {
        val t = t.compile(trace, k)

        val f = if (t == f) cond.compile(trace, t)
        else cond.compile(trace, IfElse(trace, t, f))

        prior?.compile(f, trace, k) ?: f
    }

    //override fun compile(k: Continuation) = compile(k, k)
    override fun compile(
        trace: Cons<StackTraceElement>?,
        k: Continuation,
    ) = compile(k, trace, k)
}
