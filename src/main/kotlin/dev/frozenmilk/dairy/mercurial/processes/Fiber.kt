package dev.frozenmilk.dairy.mercurial.processes

import dev.frozenmilk.dairy.mercurial.Mercurial
import dev.frozenmilk.dairy.mercurial.continuations.Continuation
import dev.frozenmilk.dairy.mercurial.environments.GeneralPurposeRegister
import dev.frozenmilk.dairy.mercurial.environments.SpaghettiStack
import dev.frozenmilk.dairy.mercurial.getValue
import dev.frozenmilk.dairy.mercurial.setValue
import dev.frozenmilk.sinister.util.log.Logger
import dev.frozenmilk.util.collections.Cons
import dev.frozenmilk.util.collections.Ord
import dev.frozenmilk.util.collections.WBT
import java.lang.reflect.Array
import java.util.Arrays
import java.util.function.BooleanSupplier
import java.util.function.Consumer
import java.util.function.DoubleConsumer
import java.util.function.DoubleSupplier
import java.util.function.IntConsumer
import java.util.function.IntSupplier
import java.util.function.Supplier
import kotlin.reflect.KProperty
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.measureTime

class Fiber<out T> {
    companion object {
        @JvmField
        val Set = WBT.MakeSet<Fiber<*>>(Ord.HashCode)

        internal val rootThreadLocal: ThreadLocal<Fiber<Nothing>> = ThreadLocal.withInitial {
            throw IllegalStateException("Mercurial not initialised")
        }
        @JvmStatic
        @get:JvmName("root")
        val root: Fiber<Nothing> by rootThreadLocal

        @JvmStatic
        @get:JvmName("current")
        @set:JvmName("current")
        var current: Fiber<*> by ThreadLocal.withInitial {
            throw IllegalStateException("Mercurial not initialised")
        }

        @JvmStatic
        fun exitChannel(value: Channel<in Messages.Exit>?) {
            current.exitChannel = value
        }
    }

    @get:JvmName("status")
    var status: ProcessStatus = ProcessStatus.Alive
        private set

    private var exitChannel: Channel<in Messages.Exit>? = null
    val trappingExits
        get() = exitChannel !== null

    private var links = Set.empty<Fiber<*>>()
    private var monitors = Channel.Set.empty<Channel<in Messages.Down>>()

    var stack: Cons<SpaghettiStack>? = null
        private set

    fun push(frame: SpaghettiStack) {
        stack = Cons.cons(frame, stack)
    }

    fun pop() {
        stack?.let { stack ->
            this.stack = stack.cdr
        }
    }

    @get:JvmName("frame")
    val frame
        get() = stack?.car

    val returnRegister = GeneralPurposeRegister()

    var dictionary = Local.Dictionary.empty<Local.DictionaryEntry>()

    private fun retrace(e: Throwable) {
        var i = 0
        val len = e.stackTrace.size
        while (i < len) {
            val stackTraceElement = e.stackTrace[i]
            if (stackTraceElement.className == SpaghettiStack::class.qualifiedName && stackTraceElement.methodName == "poll") {
                i--
                break
            }
            i++
        }

        if (i >= len) return

        val fiberStackTraceBuilder = ArrayList<StackTraceElement?>((len * 1.5).toInt())
        fiberStackTraceBuilder.addAll(e.stackTrace.copyOf(i))

        run {
            val trace = frame?.k?.trace ?: return@run
            Cons.forEach(trace, fiberStackTraceBuilder::add)
        }
        Cons.forEach(stack) { frame ->
            val trace = frame.trace ?: return@forEach
            Cons.forEach(trace, fiberStackTraceBuilder::add)
        }
        fiberStackTraceBuilder.addAll(e.stackTrace.copyOfRange(i, e.stackTrace.size))

        e.stackTrace = fiberStackTraceBuilder.toTypedArray()
    }

    private inline fun <T> runInContext(f: () -> T) = run {
        val proc = current
        current = this
        try {
            f()
        } catch (e: Throwable) {
            if (Mercurial.stackTraces) retrace(e)
            exit(ExitReason.Exceptionally(e))
            throw e
        } finally {
            current = proc
        }
    }

    var pollDuration: Duration = Duration.ZERO
        private set

    val pollDurationSeconds
        get() = pollDuration.toDouble(DurationUnit.SECONDS)
    fun poll(): ProcessStatus = if (status !== ProcessStatus.Alive) status
    else runInContext {
        // updates state
        pollDuration = Mercurial.timeSource.measureTime {
            frame?.poll()
        }
        if (stack === null) exit(ExitReason.Normally)
        status
    }

    fun link() {
        val self = current
        if (this === self) return
        links = Set.add(links, self)
        self.links = Set.add(self.links, this)
    }

    fun unlink() {
        val self = current
        if (this === self) return
        links = Set.delete(links, self)
        self.links = Set.delete(self.links, this)
    }

    fun monitor(channel: Channel<in Messages.Down>) {
        monitors = Channel.Set.add(monitors, channel)
    }

    fun demonitor(channel: Channel<in Messages.Down>) {
        monitors = Channel.Set.delete(monitors, channel)
    }

    fun exit(reason: ExitReason) {
        // ignore new exits if we have already exited
        if (!status.alive) return
        // if it's a kill, we must process it and exit
        else if (reason === ExitReason.Kill) exitInternal(reason)
        // if the signal came from in the house, we let it happen, never trap it
        else if (this === current) exitInternal(reason)
        else {
            // if trapping, send a message
            val exitChannel = exitChannel
            if (exitChannel !== null) exitChannel.send(Messages.Exit(current, reason))
            // otherwise exit like normal
            else exitInternal(reason)
        }
    }

    private fun exitInternal(reason: ExitReason) {
        val reason = if (reason === ExitReason.Kill) ExitReason.Killed else reason
        status = reason
        if (reason is ExitReason.Exceptionally) Logger.e(
            "MercurialFiber",
            "fiber exited exceptionally",
            reason.e,
        )
        publishExit(reason)
    }

    private fun publishExit(reason: ExitReason) {
        val _ = Set.inorderFold(links, null as Messages.Exit?) { msg, link ->
            if (!link.status.alive) msg
            else {
                val exitChannel = link.exitChannel
                if (exitChannel !== null) {
                    val msg = msg ?: Messages.Exit(this, reason)
                    exitChannel.send(msg)
                    msg
                } else {
                    // if it was a normal exit,
                    // we don't propagate it
                    if (reason !is ExitReason.Normally) link.exitInternal(reason)
                    msg
                }
            }
        }

        val _ = Channel.Set.inorderFold(monitors, null as Messages.Down?) { msg, monitor ->
            val msg = msg ?: Messages.Down(this, reason)
            monitor.send(msg)
            msg
        }
    }

    @Suppress("UNCHECKED_CAST")
    @get:JvmName("returnValue")
    val returnValue: T
        get() = if (status === ExitReason.Normally) returnRegister.o as T
        else throw IllegalStateException("Expected fiber $this to have exited normally")

    @Suppress("UNCHECKED_CAST")
    @get:JvmName("returnResult")
    val returnResult: Result<T>
        get() = if (status === ExitReason.Normally) Result.Ok(returnRegister.o as T)
        else status

    constructor(program: Continuation) {
        push(SpaghettiStack(program))
    }

    constructor(frame: SpaghettiStack) {
        push(frame)
    }

    sealed class Result<out T> {
        data class Ok<T>(val value: T) : Result<T>()
    }

    sealed class Local {
        companion object {
            val Dictionary = WBT.Make(DictionaryEntry::key, Ord.IdentityHashCode)
        }

        sealed class DictionaryEntry {
            abstract val key: Local

            data class O<T>(override val key: Local.O<T>, var value: T) : DictionaryEntry()
            data class D(override val key: Local.D, var value: Double) : DictionaryEntry()
            data class I(override val key: Local.I, var value: Int) : DictionaryEntry()
            data class B(override val key: Local.B, var value: Boolean) : DictionaryEntry()
        }

        val present
            get() = present(current)

        fun present(fiber: Fiber<*>) = Dictionary.get(fiber.dictionary, this) !== null

        class O<T>(val initialiser: Supplier<T>? = null) : Local(), Supplier<T>, Consumer<T> {
            @Suppress("UNCHECKED_CAST")
            private fun entry(fiber: Fiber<*>) = Dictionary.get(
                fiber.dictionary,
                this,
            ) as DictionaryEntry.O<T>?

            private fun introduce(fiber: Fiber<*>, value: T) {
                fiber.dictionary = Dictionary.add(
                    fiber.dictionary,
                    DictionaryEntry.O(this, value),
                )
            }

            operator fun get(fiber: Fiber<*>) = run {
                val entry = entry(fiber)
                if (entry == null) {
                    requireNotNull(initialiser) { "Attempted to get value of FiberLocal when it was uninitialised" }
                    val initial = initialiser.get()
                    introduce(fiber, initial)
                    initial
                } else entry.value
            }

            override fun get() = get(current)

            operator fun set(fiber: Fiber<*>, value: T) {
                val entry = entry(fiber)
                if (entry == null) introduce(fiber, value)
                else entry.value = value
            }

            fun set(value: T) = set(current, value)
            override fun accept(t: T) = set(current, t)

            @JvmSynthetic
            operator fun invoke() = get()

            @JvmSynthetic
            operator fun invoke(value: T) = set(value)

            @JvmSynthetic
            operator fun getValue(
                thisRef: Any?,
                property: KProperty<*>,
            ) = get()

            @JvmSynthetic
            operator fun setValue(
                thisRef: Any?,
                property: KProperty<*>,
                value: T,
            ) = set(value)
        }

        class D(val initialiser: DoubleSupplier? = null) : Local(), DoubleSupplier, DoubleConsumer {
            @Suppress("UNCHECKED_CAST")
            private fun entry(fiber: Fiber<*>) = Dictionary.get(
                fiber.dictionary,
                this,
            ) as DictionaryEntry.D?

            private fun introduce(fiber: Fiber<*>, value: Double) {
                fiber.dictionary = Dictionary.add(
                    fiber.dictionary,
                    DictionaryEntry.D(this, value),
                )
            }

            operator fun get(fiber: Fiber<*>) = run {
                val entry = entry(fiber)
                if (entry === null) {
                    requireNotNull(initialiser) { "Attempted to get value of FiberLocal when it was uninitialised" }
                    val initial = initialiser.asDouble
                    introduce(fiber, initial)
                    initial
                } else entry.value
            }

            fun get() = get(current)
            override fun getAsDouble() = get(current)

            operator fun set(fiber: Fiber<*>, value: Double) {
                val entry = entry(fiber)
                if (entry == null) introduce(fiber, value)
                else entry.value = value
            }

            fun set(value: Double) = set(current, value)
            override fun accept(value: Double) = set(current, value)

            @JvmSynthetic
            operator fun invoke() = get()

            @JvmSynthetic
            operator fun invoke(value: Double) = set(value)

            @JvmSynthetic
            operator fun getValue(
                thisRef: Any?,
                property: KProperty<*>,
            ) = get()

            @JvmSynthetic
            operator fun setValue(
                thisRef: Any?,
                property: KProperty<*>,
                value: Double,
            ) = set(value)
        }

        class I(val initialiser: IntSupplier? = null) : Local(), IntSupplier, IntConsumer {
            @Suppress("UNCHECKED_CAST")
            private fun entry(fiber: Fiber<*>) = Dictionary.get(
                fiber.dictionary,
                this,
            ) as DictionaryEntry.I?

            private fun introduce(fiber: Fiber<*>, value: Int) {
                fiber.dictionary = Dictionary.add(
                    fiber.dictionary,
                    DictionaryEntry.I(this, value),
                )
            }

            operator fun get(fiber: Fiber<*>) = run {
                val entry = entry(fiber)
                if (entry === null) {
                    requireNotNull(initialiser) { "Attempted to get value of FiberLocal when it was uninitialised" }
                    val initial = initialiser.asInt
                    introduce(fiber, initial)
                    initial
                } else entry.value
            }

            fun get() = get(current)
            override fun getAsInt() = get(current)

            operator fun set(fiber: Fiber<*>, value: Int) {
                val entry = entry(fiber)
                if (entry == null) introduce(fiber, value)
                else entry.value = value
            }

            fun set(value: Int) = set(current, value)
            override fun accept(value: Int) = set(current, value)

            @JvmSynthetic
            operator fun invoke() = get()

            @JvmSynthetic
            operator fun invoke(value: Int) = set(value)

            @JvmSynthetic
            operator fun getValue(
                thisRef: Any?,
                property: KProperty<*>,
            ) = get()

            @JvmSynthetic
            operator fun setValue(
                thisRef: Any?,
                property: KProperty<*>,
                value: Int,
            ) = set(value)
        }

        class B(val initialiser: BooleanSupplier? = null) : Local(), BooleanSupplier {
            @Suppress("UNCHECKED_CAST")
            private fun entry(fiber: Fiber<*>) = Dictionary.get(
                fiber.dictionary,
                this,
            ) as DictionaryEntry.B?

            private fun introduce(fiber: Fiber<*>, value: Boolean) {
                fiber.dictionary = Dictionary.add(
                    fiber.dictionary,
                    DictionaryEntry.B(this, value),
                )
            }

            operator fun get(fiber: Fiber<*>) = run {
                val entry = entry(fiber)
                if (entry === null) {
                    requireNotNull(initialiser) { "Attempted to get value of FiberLocal when it was uninitialised" }
                    val initial = initialiser.asBoolean
                    introduce(fiber, initial)
                    initial
                } else entry.value
            }

            fun get() = get(current)
            override fun getAsBoolean() = get(current)

            operator fun set(fiber: Fiber<*>, value: Boolean) {
                val entry = entry(fiber)
                if (entry == null) introduce(fiber, value)
                else entry.value = value
            }

            fun set(value: Boolean) = set(current, value)

            @JvmSynthetic
            operator fun invoke() = get()

            @JvmSynthetic
            operator fun invoke(value: Boolean) = set(value)

            @JvmSynthetic
            operator fun getValue(
                thisRef: Any?,
                property: KProperty<*>,
            ) = get()

            @JvmSynthetic
            operator fun setValue(
                thisRef: Any?,
                property: KProperty<*>,
                value: Boolean,
            ) = set(value)
        }
    }

    sealed class SpawnFlag {
        data object None : SpawnFlag() {
            override fun applyTo(fiber: Fiber<*>) {}
        }
        data object Link : SpawnFlag() {
            override fun applyTo(fiber: Fiber<*>) = fiber.link()
        }
        data class Monitor(val channel: Supplier<out Channel<in Messages.Down>>) : SpawnFlag() {
            override fun applyTo(fiber: Fiber<*>) = fiber.monitor(channel.get())
        }

        abstract fun applyTo(fiber: Fiber<*>)
    }
}
