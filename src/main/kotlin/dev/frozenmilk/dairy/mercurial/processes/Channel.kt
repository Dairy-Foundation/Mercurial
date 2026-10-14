package dev.frozenmilk.dairy.mercurial.processes

import dev.frozenmilk.util.collections.Ord
import dev.frozenmilk.util.collections.Q
import dev.frozenmilk.util.collections.WBT
import org.jetbrains.annotations.Contract
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

@Suppress("INAPPLICABLE_JVM_NAME")
interface Channel<MSG : Any> {
    companion object {
        @JvmField
        val Set = WBT.MakeSet<Channel<*>>(Ord.HashCode)

        @OptIn(ExperimentalContracts::class)
        @JvmStatic
        inline fun <MSG : Any> Channel<MSG>.tryPoll(f: (MSG) -> Unit) {
            contract {
                callsInPlace(f, InvocationKind.AT_MOST_ONCE)
            }
            val msg = poll()
            if (msg !== null) f(msg)
        }

        @OptIn(ExperimentalContracts::class)
        @JvmStatic
        inline fun <MSG : Any> Channel<MSG>.tryQuery(f: (MSG) -> Unit) {
            contract {
                callsInPlace(f, InvocationKind.AT_MOST_ONCE)
            }
            val msg = query()
            if (msg !== null) f(msg)
        }

        @JvmStatic
        @Contract(pure = true)
        fun <MSG : Any> queue() = Queue<MSG>()

        @JvmStatic
        @Contract(pure = true)
        fun <MSG : Any> single() = Single<MSG>()
    }

    fun send(message: MSG)
    fun sendIfEmpty(message: MSG)
    fun prepend(message: MSG)
    fun prependIfEmpty(message: MSG)
    fun returnToFront(message: MSG)
    fun poll(): MSG?
    fun query(): MSG?

    @get:JvmName("open")
    val open: Boolean
    fun link(fiber: Fiberous<*>)
    fun unlink(fiber: Fiberous<*>)
    fun close()

    class Queue<MSG : Any> : Channel<MSG> {
        private val inbox = Q<MSG>()
        private val _open = AtomicBoolean(true)

        @get:JvmName("open")
        override val open
            get() = _open.get()

        override fun poll(): MSG? = synchronized(inbox) {
            if (inbox.empty) null
            else inbox.pop()
        }

        override fun query(): MSG? = synchronized(inbox) {
            val head = inbox.head
            if (head === null) null
            else head.car
        }

        override fun send(message: MSG) {
            if (open) synchronized(inbox) {
                inbox.append(message)
            }
        }

        override fun sendIfEmpty(message: MSG) {
            if (open) synchronized(inbox) {
                if (inbox.empty) inbox.append(message)
            }
        }

        override fun prepend(message: MSG) {
            if (open) synchronized(inbox) {
                inbox.prepend(message)
            }
        }

        override fun prependIfEmpty(message: MSG) {
            if (open) synchronized(inbox) {
                if (inbox.empty) inbox.prepend(message)
            }
        }

        override fun returnToFront(message: MSG) {
            synchronized(inbox) {
                inbox.prepend(message)
            }
        }

        override fun link(fiber: Fiberous<*>) {
            fiber.fiber.link(this)
        }

        override fun unlink(fiber: Fiberous<*>) {
            fiber.fiber.unlink(this)
        }

        override fun close() {
            _open.set(false)
        }
    }

    class Single<MSG : Any> : Channel<MSG> {
        private val message = AtomicReference<MSG?>(null)
        private val _open = AtomicBoolean(true)

        @get:JvmName("open")
        override val open
            get() = _open.get()

        override fun send(message: MSG) {
            if (open) this.message.set(message)
        }

        override fun sendIfEmpty(message: MSG) {
            if (open) this.message.compareAndSet(null, message)
        }

        override fun prepend(message: MSG) {
            if (open) this.message.set(message)
        }

        override fun prependIfEmpty(message: MSG) {
            if (open) this.message.compareAndSet(null, message)
        }

        override fun returnToFront(message: MSG) {
            this.message.compareAndSet(null, message)
        }

        override fun poll(): MSG? = message.getAndSet(null)

        override fun query(): MSG? = message.get()

        override fun link(fiber: Fiberous<*>) {
            fiber.fiber.link(this)
        }

        override fun unlink(fiber: Fiberous<*>) {
            fiber.fiber.unlink(this)
        }

        override fun close() {
            _open.set(false)
        }
    }
}
