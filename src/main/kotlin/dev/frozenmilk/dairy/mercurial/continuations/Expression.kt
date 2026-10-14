package dev.frozenmilk.dairy.mercurial.continuations

import dev.frozenmilk.dairy.mercurial.Tracing.traced
import dev.frozenmilk.dairy.mercurial.continuations.Continuation.Builder
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.value
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.noop
import dev.frozenmilk.dairy.mercurial.environments.Reference
import dev.frozenmilk.dairy.mercurial.getValue
import dev.frozenmilk.dairy.mercurial.setValue
import dev.frozenmilk.dairy.mercurial.Tracing.untraced

import java.util.function.BooleanSupplier
import java.util.function.DoubleSupplier
import java.util.function.IntSupplier
import java.util.function.Supplier

class Expression<T> : LambdaBuilder<Return<T>> {
    private companion object {
        var scope: Expression<*>? by ThreadLocal.withInitial { null }
    }

    private val _body: Lazy<Builder<T>>

    private fun compileActual() = run {
        if (function.body === Unresolved) {
            function.body = Resolving
            // TODO: set up scope again, so that lazier constructions know they are in a scope
            val scope = scope
            try {
                Expression.scope = this
                function.body = _body.value.compile(null, Continuation.Halt)
                function.capturing = enclosingScope !== null
            } finally {
                Expression.scope = scope
            }
        }
        function
    }

    private val enclosingScope: Expression<*>? = scope
    override val lambda: Return<T> = Return(
        noop,
        ::compileActual,
    )

    constructor(parent: LambdaBuilder<*>, f: Expression<T>.() -> Builder<T>) : super(parent) {
        _body = lazy {
            val scope = scope
            try {
                Expression.scope = this
                // run expression
                val expression = untraced { f() }
                // determine body
                state.then(expression)
            } finally {
                Expression.scope = scope
            }
        }
    }

    constructor(f: Expression<T>.(self: Builder<T>) -> Builder<T>) : super(null) {
        _body = lazy {
            val scope = scope
            try {
                Expression.scope = this
                // run expression
                val expression = untraced { f(lambda) }
                // determine body
                Builder.Then(state, null, expression)
            } finally {
                Expression.scope = scope
            }
        }
    }

    private var state: Builder<*> = noop

    //
    // io
    //

    fun run(builder: Builder<*>) = traced {
        state = state.then(builder)
    }

    fun runExec(f: Runnable) = traced {
        state = state.thenExec(f)
    }

    @JvmSynthetic
    operator fun Builder<*>.not() = traced {
        state = state.then(this)
    }

    //
    // references
    //

    fun <U> o(value: Builder<U>): Reference.O<U> = traced {
        Reference.O<U>(function, function.oSize++).also {
            state = state.then(value.then(Continuation.Store.Stack.o))
        }
    }

    fun <U> o(f: Supplier<U>) = traced {
        o(value.o(f))
    }

    fun d(value: Builder<Double>) = traced {
        Reference.D(function, function.dSize++).also {
            state = state.then(value.then(Continuation.Store.Stack.d))
        }
    }

    fun d(f: DoubleSupplier) = traced {
        d(value.d(f))
    }

    fun i(value: Builder<Int>) = traced {
        Reference.I(function, function.iSize++).also {
            state = state.then(value.then(Continuation.Store.Stack.i))
        }
    }

    fun i(f: IntSupplier) = traced {
        i(value.i(f))
    }

    fun b(value: Builder<Boolean>) = traced {
        Reference.B(function, function.bSize++).also {
            state = state.then(value.then(Continuation.Store.Stack.b))
        }
    }

    fun b(f: BooleanSupplier) = traced {
        b(value.b(f))
    }
}
