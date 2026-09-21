package dev.frozenmilk.dairy.mercurial.continuations

import dev.frozenmilk.dairy.mercurial.continuations.Continuation.Builder
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.loop
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.noop
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.value
import dev.frozenmilk.dairy.mercurial.Tracing.traced
import dev.frozenmilk.util.collections.Cons
import java.util.function.BooleanSupplier

@ExposedCopyVisibility
data class Command private constructor(
    private val init: Builder<*>,
    private val exec: Builder<*>,
    private val finished: Builder<Boolean>,
    private val end: Builder<*>,
) : Builder<Unit> {
    companion object {
        private val TRUE = value.b { true }

        @JvmStatic
        val DEFAULT = Command(
            noop,
            noop,
            TRUE,
            noop,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private val inner by lazy {
        init.then(
            if (finished === TRUE) end
            else loop(finished, exec).then(end)
        )
    }

    //override fun compile(k: Continuation) = inner.compile(k)
    override fun compile(
        trace: Cons<StackTraceElement>?,
        k: Continuation,
    ) = inner.compile(trace, k)

    fun init(init: Builder<*>) = Command(
        init,
        exec,
        finished,
        end,
    )

    fun init(f: Runnable) = traced {
        init(Continuations.exec(f))
    }

    fun exec(exec: Builder<*>) = Command(
        init,
        exec,
        finished,
        end,
    )

    fun exec(f: Runnable) = traced {
        exec(Continuations.exec(f))
    }

    fun finished(finished: Builder<Boolean>) = Command(
        init,
        exec,
        finished,
        end,
    )

    fun finished(finished: BooleanSupplier) = traced {
        finished(value.b(finished))
    }

    fun end(end: Builder<*>) = Command(
        init,
        exec,
        finished,
        end,
    )

    fun end(f: Runnable) = traced {
        end(Continuations.exec(f))
    }
}
