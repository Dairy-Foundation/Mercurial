package dev.frozenmilk.dairy.mercurial.continuations

import dev.frozenmilk.dairy.mercurial.Tracing.plus
import dev.frozenmilk.dairy.mercurial.Tracing.traced
import dev.frozenmilk.dairy.mercurial.environments.Reference

@Suppress("LocalVariableName")
@LambdaBuilderDSLMarker
sealed class LambdaBuilder<L : Lambda<L>>(
    protected val parent: LambdaBuilder<*>?,
) {
    object Unresolved : Continuation {
        override fun eval() = throw IllegalStateException()
    }

    object Resolving : Continuation {
        override fun eval() = throw IllegalStateException()
    }

    protected val actual: Continuation.Function.Actual =
        parent?.actual ?: traced { trace -> Continuation.Function.Actual(trace + null, Unresolved) }
    abstract val lambda: L
    private var child: LambdaBuilder<*>? = null

    sealed class ParameterBuilder<L : Lambda<L>>(
        parent: LambdaBuilder<*>?,
    ) : LambdaBuilder<L>(parent) {
        init {
            if (parent?.child !== null) throw IllegalStateException("Cannot bind two bodies to a function parameter")
            parent?.child = this
        }
        fun <T, K : Lambda<K>> o(f: O<T,*>.(Reference.O<T>) -> LambdaBuilder<K>) = O(this, f)
        fun <K : Lambda<K>> d(f: D<*>.(Reference.D) -> LambdaBuilder<K>) = D(this, f)
        fun <K : Lambda<K>> i(f: I<*>.(Reference.I) -> LambdaBuilder<K>) = I(this, f)
        fun <K : Lambda<K>> b(f: B<*>.(Reference.B) -> LambdaBuilder<K>) = B(this, f)
        fun <T> body(f: Expression<T>.() -> Continuation.Builder<T>) = Expression(this, f)
    }

    class O<T, K : Lambda<K>> : ParameterBuilder<Parameter.O<T, K>> {
        override val lambda: Parameter.O<T, K>
        val reference = this.actual.let { _fn ->
            Reference.O<T>(_fn, _fn.oSize++)
        }

        constructor(
            parent: LambdaBuilder<*>,
            f: O<T, *>.(Reference.O<T>) -> LambdaBuilder<K>,
        ) : super(parent) {
            lambda = Parameter.O(f(this, reference).lambda)
        }

        constructor(f: O<T, *>.(self: Parameter.O<T, K>, Reference.O<T>) -> LambdaBuilder<K>) : super(null) {
            lambda = Parameter.O { self -> f(this, self, reference).lambda }
        }
    }

    class D<K : Lambda<K>> : ParameterBuilder<Parameter.D<K>> {
        override val lambda: Parameter.D<K>
        val reference = this.actual.let { _fn ->
            Reference.D(_fn, _fn.dSize++)
        }

        constructor(
            parent: LambdaBuilder<*>,
            f: D<*>.(Reference.D) -> LambdaBuilder<K>,
        ) : super(parent) {
            lambda = Parameter.D(f(this, reference).lambda)
        }

        constructor(f: D<*>.(self: Parameter.D<K>, Reference.D) -> LambdaBuilder<K>) : super(null) {
            lambda = Parameter.D { self -> f(this, self, reference).lambda }
        }
    }

    class I<K : Lambda<K>> : ParameterBuilder<Parameter.I<K>> {
        override val lambda: Parameter.I<K>
        val reference = this.actual.let { _fn ->
            Reference.I(_fn, _fn.iSize++)
        }

        constructor(
            parent: LambdaBuilder<*>,
            f: I<*>.(Reference.I) -> LambdaBuilder<K>,
        ) : super(parent) {
            lambda = Parameter.I(f(this, reference).lambda)
        }

        constructor(f: I<*>.(self: Parameter.I<K>, Reference.I) -> LambdaBuilder<K>) : super(null) {
            lambda = Parameter.I { self -> f(this, self, reference).lambda }
        }
    }

    class B<K : Lambda<K>> : ParameterBuilder<Parameter.B<K>> {
        override val lambda: Parameter.B<K>
        val reference = this.actual.let { _fn ->
            Reference.B(_fn, _fn.bSize++)
        }

        constructor(
            parent: LambdaBuilder<*>,
            f: B<*>.(Reference.B) -> LambdaBuilder<K>,
        ) : super(parent) {
            lambda = Parameter.B(f(this, reference).lambda)
        }

        constructor(f: B<*>.(self: Parameter.B<K>, Reference.B) -> LambdaBuilder<K>) : super(null) {
            lambda = Parameter.B { self -> f(this, self, reference).lambda }
        }
    }
}
