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

    interface Handler<EVENT> {
        fun handleEvent(event: EVENT): EventHandled<EVENT>
        fun remove() {}
    }

    private val handlers = Q<Handler<EVENT>>()

    private val k = object : Continuation {
        override fun eval() = run {
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
