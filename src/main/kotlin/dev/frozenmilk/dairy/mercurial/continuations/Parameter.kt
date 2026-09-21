package dev.frozenmilk.dairy.mercurial.continuations

import dev.frozenmilk.dairy.mercurial.continuations.Continuations.noop
import dev.frozenmilk.dairy.mercurial.environments.SpaghettiStack
import java.util.function.BooleanSupplier
import java.util.function.DoubleSupplier
import java.util.function.IntSupplier
import java.util.function.Supplier

sealed class Parameter<SELF : Parameter<SELF, K>, K : Lambda<K>> : Lambda<SELF>() {
    abstract val k: K

    final override val fn: Continuation.Function
        get() = k.fn

    open class O<T, K : Lambda<K>> : Parameter<O<T, K>, K> {
        val formals: Continuation.Builder<*>
        final override val k: K

        private constructor(formals: Continuation.Builder<*>, k: K) {
            this.formals = formals
            this.k = k
        }

        constructor(k: K) : this(noop, k)
        constructor(f: (O<T, K>) -> K) {
            formals = noop
            k = f(this)
        }
        constructor(o: O<T, K>) {
            formals = o.formals
            k = o.k
        }

        final override fun formals(formals: Continuation.Builder<*>): O<T, K> = O(formals, k)

        fun bind(value: Continuation.Builder<T>): K =
            k.formals(formals.then(value.then(Continuation.Store.Formals.o)))

        fun bind(f: Supplier<out T>) = bind(Continuations.value.o(f))

        @JvmSynthetic
        operator fun times(value: Continuation.Builder<T>) = bind(value)

        @JvmSynthetic
        operator fun times(f: Supplier<out T>) = bind(f)

        fun spawn(value: T) = run {
            check(formals == noop)
            val frame = spawning ?: SpaghettiStack(null, fn).also(::spawning::set)
            frame.oPush(value)
            k
        }

        @JvmSynthetic
        operator fun invoke(value: T) = spawn(value)
    }

    open class D<K : Lambda<K>> : Parameter<D<K>, K> {
        val formals: Continuation.Builder<*>
        final override val k: K

        private constructor(formals: Continuation.Builder<*>, k: K) {
            this.formals = formals
            this.k = k
        }

        constructor(k: K) : this(noop, k)
        constructor(f: (D<K>) -> K) {
            formals = noop
            k = f(this)
        }
        constructor(d: D<K>) {
            formals = d.formals
            k = d.k
        }

        final override fun formals(formals: Continuation.Builder<*>): D<K> = D(formals, k)

        fun bind(value: Continuation.Builder<Double>): K =
            k.formals(formals.then(value.then(Continuation.Store.Formals.d)))

        fun bind(f: DoubleSupplier) = bind(Continuations.value.d(f))

        @JvmSynthetic
        operator fun times(value: Continuation.Builder<Double>) = bind(value)

        @JvmSynthetic
        operator fun times(f: DoubleSupplier) = bind(f)

        fun spawn(value: Double) = run {
            check(formals == noop)
            val frame = spawning ?: SpaghettiStack(null, fn).also(::spawning::set)
            frame.dPush(value)
            k
        }

        @JvmSynthetic
        operator fun invoke(value: Double) = spawn(value)
    }

    open class I<K : Lambda<K>> : Parameter<I<K>, K> {
        val formals: Continuation.Builder<*>
        final override val k: K

        private constructor(formals: Continuation.Builder<*>, k: K) {
            this.formals = formals
            this.k = k
        }

        constructor(k: K) : this(noop, k)
        constructor(f: (I<K>) -> K) {
            formals = noop
            k = f(this)
        }
        constructor(i: I<K>) {
            formals = i.formals
            k = i.k
        }

        final override fun formals(formals: Continuation.Builder<*>): I<K> = I(formals, k)

        fun bind(value: Continuation.Builder<Int>): K =
            k.formals(formals.then(value.then(Continuation.Store.Formals.i)))

        fun bind(f: IntSupplier) = bind(Continuations.value.i(f))

        @JvmSynthetic
        operator fun times(value: Continuation.Builder<Int>) = bind(value)

        @JvmSynthetic
        operator fun times(f: IntSupplier) = bind(f)

        fun spawn(value: Int) = run {
            check(formals == noop)
            val frame = spawning ?: SpaghettiStack(null, fn).also(::spawning::set)
            frame.iPush(value)
            k
        }

        @JvmSynthetic
        operator fun invoke(value: Int) = spawn(value)
    }

    open class B<K : Lambda<K>> : Parameter<B<K>, K> {
        val formals: Continuation.Builder<*>
        final override val k: K

        private constructor(formals: Continuation.Builder<*>, k: K) {
            this.formals = formals
            this.k = k
        }

        constructor(k: K) : this(noop, k)
        constructor(f: (B<K>) -> K) {
            formals = noop
            k = f(this)
        }
        constructor(b: B<K>) {
            formals = b.formals
            k = b.k
        }

        final override fun formals(formals: Continuation.Builder<*>): B<K> = B(formals, k)

        fun bind(value: Continuation.Builder<Boolean>): K =
            k.formals(formals.then(value.then(Continuation.Store.Formals.b)))

        fun bind(f: BooleanSupplier) = bind(Continuations.value.b(f))

        @JvmSynthetic
        operator fun times(value: Continuation.Builder<Boolean>) = bind(value)

        @JvmSynthetic
        operator fun times(f: BooleanSupplier) = bind(f)

        fun spawn(value: Boolean) = run {
            check(formals == noop)
            val frame = spawning ?: SpaghettiStack(null, fn).also(::spawning::set)
            frame.bPush(value)
            k
        }

        @JvmSynthetic
        operator fun invoke(value: Boolean) = spawn(value)
    }
}
