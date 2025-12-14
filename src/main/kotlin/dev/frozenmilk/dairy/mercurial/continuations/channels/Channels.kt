package dev.frozenmilk.dairy.mercurial.continuations.channels

import dev.frozenmilk.dairy.mercurial.continuations.Closure
import dev.frozenmilk.dairy.mercurial.continuations.Continuation
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.exec
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.letrecStrict
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.sequence
import dev.frozenmilk.dairy.mercurial.continuations.FactoryClosure
import dev.frozenmilk.dairy.mercurial.continuations.Fiber
import dev.frozenmilk.dairy.mercurial.continuations.registers.ValRegister
import dev.frozenmilk.util.collections.Cons
import dev.frozenmilk.util.collections.Q
import java.util.function.Supplier

object Channels {
    fun interface RecvScopeClosure<T> {
        fun bind(messageRegister: ValRegister<T>): Closure
    }

    @JvmStatic
    fun <T> recvPoll(
        from: Supplier<out Receiver<T>>,
        to: RecvScopeClosure<T>,
        fail: Closure,
    ): Closure = run {
        val messageRegister = ValRegister<T>()

        object : FactoryClosure() {
            override fun close(
                name: String?,
                k: Continuation,
            ) = run {
                val to = sequence(
                    to.bind(messageRegister),
                    exec { Fiber.Registers.DELETE(messageRegister) },
                ).close(name, k)

                val fail = fail.close(name, k)

                Continuation(name ?: "recv-poll") {
                    val from = from.get()
                    if (from.empty) fail
                    else {
                        Fiber.Registers.CREATE(messageRegister, from.take())
                        to
                    }
                }
            }
        }
    }

    @JvmStatic
    fun <T> recv(
        from: Supplier<out Receiver<T>>,
        to: RecvScopeClosure<T>,
    ) = letrecStrict { self ->
        recvPoll(from, to, self)
    }

    @JvmStatic
    fun <T> sendPoll(
        from: Supplier<out T>,
        to: Supplier<out Sender<in T>>,
        fail: Closure,
    ): Closure = object : FactoryClosure() {
        override fun close(
            name: String?,
            k: Continuation,
        ) = run {
            val fail = fail.close(name, k)
            Continuation(name ?: "send") {
                val to = to.get()
                if (to.full) fail
                else {
                    to.send(from.get())
                    k
                }
            }
        }
    }

    @JvmStatic
    fun <T> send(
        from: Supplier<out T>,
        to: Supplier<out Sender<in T>>,
    ) = letrecStrict { self ->
        sendPoll(from, to, self)
    }

    @JvmStatic
    fun <T> transfer(
        from: Supplier<out Receiver<out T>>,
        to: Supplier<out Sender<in T>>,
        fail: Closure,
    ): Closure = object : FactoryClosure() {
        override fun close(
            name: String?,
            k: Continuation,
        ) = run {
            val fail = fail.close(name, k)
            Continuation(name ?: "send") {
                val from = from.get()
                val to = to.get()
                if (from.empty || to.full) fail
                else {
                    to.send(from.take())
                    k
                }
            }
        }
    }

    @JvmStatic
    fun <T> transfer(
        from: Supplier<out Receiver<out T>>,
        to: Supplier<out Sender<in T>>,
    ) = letrecStrict { self ->
        transfer(from, to, self)
    }

    @JvmStatic
    fun <T> oneshot(): Channel<T> = run {
        var state: Cons<T>? = null

        val tx = object : Sender<T> {
            override val full
                get() = state != null

            override fun send(value: T) {
                check(state == null) { "attempted to send to a full channel" }
                state = Cons.cons(value, null)
            }
        }

        val rx = object : Receiver<T> {
            override val empty
                get() = state == null

            override fun take() =
                checkNotNull(state) { "attempted to take from an empty channel" }.car
        }

        Channel(tx, rx)
    }

    @JvmStatic
    fun <T> single(): Channel<T> = run {
        val state = Q<T>()

        val tx = object : Sender<T> {
            override val full = false
            override fun send(value: T) = state.append(value)
        }

        val rx = object : Receiver<T> {
            override val empty
                get() = state.empty()

            override fun take() = state.pop()
        }

        Channel(tx, rx)
    }
}