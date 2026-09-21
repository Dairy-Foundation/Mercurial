package dev.frozenmilk.dairy.mercurial.processes

import dev.frozenmilk.dairy.mercurial.Mercurial
import dev.frozenmilk.dairy.mercurial.continuations.Continuation
import dev.frozenmilk.dairy.mercurial.continuations.Continuation.Halt
import org.jetbrains.annotations.Contract
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit
import kotlin.time.TimeMark

sealed class Timer<T>(
    val end: TimeMark,
    var message: T,
) {
    protected abstract val k: Continuation
    private val spawnable = Spawnable.Builder<T>(k)
    val fiber = spawnable.spawn()
    val remaining: Duration
        get() = -end.elapsedNow()
    val remainingSeconds
        get() = remaining.toDouble(DurationUnit.SECONDS)
    fun cancel() = fiber.exit(ExitReason.Interrupt)

    companion object {
        @JvmStatic
        @Contract(pure = true)
        fun <T> start(
            duration: Duration,
            destination: Channel<in Timer<T>>,
            message: T,
        ): Timer<T> = Start(
            Mercurial.timeSource.markNow() + duration,
            destination,
            message,
        )

        @JvmStatic
        @Contract(pure = true)
        fun <T> start(
            seconds: Double,
            destination: Channel<in Timer<T>>,
            message: T,
        ): Timer<T> = Start(
            Mercurial.timeSource.markNow() + seconds.seconds,
            destination,
            message,
        )

        @JvmStatic
        @Contract(pure = true)
        fun <T : Any> sendAfter(
            duration: Duration,
            destination: Channel<in T>,
            message: T,
        ): Timer<T> = SendAfter(
            Mercurial.timeSource.markNow() + duration,
            destination,
            message,
        )

        @JvmStatic
        @Contract(pure = true)
        fun <T : Any> sendAfter(
            seconds: Double,
            destination: Channel<in T>,
            message: T,
        ): Timer<T> = SendAfter(
            Mercurial.timeSource.markNow() + seconds.seconds,
            destination,
            message,
        )
    }

    private class Start<T>(
        end: TimeMark,
        val destination: Channel<in Timer<T>>,
        message: T,
    ) : Timer<T>(
        end,
        message,
    ) {
        override val k = object : Continuation {
            override fun eval() = run {
                if (end.hasPassedNow()) {
                    destination.send(this@Start)
                    Fiber.current.returnRegister.o = message
                    Halt
                } else this
            }
        }
    }

    private class SendAfter<T : Any>(
        end: TimeMark,
        val destination: Channel<in T>,
        message: T,
    ) : Timer<T>(
        end,
        message,
    ) {
        override val k = object : Continuation {
            override fun eval() = run {
                if (end.hasPassedNow()) {
                    destination.send(message)
                    Fiber.current.returnRegister.o = message
                    Halt
                } else this
            }
        }
    }
}
