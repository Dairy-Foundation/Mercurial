package dev.frozenmilk.dairy.mercurial

import dev.frozenmilk.util.collections.Cons
import org.jetbrains.annotations.Contract

object Tracing {
    var trace: StackTraceElement? by ThreadLocal()

    operator fun StackTraceElement?.plus(trace: Cons<StackTraceElement>?) =
        if (this === null) trace
        else Cons.cons(this, trace)

    /**
     * captures a trace, if not yet captured
     */
    @JvmStatic
    @Contract(pure = true)
    inline fun <T> traced(f: (trace: StackTraceElement?) -> T) = run {
        if (Mercurial.stackTraces) {
            val currentTrace = Tracing.trace
            if (currentTrace === null) {
                val throwable = Throwable()
                val trace = throwable.stackTrace[1]
                Tracing.trace = trace
                try {
                    f(trace)
                } finally {
                    Tracing.trace = null
                }
            } else f(currentTrace)
        } else f(null)
    }

    /**
     * removes the current trace for the contents
     */
    @JvmStatic
    @Contract(pure = true)
    fun <T> untraced(f: () -> T) = run {
        if (Mercurial.stackTraces) {
            val currentTrace = Tracing.trace
            try {
                Tracing.trace = null
                f()
            } finally {
                Tracing.trace = currentTrace
            }
        } else f()
    }
}
