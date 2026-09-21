package dev.frozenmilk.dairy.mercurial.processes

import dev.frozenmilk.util.collections.Ord
import dev.frozenmilk.util.collections.Q
import dev.frozenmilk.util.collections.WBT
import org.jetbrains.annotations.Contract
import java.util.concurrent.atomic.AtomicReference
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

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

        @JvmStatic
        @Contract(pure = true)
        fun <MSG: Any> queue() = Queue<MSG>()

        @JvmStatic
        @Contract(pure = true)
        fun <MSG: Any> single() = Single<MSG>()
    }

    fun send(message: MSG)
    fun prepend(message: MSG)
    fun poll(): MSG?

    class Queue<MSG : Any> : Channel<MSG> {
        private val inbox = Q<MSG>()

        @Synchronized
        override fun poll() =
            if (inbox.empty) null
            else inbox.pop()

        @Synchronized
        override fun send(message: MSG) {
            inbox.append(message)
        }

        @Synchronized
        override fun prepend(message: MSG) {
            inbox.prepend(message)
        }
    }

    class Single<MSG : Any> : Channel<MSG> {
        private val message = AtomicReference<MSG?>(null)

        override fun send(message: MSG) = this.message.set(message)
        override fun prepend(message: MSG) = this.message.set(message)
        override fun poll() = message.getAndSet(null)
    }
}

//class Channel<MSG : Any> {
//    companion object {
//        @JvmField
//        val Set = WBT2.MakeSet<Channel<*>>(Ord.IdentityHashCode)
//    }
//
//    // queue of incoming messages
//    private var inbox = Q<MSG>()
//    private var partition = Q<MSG>()
//
//    fun send(message: MSG) = synchronized(this) {
//        inbox.append(message)
//    }
//
//    fun prepend(message: MSG) = synchronized(this) {
//        inbox.prepend(message)
//    }
//
//    @OptIn(ExperimentalContracts::class)
//    inline fun tryPoll(f: (MSG) -> Unit) {
//        contract {
//            callsInPlace(f, InvocationKind.AT_MOST_ONCE)
//        }
//        val msg = poll()
//        if (msg !== null) f(msg)
//    }
//
//    fun poll() = synchronized(this) {
//        if (inbox.empty) null
//        // get the next value from the inbox
//        else inbox.pop()
//    }
//
//    fun pollLast() = synchronized(this) {
//        val result = inbox.tail?.car
//        inbox.clear()
//        result
//    }
//
//    fun partition(msg: MSG?) {
//        msg?.let { msg ->
//            synchronized(this) {
//                partition.append(msg)
//            }
//        }
//    }
//
//    fun resetPartition() = synchronized(this) {
//        // we know all partitions arrived before all inboxed values
//        partition.append(inbox)
//        // swap inbox and partition
//        val tmp = inbox
//        inbox = partition
//        partition = tmp
//    }
//
//    val empty get() = synchronized(this) { inbox.empty }
//}
