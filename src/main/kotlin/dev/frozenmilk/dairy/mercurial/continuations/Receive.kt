package dev.frozenmilk.dairy.mercurial.continuations

import dev.frozenmilk.dairy.mercurial.continuations.Continuation.Builder
import dev.frozenmilk.dairy.mercurial.continuations.Continuation.Receive
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.exec
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.expression
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.function
import dev.frozenmilk.dairy.mercurial.continuations.PredicateChain.Companion.link
import dev.frozenmilk.dairy.mercurial.environments.Reference
import dev.frozenmilk.dairy.mercurial.processes.Channel
import dev.frozenmilk.dairy.mercurial.processes.Fiber
import dev.frozenmilk.util.collections.Cons
import java.util.function.BooleanSupplier
import java.util.function.Predicate
import java.util.function.Supplier
import kotlin.time.TimeMark

data class Receive<T>(
    private val chain: PredicateChain.Unterminated<Nothing?, Builder<T>>?,
    private val after: After<T>?,
) : Builder<T> {
    constructor() : this(null, null)

    data class After<T>(val timeout: Supplier<out TimeMark>, val then: Builder<T>)
    typealias Function<MSG, T> = Parameter.O<MSG, Return<T>>

    @Suppress("UNCHECKED_CAST")
    fun <MSG : Any> receive(
        channel: Supplier<out Channel<MSG>>,
        then: Function<MSG, T>,
    ) = Receive(
        chain.link(
            {
                val msg = channel.get().poll()
                if (msg !== null) Fiber.current.returnRegister.o = msg
                msg !== null
            },
            then * { Fiber.current.returnRegister.o as MSG },
        ),
        after,
    )

    fun <MSG : Any> receive(
        channel: Channel<MSG>,
        then: Function<MSG, T>,
    ) = receive({ channel }, then)

    fun <MSG : Any> receive(
        channel: Supplier<out Channel<MSG>>,
        then: Expression<*>.(Reference.O<MSG>) -> Builder<T>,
    ) = receive(channel, function { _, msg -> then(msg) })

    fun <MSG : Any> receive(
        channel: Channel<MSG>,
        then: Expression<*>.(Reference.O<MSG>) -> Builder<T>,
    ) = receive({ channel }, function { _, msg -> then(msg) })

    fun receive(
        channel: Supplier<out Channel<*>>,
        then: Builder<T>,
    ) = Receive(
        chain.link(
            { channel.get().poll() !== null },
            then,
        ),
        after,
    )

    fun receive(
        channel: Channel<*>,
        handler: Builder<T>,
    ) = receive({ channel }, handler)

    @Suppress("UNCHECKED_CAST")
    fun <MSG : Any> receive(
        channel: Supplier<out Channel<MSG>>,
        guard: Predicate<in MSG>,
        then: Function<MSG, T>,
    ) = Receive(
        chain.link(
            {
                val chan = channel.get()
                val msg = chan.poll()
                if (msg !== null) {
                    val guarded = guard.test(msg)
                    if (guarded) Fiber.current.returnRegister.o = msg
                    else chan.prepend(msg)
                    guarded
                } else false
            },
            then * { Fiber.current.returnRegister.o as MSG },
        ),
        after,
    )

    fun <MSG : Any> receive(
        channel: Channel<MSG>,
        guard: Predicate<in MSG>,
        then: Function<MSG, T>,
    ) = receive({ channel }, guard, then)

    fun <MSG : Any> receive(
        channel: Supplier<out Channel<MSG>>,
        guard: Predicate<in MSG>,
        then: Expression<*>.(Reference.O<MSG>) -> Builder<T>,
    ) = receive(channel, guard, function { _, msg -> then(msg) })

    fun <MSG : Any> receive(
        channel: Channel<MSG>,
        guard: Predicate<in MSG>,
        then: Expression<*>.(Reference.O<MSG>) -> Builder<T>,
    ) = receive({ channel }, guard, function { _, msg -> then(msg) })

    fun <MSG : Any> receive(
        channel: Supplier<out Channel<MSG>>,
        guard: Predicate<in MSG>,
        then: Builder<T>,
    ) = Receive(
        chain.link(
            {
                val chan = channel.get()
                val msg = chan.poll()
                if (msg !== null) {
                    val guarded = guard.test(msg)
                    if (!guarded) chan.prepend(msg)
                    guarded
                } else false
            },
            then,
        ),
        after,
    )

    fun <MSG : Any> receive(
        channel: Channel<MSG>,
        guard: Predicate<in MSG>,
        then: Builder<T>,
    ) = receive({ channel }, guard, then)

    fun or(guard: BooleanSupplier, then: Builder<T>) = Receive(
        chain.link(
            { guard.asBoolean },
            then,
        ),
        after,
    )

    fun or(default: Builder<T>): Builder<T> =
        if (after === null) Receive.Builder(chain.link(default))
        else exec { after.timeout.get() }.then(Receive.Builder(chain.link(default)))

    fun after(timeout: Supplier<out TimeMark>, then: Builder<T>) = Receive(
        chain,
        After(timeout, then),
    )

    private val compiler by lazy {
        if (after === null) expression { self ->
            Receive.Builder(chain.link(self))
        }
        else expression {
            val timeMark by o(after.timeout)
            Receive(
                chain.link({ timeMark.hasPassedNow() }, after.then),
                // remove after
                null,
            )
        }
    }

    //override fun compile(k: Continuation) = compiler.compile(k)
    override fun compile(
        trace: Cons<StackTraceElement>?,
        k: Continuation,
    ) = compiler.compile(trace, k)
}
