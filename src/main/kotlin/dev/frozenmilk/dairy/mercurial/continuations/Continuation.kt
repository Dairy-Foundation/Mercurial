package dev.frozenmilk.dairy.mercurial.continuations

import dev.frozenmilk.dairy.mercurial.MapTo
import dev.frozenmilk.dairy.mercurial.MapToBoolean
import dev.frozenmilk.dairy.mercurial.MapToDouble
import dev.frozenmilk.dairy.mercurial.MapToInt
import dev.frozenmilk.dairy.mercurial.Mercurial
import dev.frozenmilk.dairy.mercurial.Tracing.plus
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.match
import dev.frozenmilk.dairy.mercurial.continuations.PredicateChain.Companion.compile
import dev.frozenmilk.dairy.mercurial.environments.Reference
import dev.frozenmilk.dairy.mercurial.environments.SpaghettiStack
import dev.frozenmilk.dairy.mercurial.processes.Fiber
import dev.frozenmilk.dairy.mercurial.processes.Spawnable
import dev.frozenmilk.dairy.mercurial.Tracing.traced
import dev.frozenmilk.util.collections.Cons
import dev.frozenmilk.util.collections.Ord
import dev.frozenmilk.util.collections.WBT
import org.jetbrains.annotations.Contract
import java.util.function.BooleanSupplier
import java.util.function.DoubleSupplier
import java.util.function.IntSupplier
import java.util.function.Supplier

interface Continuation {
    val trace: Cons<StackTraceElement>?
        get() = null

    fun eval(): Continuation

    interface Builder<out T> {
        @Contract(pure = true)
        fun compile(trace: Cons<StackTraceElement>?, k: Continuation): Continuation

        @Contract(pure = true)
        fun compile() = traced { trace ->
            compile(trace + null, Halt)
        }

        @Contract(pure = true)
        fun <T> then(then: Builder<T>): Builder<T> = traced { trace ->
            Then(this, trace, then)
        }

        @Contract(pure = true)
        fun thenExec(then: Runnable): Builder<Any?> = traced { trace ->
            then(IOExec.Builder(trace, then))
        }

        @Contract(pure = true)
        fun <U> map(f: MapTo<T, U>) = traced { trace ->
            then(Map.O(trace, f))
        }

        @Contract(pure = true)
        fun mapD(f: MapToDouble<T>) = traced { trace ->
            then(Map.D(trace, f))
        }

        @Contract(pure = true)
        fun mapI(f: MapToInt<T>) = traced { trace ->
            then(Map.I(trace, f))
        }

        @Contract(pure = true)
        fun mapB(f: MapToBoolean<T>) = traced { trace ->
            then(Map.B(trace, f))
        }

        @Contract(pure = true)
        fun <U> match(with: Cases.Inexhaustive<@UnsafeVariance T, U>) = match(this, with)

        @Contract(pure = true)
        fun <U> match(with: Cases.Exhaustive<@UnsafeVariance T, U>) = match(this, with)

        @Contract(pure = true)
        fun spawnable(): Spawnable<T> = Spawnable.Builder(this)

        data class Then<out T>(
            val a: Builder<*>,
            val trace: StackTraceElement?,
            val b: Builder<T>
        ) : Builder<T> {
            override fun compile(
                trace: Cons<StackTraceElement>?,
                k: Continuation,
            ) = a.compile(trace, b.compile(this.trace + trace, k))
        }

        @Suppress("UNCHECKED_CAST")
        object Map {
            data class O<in T, out U>(
                val trace: StackTraceElement?,
                val f: MapTo<T, U>,
            ) : Builder<U> {
                override fun compile(
                    trace: Cons<StackTraceElement>?,
                    k: Continuation,
                ) = IOExec(
                    this.trace + trace,
                    {
                        val register = Fiber.current.returnRegister
                        register.o = f.mapTo(register.o as T)
                    },
                    k,
                )
            }

            data class D<in T>(
                val trace: StackTraceElement?,
                val f: MapToDouble<T>,
            ) : Builder<Double> {
                override fun compile(
                    trace: Cons<StackTraceElement>?,
                    k: Continuation,
                ) = IOExec(
                    this.trace + trace,
                    {
                        val register = Fiber.current.returnRegister
                        register.d = f.mapToDouble(register.o as T)
                    },
                    k,
                )
            }

            data class I<in T>(
                val trace: StackTraceElement?,
                val f: MapToInt<T>,
            ) : Builder<Int> {
                override fun compile(
                    trace: Cons<StackTraceElement>?,
                    k: Continuation,
                ) = IOExec(
                    this.trace + trace,
                    {
                        val register = Fiber.current.returnRegister
                        register.i = f.mapToInt(register.o as T)
                    },
                    k,
                )
            }

            data class B<in T>(
                val trace: StackTraceElement?,
                val f: MapToBoolean<T>,
            ) : Builder<Boolean> {
                override fun compile(
                    trace: Cons<StackTraceElement>?,
                    k: Continuation,
                ) = IOExec(
                    this.trace + trace,
                    {
                        val register = Fiber.current.returnRegister
                        register.b = f.mapToBoolean(register.o as T)
                    },
                    k,
                )
            }
        }
    }

    data object Halt : Continuation, Builder<Any?> {
        override fun eval() = run {
            // return
            Fiber.current.pop()
            this
        }

        override fun compile(trace: Cons<StackTraceElement>?, k: Continuation) = k
    }

    data object Root : Continuation {
        override fun eval() = this
    }

    data class Spawn<out T>(
        override val trace: Cons<StackTraceElement>?,
        val spawn: Continuation,
        val flag: Fiber.SpawnFlag,
        val k: Continuation,
    ) : Continuation {
        override fun eval() = run {
            val current = Fiber.current
            val frame = current.frame
            val fiber = Fiber<T>(
                SpaghettiStack(
                    frame?.trace,
                    frame,
                    null,
                    spawn,
                )
            )
            Mercurial.scheduler.schedule(fiber)
            current.returnRegister.o = fiber
            flag.applyTo(fiber)
            k
        }

        data class Builder<out T>(
            val trace: StackTraceElement?,
            val spawn: Continuation.Builder<T>,
            val flag: Fiber.SpawnFlag,
        ) : Continuation.Builder<Fiber<T>> {
            override fun compile(
                trace: Cons<StackTraceElement>?,
                k: Continuation,
            ) = Spawn<T>(
                this.trace + trace,
                spawn.compile(),
                flag,
                k,
            )
        }

        data class Set<out T>(
            override val trace: Cons<StackTraceElement>?,
            val spawns: List<Continuation>,
            val flag: Fiber.SpawnFlag,
            val k: Continuation,
        ) : Continuation {
            override fun eval() = run {
                val frame = Fiber.current.frame
                // produce and return set of fibers
                Fiber.current.returnRegister.o =
                    spawns.fold(Fiber.Set.empty<Fiber<T>>()) { set, spawn ->
                        val fiber = Fiber<T>(
                            SpaghettiStack(
                                frame?.trace,
                                frame,
                                null,
                                spawn,
                            )
                        )
                        Mercurial.scheduler.schedule(fiber)
                        flag.applyTo(fiber)
                        Fiber.Set.add(set, fiber)
                    }

                k
            }

            data class Builder<out T>(
                val trace: StackTraceElement?,
                val spawns: List<Continuation.Builder<T>>,
                val flag: Fiber.SpawnFlag,
            ) : Continuation.Builder<WBT.Tree<Fiber<@UnsafeVariance T>>?> {
                override fun compile(
                    trace: Cons<StackTraceElement>?,
                    k: Continuation,
                ) = if (spawns.isEmpty()) k
                else Set<T>(
                    this.trace + trace,
                    spawns.map(Continuation.Builder<T>::compile),
                    flag,
                    k,
                )
            }
        }
    }

    data class CopyRegister<T>(val f: Supplier<Fiber<T>>, val k: Continuation) : Continuation {
        override fun eval() = run {
            Fiber.current.returnRegister.copy(f.get().returnRegister)
            k
        }

        data class Builder<T>(val f: Supplier<Fiber<T>>) : Continuation.Builder<T> {
            override fun compile(
                trace: Cons<StackTraceElement>?,
                k: Continuation,
            ) = CopyRegister(f, k)
        }
    }

    object Value {
        data class O<T>(
            override val trace: Cons<StackTraceElement>?,
            val f: Supplier<T>,
            val k: Continuation,
        ) : Continuation {
            override fun eval() = run {
                Fiber.current.returnRegister.o = f.get()
                k
            }

            data class Builder<T>(
                val trace: StackTraceElement?,
                val f: Supplier<T>,
            ) : Continuation.Builder<T> {
                override fun compile(
                    trace: Cons<StackTraceElement>?,
                    k: Continuation,
                ) = O(this.trace + trace, f, k)
            }
        }

        data class D(
            override val trace: Cons<StackTraceElement>?,
            val f: DoubleSupplier,
            val k: Continuation,
        ) : Continuation {
            override fun eval() = run {
                Fiber.current.returnRegister.d = f.asDouble
                k
            }

            data class Builder(
                val trace: StackTraceElement?,
                val f: DoubleSupplier,
            ) : Continuation.Builder<Double> {
                override fun compile(
                    trace: Cons<StackTraceElement>?,
                    k: Continuation,
                ) = D(this.trace + trace, f, k)
            }
        }

        data class I(
            override val trace: Cons<StackTraceElement>?,
            val f: IntSupplier,
            val k: Continuation,
        ) : Continuation {
            override fun eval() = run {
                Fiber.current.returnRegister.i = f.asInt
                k
            }

            data class Builder(
                val trace: StackTraceElement?,
                val f: IntSupplier,
            ) : Continuation.Builder<Int> {
                override fun compile(
                    trace: Cons<StackTraceElement>?,
                    k: Continuation,
                ) = I(this.trace + trace, f, k)
            }
        }

        data class B(
            override val trace: Cons<StackTraceElement>?,
            val f: BooleanSupplier,
            val k: Continuation,
        ) : Continuation {
            override fun eval() = run {
                Fiber.current.returnRegister.b = f.asBoolean
                k
            }

            data class Builder(
                val trace: StackTraceElement?,
                val f: BooleanSupplier,
            ) : Continuation.Builder<Boolean> {
                override fun compile(
                    trace: Cons<StackTraceElement>?,
                    k: Continuation,
                ) = B(this.trace + trace, f, k)
            }
        }
    }

    object Store {
        object Stack {
            val o = IOExec.Builder(null) {
                val proc = Fiber.current
                proc.frame?.oPush(proc.returnRegister.o)
            }
            val d = IOExec.Builder(null) {
                val proc = Fiber.current
                proc.frame?.dPush(proc.returnRegister.d)
            }
            val i = IOExec.Builder(null) {
                val proc = Fiber.current
                proc.frame?.iPush(proc.returnRegister.i)
            }
            val b = IOExec.Builder(null) {
                val proc = Fiber.current
                proc.frame?.bPush(proc.returnRegister.b)
            }
        }

        object Formals {
            val o = IOExec.Builder(null) {
                val proc = Fiber.current
                val tip = proc.frame ?: return@Builder
                val formals = tip.oData[tip.oStack - 1] as SpaghettiStack
                formals.oPush(proc.returnRegister.o)
            }
            val d = IOExec.Builder(null) {
                val proc = Fiber.current
                val tip = proc.frame ?: return@Builder
                val formals = tip.oData[tip.oStack - 1] as SpaghettiStack
                formals.dPush(proc.returnRegister.d)
            }
            val i = IOExec.Builder(null) {
                val proc = Fiber.current
                val tip = proc.frame ?: return@Builder
                val formals = tip.oData[tip.oStack - 1] as SpaghettiStack
                formals.iPush(proc.returnRegister.i)
            }
            val b = IOExec.Builder(null) {
                val proc = Fiber.current
                val tip = proc.frame ?: return@Builder
                val formals = tip.oData[tip.oStack - 1] as SpaghettiStack
                formals.bPush(proc.returnRegister.b)
            }
        }
    }

    data class IOExec(
        override val trace: Cons<StackTraceElement>?,
        val f: Runnable,
        val k: Continuation,
    ) : Continuation {
        override fun eval() = run {
            f.run()
            k
        }

        data class Builder(
            val trace: StackTraceElement?,
            val f: Runnable,
        ) : Continuation.Builder<Any?> {
            override fun compile(
                trace: Cons<StackTraceElement>?,
                k: Continuation,
            ) = IOExec(this.trace + trace, f, k)
        }
    }

    data class IfElse(
        override val trace: Cons<StackTraceElement>?,
        val t: Continuation,
        val f: Continuation,
    ) : Continuation {
        override fun eval() = if (Fiber.current.returnRegister.b) t
        else f

        data class Builder<T>(
            val trace: StackTraceElement?,
            val t: Continuation.Builder<T>,
            val f: Continuation.Builder<T>,
        ) : Continuation.Builder<T> {
            override fun compile(
                trace: Cons<StackTraceElement>?,
                k: Continuation,
            ) = if (t == f) t.compile(this.trace + trace, k)
            else {
                val trace = this.trace + trace
                val t = t.compile(trace, k)
                val f = f.compile(trace, k)
                if (t == f) t
                else IfElse(trace, t, f)
            }
        }
    }

    data class Match2(
        val consts: WBT.Tree<Const>?,
        val types: WBT.Tree<Type>?,
        val default: (Any?) -> Continuation,
    ) : Continuation {
        companion object {
            val ConstMap = WBT.Make(Const::const, Ord.HashCode)
            val TypeMap = WBT.Make(Type::type, Ord.HashCode)
        }

        data class Const(val const: Any?, val k: (Any?) -> Continuation)
        data class Type(val type: Class<*>, val k: (Any?) -> Continuation)

        override fun eval() = run {
            val register = Fiber.current.returnRegister
            val value = register.o

            var case = ConstMap.get(consts, value)?.k //
            if (case === null && value != null) case = TypeMap.get(types, value.javaClass)?.k
            if (case === null) case = default

            // run the predicates
            case(value).also {
                // just in case
                if (register.o !== value) register.o = value
            }
        }

        data class Builder<M, T>(
            val trace: StackTraceElement?,
            val consts: WBT.Tree<Cases.Const<out M, Continuation.Builder<T>>>?,
            val types: WBT.Tree<Cases.Type<out M & Any, Continuation.Builder<T>>>?,
            val default: PredicateChain<M, Continuation.Builder<T>>?,
        ) : Continuation.Builder<T> {
            @Suppress("UNCHECKED_CAST")
            override fun compile(
                trace: Cons<StackTraceElement>?,
                k: Continuation,
            ) = run {
                val trace = this.trace + trace
                val compiler = { builder: Continuation.Builder<T> -> builder.compile(trace, k) }
                val default = default.compile(compiler) { k }

                val consts = Cases.ConstMap.map(consts) { (const, chain) ->
                    Const(
                        const,
                        chain.compile(compiler, default) as (Any?) -> Continuation,
                    )
                }
                val types = Cases.TypeMap.map(types) { (type, chain) ->
                    Type(
                        type,
                        chain.compile(compiler, default) as (Any?) -> Continuation,
                    )
                }

                Match2(
                    consts,
                    types,
                    default as (Any?) -> Continuation,
                )
            }
        }
    }

    data class Receive(val chain: (Nothing?) -> Continuation) : Continuation {
        override fun eval() = chain(null)

        data class Builder<T>(
            val chain: PredicateChain.Terminated<Nothing?, Continuation.Builder<T>>,
        ) : Continuation.Builder<T> {
            override fun compile(
                trace: Cons<StackTraceElement>?,
                k: Continuation,
            ) = Receive(
                chain.compile(
                    { builder -> builder.compile(trace, k) },
                    // unused
                    { k },
                )
            )
        }
    }

    object Call {
        // calls look like this:
        // begin -> formals -> complete

        // TODO:
        //  if the current frame is not captured
        //  then we can re-use the frame in tail calls
        //  even if the frame has data
        //  however this might be impossible to make work,
        //  due to building parameters being hard :tm:

        // TODO:
        //  also need to get functions to reserve space for function calls?
        //  might need it to be more complex
        //  as naively just adding to the current size fields would prevent runtime optimisations
        //  by expanding the size of otherwise empty frames
        //  a fairly small concern though

        data class Begin(
            val callTrace: Cons<StackTraceElement>?,
            val fn: Function,
            val tailCall: Boolean,
            val k: Continuation,
        ) : Continuation {
            override val trace: Cons<StackTraceElement>? = Cons.cat(callTrace, fn.trace)

            override fun eval() = run {
                // direct jump is safe here!
                if (tailCall && fn.isEmpty) {
                    // correct the trace as well
                    Fiber.current.frame?.trace = trace
                    fn.body
                }
                else {
                    val frame = SpaghettiStack(trace, fn)
                    Fiber.current.frame?.oPush(frame)
                    k
                }
            }
        }

        data class Complete(val k: Continuation) : Continuation {
            override fun eval() = run {
                val frame = Fiber.current.frame
                // tail call
                if (k === Halt) Fiber.current.pop()
                Fiber.current.push(frame?.oPop() as SpaghettiStack)
                k
            }
        }
    }

    sealed class Function {
        // actual
        abstract val actual: Actual

        // closure captured stack
        abstract val captured: SpaghettiStack?

        abstract val trace: Cons<StackTraceElement>?

        // body
        abstract val body: Continuation

        // sizes
        abstract val oSize: Int
        abstract val dSize: Int
        abstract val iSize: Int
        abstract val bSize: Int
        abstract val isEmpty: Boolean

        data class Actual(
            override val trace: Cons<StackTraceElement>?,
            override var body: Continuation,
        ) : Function() {
            override val actual = this
            override val captured = null
            override var oSize: Int = 0
            override var dSize: Int = 0
            override var iSize: Int = 0
            override var bSize: Int = 0
            override val isEmpty: Boolean
                get() = oSize == 0
                    && dSize == 0
                    && iSize == 0
                    && bSize == 0
        }

        data class Closure(
            val ref: Reference.O<SpaghettiStack?>,
            override val actual: Actual,
        ) : Function() {
            override val captured: SpaghettiStack?
                get() = ref.get()
            override val trace by actual::trace
            override val body by actual::body
            override val oSize by actual::oSize
            override val dSize by actual::dSize
            override val iSize by actual::iSize
            override val bSize by actual::bSize
            override val isEmpty by actual::isEmpty
        }
    }
}
