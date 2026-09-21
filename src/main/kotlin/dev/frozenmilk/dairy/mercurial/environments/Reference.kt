package dev.frozenmilk.dairy.mercurial.environments

import dev.frozenmilk.dairy.mercurial.continuations.Continuation
import dev.frozenmilk.dairy.mercurial.processes.Fiber
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.DoubleConsumer
import java.util.function.DoubleSupplier
import java.util.function.IntConsumer
import java.util.function.IntSupplier
import java.util.function.Supplier
import kotlin.reflect.KProperty

sealed class Reference(
    val actual: Continuation.Function.Actual,
    val offset: Int,
) {
    @get:JvmName("name")
    @set:JvmName("name")
    var name: String? = null

    private tailrec fun frame(frame: SpaghettiStack?): SpaghettiStack =
        if (frame === null) throw IllegalStateException("Attempted to access illegal reference $this")
        else if (frame.actual === actual) frame
        else frame(frame.captured)

    protected val frame
        get() = frame(Fiber.current.frame)

    @Suppress("UNCHECKED_CAST")
    class O<T>(
        actual: Continuation.Function.Actual,
        offset: Int,
    ) : Reference(actual, offset), Supplier<T>, Consumer<T> {
        fun named(name: String) = this.also {
            this.name = name
        }

        override fun get() = frame.oData[offset] as T
        fun set(value: T) {
            frame.oData[offset] = value
        }

        override fun accept(t: T) {
            frame.oData[offset] = t
        }

        @JvmSynthetic
        operator fun invoke() = frame.oData[offset] as T

        @JvmSynthetic
        operator fun invoke(value: T) {
            frame.oData[offset] = value
        }

        @JvmSynthetic
        operator fun getValue(
            thisRef: Any?,
            property: KProperty<*>,
        ) = run {
            name = property.name
            frame.oData[offset] as T
        }

        @JvmSynthetic
        operator fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: T,
        ) = run {
            name = property.name
            frame.oData[offset] = value
        }
    }

    class D(
        actual: Continuation.Function.Actual,
        offset: Int,
    ) : Reference(actual, offset), DoubleSupplier, DoubleConsumer {
        fun named(name: String) = this.also {
            this.name = name
        }

        fun get() = frame.dData[offset]
        override fun getAsDouble() = frame.dData[offset]
        fun set(value: Double) {
            frame.dData[offset] = value
        }

        override fun accept(value: Double) {
            frame.dData[offset] = value
        }

        @JvmSynthetic
        operator fun invoke() = frame.dData[offset]

        @JvmSynthetic
        operator fun invoke(value: Double) {
            frame.dData[offset] = value
        }

        @JvmSynthetic
        operator fun getValue(
            thisRef: Any?,
            property: KProperty<*>,
        ) = run {
            name = property.name
            frame.dData[offset]
        }

        @JvmSynthetic
        operator fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: Double,
        ) = run {
            name = property.name
            frame.dData[offset] = value
        }
    }

    class I(
        actual: Continuation.Function.Actual,
        offset: Int,
    ) : Reference(actual, offset), IntSupplier, IntConsumer {
        fun named(name: String) = this.also {
            this.name = name
        }

        fun get() = frame.iData[offset]
        override fun getAsInt() = frame.iData[offset]
        fun set(value: Int) {
            frame.iData[offset] = value
        }

        override fun accept(value: Int) {
            frame.iData[offset] = value
        }

        @JvmSynthetic
        operator fun invoke() = frame.iData[offset]

        @JvmSynthetic
        operator fun invoke(value: Int) {
            frame.iData[offset] = value
        }

        @JvmSynthetic
        operator fun getValue(
            thisRef: Any?,
            property: KProperty<*>,
        ) = run {
            name = property.name
            frame.iData[offset]
        }

        @JvmSynthetic
        operator fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: Int,
        ) = run {
            name = property.name
            frame.iData[offset] = value
        }
    }

    class B(
        actual: Continuation.Function.Actual,
        offset: Int,
    ) : Reference(actual, offset), BooleanSupplier {
        fun named(name: String) = this.also {
            this.name = name
        }

        fun get() = frame.bData[offset]
        override fun getAsBoolean() = frame.bData[offset]
        fun set(value: Boolean) {
            frame.bData[offset] = value
        }

        @JvmSynthetic
        operator fun invoke() = frame.bData[offset]

        @JvmSynthetic
        operator fun invoke(value: Boolean) {
            frame.bData[offset] = value
        }

        @JvmSynthetic
        operator fun getValue(
            thisRef: Any?,
            property: KProperty<*>,
        ) = run {
            name = property.name
            frame.bData[offset]
        }

        @JvmSynthetic
        operator fun setValue(
            thisRef: Any?,
            property: KProperty<*>,
            value: Boolean,
        ) = run {
            name = property.name
            frame.bData[offset] = value
        }
    }
}
