package dev.frozenmilk.dairy.mercurial.processes

import dev.frozenmilk.dairy.mercurial.continuations.Continuation
import dev.frozenmilk.dairy.mercurial.processes.Channel.Companion.tryPoll
import dev.frozenmilk.util.collections.Q
import org.jetbrains.annotations.Contract
import java.util.function.Consumer

open class EventManager<EVENT : Any> : Fiberous<Nothing> {
    sealed class EventHandled<out EVENT> {
        companion object {
            @JvmStatic
            @Contract(pure = true)
            fun <EVENT> ok() = Ok as EventHandled<EVENT>

            @JvmStatic
            @Contract(pure = true)
            fun <EVENT> remove() = Remove as EventHandled<EVENT>
        }

        data object Ok : EventHandled<Nothing>()
        data object Remove : EventHandled<Nothing>()
        data class SwapTo<EVENT>(val handler: Handler<EVENT>) : EventHandled<EVENT>()
    }

    protected open fun eventSource() {}

    companion object {
        @JvmStatic
        @Contract(pure = true)
        fun <EVENT : Any> dependingOn(fiber: Fiberous<*>, handler: Handler<EVENT>): Handler<EVENT> = object : Handler<EVENT> {
            override fun handleEvent(event: EVENT): EventHandled<EVENT> {
                val handled = handler.handleEvent(event)
                return if (fiber.fiber.status.alive) handled
                else {
                    handler.remove()
                    EventHandled.Remove
                }
            }

            override fun remove() {
                handler.remove()
            }
        }

        @JvmStatic
        @Contract(pure = true)
        fun <EVENT : Any> dependingOn(channel: Channel<*>, handler: Handler<EVENT>): Handler<EVENT> = object : Handler<EVENT> {
            override fun handleEvent(event: EVENT): EventHandled<EVENT> {
                val handled = handler.handleEvent(event)
                return if (channel.open) handled
                else {
                    handler.remove()
                    EventHandled.Remove
                }
            }

            override fun remove() {
                handler.remove()
            }
        }

        @JvmStatic
        @Contract(pure = true)
        fun <EVENT : Any> forwardTo(fiber: Fiberous<*>, f: Consumer<EVENT>): Handler<EVENT> = { event ->
            f.accept(event)
            if (fiber.fiber.status.alive) EventHandled.Ok
            else EventHandled.Remove
        }

        @JvmStatic
        @Contract(pure = true)
        fun <EVENT : Any> forwardTo(channel: Channel<in EVENT>): Handler<EVENT> = { event ->
            channel.send(event)
            if (channel.open) EventHandled.Ok
            else EventHandled.Remove
        }

        @JvmStatic
        @Contract(pure = true)
        fun <EVENT, MSG : Any> forwardTo(
            channel: Channel<in MSG>,
            f: (EVENT) -> MSG,
        ): Handler<EVENT> = { event ->
            channel.send(f(event))
            if (channel.open) EventHandled.Ok
            else EventHandled.Remove
        }
    }

    fun interface Handler<EVENT> {
        fun handleEvent(event: EVENT): EventHandled<EVENT>
        fun remove() {}
    }

    private val handlers = Q<Handler<EVENT>>()

    private val k = object : Continuation {
        override fun eval() = run {
            eventSource()
            channel.tryPoll { event ->
                synchronized(handlers) {
                    handlers.replaceAll { handler ->
                        when (val result = handler.handleEvent(event)) {
                            EventHandled.Ok -> handler
                            EventHandled.Remove -> null
                            is EventHandled.SwapTo -> result.handler
                        }
                    }
                }
            }
            this
        }
    }

    private val spawnable = Spawnable.Builder<Nothing>(k)

    @get:JvmName("fiber")
    final override val fiber = spawnable.spawnLink()

    private val channel: Channel<EVENT> = Channel.queue<EVENT>().also { it.link(fiber) }

    fun addHandler(handler: Handler<EVENT>) {
        synchronized(handlers) {
            handlers.append(handler)
        }
    }

    fun swapHandler(
        replace: Handler<EVENT>,
        with: Handler<EVENT>,
    ): Boolean = synchronized(handlers) {
        handlers.replaceFirst { handler ->
            if (handler == replace) {
                replace.remove()
                with
            } else null
        } !== null
    }

    fun removeHandler(handler: Handler<EVENT>): Boolean = synchronized(handlers) {
        handlers.removeFirst { it == handler }
    }?.remove() !== null

    fun notify(event: EVENT) {
        channel.send(event)
    }
}
