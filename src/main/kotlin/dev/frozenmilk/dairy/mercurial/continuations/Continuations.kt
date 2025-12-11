package dev.frozenmilk.dairy.mercurial.continuations

import dev.frozenmilk.dairy.mercurial.continuations.registers.Register
import dev.frozenmilk.dairy.mercurial.continuations.registers.ValRegister
import dev.frozenmilk.dairy.mercurial.continuations.registers.VarRegister
import dev.frozenmilk.util.collections.Cons
import dev.frozenmilk.util.collections.Ord
import dev.frozenmilk.util.collections.WeightBalancedTreeMap
import java.util.function.BooleanSupplier
import java.util.function.Supplier
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.KClass

object Continuations {
    //
    // scope
    //

    @Suppress("FunctionName")
    class Env {
        private class ScopedRegister<T>(val register: Register<T>, val initializer: Supplier<T>) {
            fun create() {
                Fiber.Registers.CREATE(register, initializer.get())
            }

            fun delete() {
                Fiber.Registers.DELETE(register)
            }
        }

        private var registers: Cons<ScopedRegister<*>>? = null

        fun <T> variable(initializer: Supplier<T>) = VarRegister<T>().also {
            registers = Cons.cons(ScopedRegister(it, initializer), registers)
        }

        fun <T> value(initializer: Supplier<T>) = ValRegister<T>().also {
            registers = Cons.cons(ScopedRegister(it, initializer), registers)
        }

        fun <T, R : Register<T>> bind(register: R, initializer: Supplier<T>) = register.also {
            registers = Cons.cons(ScopedRegister(it, initializer), registers)
        }

        private fun CREATE() = CREATE(registers)
        private fun CREATE(cons: Cons<ScopedRegister<*>>?) {
            if (cons != null) {
                CREATE(cons.cdr)
                cons.car.create()
            }
        }

        private fun DELETE() {
            Cons.forEach(registers, ScopedRegister<*>::delete)
        }

        @PublishedApi
        internal fun compose(inner: Closure): Closure = sequence(
            exec(::CREATE),
            inner,
            exec(::DELETE),
        )
    }

    @JvmStatic
    @OptIn(ExperimentalContracts::class)
    inline fun scope(
        withEnv: Env.() -> Closure
    ): Closure {
        contract {
            callsInPlace(withEnv, InvocationKind.EXACTLY_ONCE)
        }
        val env = Env()
        return env.compose(withEnv(env))
    }

    //
    // noop
    //

    private val NOOP = Closure { _, k -> k }

    @JvmStatic
    fun noop() = NOOP

    //
    // exec
    //

    @JvmStatic
    fun exec(f: Runnable): Closure = object : FactoryClosure() {
        override fun close(
            name: String?,
            k: Continuation,
        ) = Continuation(name ?: "exec") {
            f.run()
            k
        }
    }

    //
    // sequence
    //

    @JvmStatic
    fun sequence(vararg closures: Closure) = if (closures.isEmpty()) noop()
    else if (closures.size == 1) closures[0]
    else Closure { name, k ->
        closures.foldRight(k) { closure, k -> closure.close(name, k) }
    }

    //
    // if?
    //

    interface BinaryBranch : Closure {
        fun elseIfHuh(
            cond: BooleanSupplier,
            t: Closure,
        ): BinaryBranch = joinBinaryBranches(
            this,
            ifHuh(cond, t),
        )

        fun elseIfHuh(
            cond: ValRegister<Boolean>,
            t: Closure,
        ): BinaryBranch = joinBinaryBranches(
            this,
            ifHuh(cond, t),
        )

        fun elseHuh(f: Closure): Closure
    }

    private fun joinBinaryBranches(
        a: BinaryBranch,
        b: BinaryBranch,
    ) = run {
        object : BinaryBranch {
            override fun close(
                name: String?,
                k: Continuation,
            ) = a.elseHuh(b).close(name, k)

            override fun elseHuh(f: Closure) = a.elseHuh(b.elseHuh(f))
        }
    }

    @JvmStatic
    fun ifHuh(
        cond: BooleanSupplier,
        t: Closure,
    ): BinaryBranch = object : FactoryClosure(), BinaryBranch {
        override fun close(
            name: String?,
            k: Continuation,
        ) = run {
            val t = t.close(name, k)
            Continuation(name ?: "if?") {
                if (cond.asBoolean) t
                else k
            }
        }

        override fun elseHuh(f: Closure): Closure = object : FactoryClosure() {
            override fun close(
                name: String?,
                k: Continuation,
            ) = run {
                val t = t.close(name, k)
                val f = f.close(name, k)
                Continuation(name ?: "if?") {
                    if (cond.asBoolean) t
                    else f
                }
            }
        }
    }

    @JvmStatic
    fun ifHuh(
        cond: ValRegister<Boolean>,
        t: Closure,
    ) = ifHuh({ cond.get() }, t)

    //
    // match?
    //

    class Match<T> private constructor(
        private val select: Supplier<T>,
        private val ord: Ord<in T>,
        private val cases: WeightBalancedTreeMap<T, Closure>?,
    ) : FactoryClosure() {
        internal constructor(
            select: Supplier<T>,
            ord: Ord<in T>,
        ) : this(
            select,
            ord,
            null,
        )

        fun branch(case: T, closure: Closure) = Match(
            select,
            ord,
            WeightBalancedTreeMap.add(
                ord,
                cases,
                case,
                closure,
            ),
        )

        fun defaultBranch(closure: Closure): Closure = if (cases == null) closure
        else Closure { name, k ->
            val cases = WeightBalancedTreeMap.inorderFold(
                cases,
                null as WeightBalancedTreeMap<T, Continuation>?,
            ) { cases, case, closure ->
                WeightBalancedTreeMap.add(
                    ord,
                    cases,
                    case,
                    closure.close(name, k),
                )
            }
            val default = closure.close(name, k)
            Continuation(name ?: "match?") {
                WeightBalancedTreeMap.get(
                    ord,
                    cases,
                    select.get(),
                )?.v ?: default
            }
        }

        fun assertExhaustive() = defaultBranch(unreachable())

        override fun close(
            name: String?,
            k: Continuation,
        ) = defaultBranch(noop()).close(name, k)
    }

    @JvmStatic
    fun <T> match(
        select: Supplier<T>,
        ord: Ord<in T>,
    ) = Match(
        select,
        ord,
    )

    @JvmStatic
    fun <T> match(select: Supplier<T>) = Match(select, Ord.HashCode)

    //
    // match-type?
    //

    fun interface TypeMatchedClosure<T> {
        fun bind(register: ValRegister<T>): Closure
    }

    class MatchType<T> private constructor(
        private val select: Supplier<T>,
        private val register: ValRegister<T>,
        private val cases: WeightBalancedTreeMap<Class<out T>?, TypeMatchedClosure<out T>>?,
    ) : FactoryClosure() {
        private class UnboundClosure<T>(val closure: Closure) : TypeMatchedClosure<T> {
            override fun bind(register: ValRegister<T>) = closure
        }

        private class UnboundContinuation(k: Continuation) : Continuation by k

        internal constructor(select: Supplier<T>) : this(
            select, ValRegister(), null
        )

        @Suppress("UNCHECKED_CAST")
        fun <CASE : T & Any> branch(case: Class<CASE>, closure: TypeMatchedClosure<CASE>) =
            MatchType(
                select,
                register,
                WeightBalancedTreeMap.add(
                    Ord.HashCode,
                    cases,
                    case,
                    closure,
                ),
            )

        fun <CASE : T & Any> branch(case: Class<CASE>, closure: Closure) =
            branch(case, UnboundClosure(closure))

        fun <CASE : T & Any> branch(case: KClass<CASE>, closure: TypeMatchedClosure<CASE>) =
            branch(case.javaObjectType, closure)

        fun <CASE : T & Any> branch(case: KClass<CASE>, closure: Closure) =
            branch(case.javaObjectType, closure)

        inline fun <reified CASE : T & Any> branch(closure: TypeMatchedClosure<CASE>) =
            branch(CASE::class, closure)

        inline fun <reified CASE : T & Any> branch(closure: Closure) = branch(CASE::class, closure)

        fun nullBranch(closure: Closure) = MatchType(
            select,
            register,
            WeightBalancedTreeMap.add(
                Ord.HashCode,
                cases,
                null,
                UnboundClosure(closure),
            ),
        )

        @Suppress("UNCHECKED_CAST")
        fun default(closure: TypeMatchedClosure<T>): Closure = if (cases == null) when (closure) {
            is UnboundClosure<*> -> closure.closure

            else -> sequence(
                exec { Fiber.Registers.CREATE(register, select.get()) },
                closure.bind(register),
                exec { Fiber.Registers.DELETE(register) },
            )
        }
        else Closure { name, k ->
            val delete = exec { Fiber.Registers.DELETE(register) }
            val cases = WeightBalancedTreeMap.inorderFold(
                cases,
                null as WeightBalancedTreeMap<Class<out T>?, Continuation>?,
            ) { cases, case, typeMatchedClosure ->
                WeightBalancedTreeMap.add(
                    Ord.HashCode,
                    cases,
                    case,
                    when (typeMatchedClosure) {
                        is UnboundClosure<*> -> UnboundContinuation(
                            typeMatchedClosure.closure.close(
                                name,
                                k,
                            )
                        )

                        else -> sequence(
                            (typeMatchedClosure as TypeMatchedClosure<T>).bind(register),
                            delete,
                        ).close(name, k)
                    },
                )
            }

            val default = when (closure) {
                is UnboundClosure<*> -> UnboundContinuation(
                    closure.closure.close(
                        name,
                        k,
                    )
                )

                else -> sequence(
                    closure.bind(register),
                    delete,
                ).close(name, k)
            }

            Continuation(name ?: "match-type?") {
                val select = select.get()

                val case = WeightBalancedTreeMap.get(
                    Ord.HashCode,
                    cases,
                    select?.javaClass,
                )?.v ?: default

                if (case !is UnboundContinuation) Fiber.Registers.CREATE(register, select)
                case
            }
        }

        fun default(closure: Closure) = default(UnboundClosure(closure))

        fun assertExhaustive() = default(unreachable())

        override fun close(
            name: String?,
            k: Continuation,
        ) = default(noop()).close(name, k)
    }

    @JvmStatic
    fun <T> matchType(select: Supplier<T>) = MatchType(select)

    //
    // letrec
    //

    fun interface Letrec {
        fun fix(self: Closure): Closure
    }

    @JvmStatic
    fun letrec(
        letrec: Letrec,
    ): Closure = object : FactoryClosure() {
        override fun close(
            name: String?,
            k: Continuation,
        ) = object {
            val fixed: Closure = letrec.fix { name, k2 ->
                if (k2 == k) indirectSelf
                else indirectTerminated.close(name, k2)
            }
            val indirectTerminated = run {
                val indirect = object : Continuation {
                    override val stackTrace get() = terminated.stackTrace
                    override fun apply() = terminated.apply()
                    override fun toString() = terminated.toString()
                }
                fork(indirect)
            }
            val terminated = fixed.close(name, Continuation.halt)
            val indirectSelf = object : Continuation {
                override val stackTrace get() = self.stackTrace
                override fun apply() = self.apply()
                override fun toString() = self.toString()
            }
            val self = fixed.close(name, k)
        }.self
    }

    @JvmStatic
    fun letrecStrict(
        letrec: Letrec,
    ): Closure = object : FactoryClosure() {
        override fun close(
            name: String?,
            k: Continuation,
        ) = object {
            val indirect = object : Continuation {
                override val stackTrace get() = self.stackTrace
                override fun apply() = self.apply()
                override fun toString() = self.toString()
            }
            val self: Continuation = letrec.fix { _, k2 ->
                // TODO: patch stacktrace a bit better
                if (k2 != k) throw IllegalArgumentException("letrecStrict does not support non tail recursion").also {
                    it.stackTrace = k2.stackTrace
                }
                indirect
            }.close(name, k)
        }.self
    }

    //
    // loop
    //

    @JvmStatic
    fun loop(body: Closure) = letrecStrict { loop ->
        sequence(
            body,
            loop,
        )
    }

    @JvmStatic
    fun loop(
        cond: BooleanSupplier,
        body: Closure,
    ) = letrecStrict { loop ->
        ifHuh(
            cond,
            sequence(
                body,
                loop,
            ),
        )
    }

    //
    // repeat
    //

    @JvmStatic
    fun repeat(times: Int, body: Closure) = if (times <= 0) noop()
    else if (times == 1) body
    else Closure { name, k ->
        var res = k
        var count = 0
        while (count < times) {
            res = body.close(name, res)
            count++
        }
        res
    }

    //
    // panic!
    //

    @JvmStatic
    fun panic(message: Supplier<String>): Closure = object : FactoryClosure() {
        override fun close(
            name: String?,
            k: Continuation,
        ) = Continuation("panic!") {
            throw RuntimeException(message.get())
        }
    }

    //
    // unreachable!
    //

    @JvmStatic
    fun unreachable(): Closure = object : FactoryClosure() {
        override fun close(
            name: String?,
            k: Continuation,
        ) = Continuation("unreachable!") {
            throw RuntimeException("reached unreachable state")
        }
    }

    //
    // wait
    //

    @JvmSynthetic
    fun wait(cond: BooleanSupplier): Closure = object : FactoryClosure() {
        override fun close(
            name: String?,
            k: Continuation,
        ) = Continuation(name ?: "wait") { self ->
            if (cond.asBoolean) k
            else self
        }
    }

    @JvmStatic
    fun waitUntil(cond: BooleanSupplier) = wait(cond)

    //
    // wait seconds
    //

    interface Clock {
        fun getTime(): Long
        fun convSeconds(seconds: Double): Long
        fun done(startTime: Long, duration: Long): Boolean

        object Standard : Clock {
            override fun getTime() = System.nanoTime()
            override fun convSeconds(seconds: Double) = (seconds * 1e9).toLong()
            override fun done(startTime: Long, duration: Long) =
                (System.nanoTime() - startTime) > duration
        }
    }

    private val startTimeRegister = ValRegister<Long>()

    @JvmSynthetic
    fun wait(clock: Clock, seconds: Double): Closure = scope {
        val duration = clock.convSeconds(seconds)
        val startTime by bind(startTimeRegister, clock::getTime)

        object : FactoryClosure() {
            override fun close(
                name: String?,
                k: Continuation,
            ) = Continuation(name ?: "wait $seconds") { self ->
                if (clock.done(startTime, duration)) k
                else self
            }
        }
    }

    @JvmSynthetic
    fun wait(seconds: Double) = wait(Clock.Standard, seconds)

    /**
     * @see wait
     */
    @JvmStatic
    fun waitSeconds(clock: Clock, seconds: Double) = wait(clock, seconds)

    /**
     * @see wait
     */
    @JvmStatic
    fun waitSeconds(seconds: Double) = wait(seconds)

    //
    // concurrency registers
    //

    private val fiberRegister = ValRegister<Fiber>()
    private val fibersRegister = VarRegister<Cons<Fiber>?>()

    //
    // fork
    //

    @JvmStatic
    fun fork(process: IntoContinuation) = process as? Closure ?: scope {
        val process = process.intoContinuation()
        val fiber by bind(fiberRegister) { Fiber(process) }

        object : FactoryClosure() {
            override fun close(
                name: String?,
                k: Continuation,
            ) = Continuation(name ?: "fork") { self ->
                val fiber = fiber
                Fiber.SUBSCHEDULE(fiber)
                if (fiber.state === Fiber.State.FINISHED) k
                else self
            }
        }
    }

    //
    // parallel
    //

    @JvmStatic
    fun parallel(vararg processes: IntoContinuation) = if (processes.isEmpty()) noop()
    else if (processes.size == 1) fork(processes[0])
    else scope {
        val processes = processes.map(IntoContinuation::intoContinuation)
        val fibers = bind(fibersRegister) {
            processes.foldRight(null as Cons<Fiber>?) { process, fibers ->
                Cons.cons(Fiber(process), fibers)
            }
        }

        object : FactoryClosure() {
            override fun close(
                name: String?,
                k: Continuation,
            ) = Continuation(name ?: "parallel") { self ->
                val finished: Boolean
                fibers.map { fibers ->
                    Cons.filter(fibers) { fiber ->
                        Fiber.SUBSCHEDULE(fiber)
                        fiber.state != Fiber.State.FINISHED
                    }.also { fibers ->
                        finished = fibers == null
                    }
                }
                if (finished) k
                else self
            }
        }
    }

    //
    // race
    //

    @JvmStatic
    fun race(vararg processes: IntoContinuation) = if (processes.isEmpty()) noop()
    else if (processes.size == 1) fork(processes[0])
    else scope {
        val processes = processes.map(IntoContinuation::intoContinuation)
        val fibers = bind(fibersRegister) {
            processes.foldRight(null as Cons<Fiber>?) { process, fibers ->
                Cons.cons(Fiber(process), fibers)
            }
        }

        object : FactoryClosure() {
            override fun close(
                name: String?,
                k: Continuation,
            ) = run {
                val cancel = Continuation(name ?: "race") {
                    Cons.drainForEach(fibers.get()) { fiber ->
                        Fiber.CANCEL(fiber)
                    }
                    k
                }

                Continuation(name ?: "race") { self ->
                    var finished = false
                    fibers.map { fibers ->
                        Cons.filter(fibers) { fiber ->
                            Fiber.SUBSCHEDULE(fiber)
                            val alive = fiber.state != Fiber.State.FINISHED
                            finished = finished || !alive
                            alive
                        }
                    }
                    if (finished) cancel
                    else self
                }
            }
        }
    }

    //
    // deadline
    //

    @JvmStatic
    fun deadline(deadline: IntoContinuation, vararg processes: IntoContinuation) =
        if (processes.isEmpty()) fork(deadline)
        else scope {
            val deadline = deadline.intoContinuation()
            val deadlineFiber by bind(fiberRegister) {
                Fiber(deadline)
            }
            val processes = processes.map(IntoContinuation::intoContinuation)
            val fibers = bind(fibersRegister) {
                processes.foldRight(null as Cons<Fiber>?) { process, fibers ->
                    Cons.cons(Fiber(process), fibers)
                }
            }

            object : FactoryClosure() {
                override fun close(
                    name: String?,
                    k: Continuation,
                ) = run {
                    val cancel = Continuation(name ?: "deadline") {
                        Cons.drainForEach(fibers.get()) { fiber ->
                            Fiber.CANCEL(fiber)
                        }
                        k
                    }

                    Continuation(name ?: "deadline") { self ->
                        val deadlineFiber = deadlineFiber
                        Fiber.SUBSCHEDULE(deadlineFiber)
                        if (deadlineFiber.state == Fiber.State.FINISHED) {
                            if (fibers.get() == null) k
                            else cancel
                        } else {
                            fibers.map { fibers ->
                                Cons.filter(fibers) { fiber ->
                                    Fiber.SUBSCHEDULE(fiber)
                                    fiber.state != Fiber.State.FINISHED
                                }
                            }
                            self
                        }
                    }
                }
            }
        }

    //
    // command
    //

    class Command private constructor(
        private val init: Runnable,
        private val execute: Runnable,
        private val finished: BooleanSupplier,
        private val end: Runnable,
    ) : Closure {
        internal companion object {
            val DEFAULT_RUNNABLE = Runnable {}
            val DEFAULT_BOOLEAN_SUPPLIER = BooleanSupplier { true }
            val DEFAULT_COMMAND = Command(
                DEFAULT_RUNNABLE,
                DEFAULT_RUNNABLE,
                DEFAULT_BOOLEAN_SUPPLIER,
                DEFAULT_RUNNABLE,
            )
        }

        fun setInit(init: Runnable) = Command(
            init,
            execute,
            finished,
            end,
        )

        fun setExecute(execute: Runnable) = Command(
            init,
            execute,
            finished,
            end,
        )

        fun setFinished(finished: BooleanSupplier) = Command(
            init,
            execute,
            finished,
            end,
        )

        fun setEnd(end: Runnable) = Command(
            init,
            execute,
            finished,
            end,
        )

        override fun close(
            name: String?,
            k: Continuation,
        ) = sequence(
            if (init == DEFAULT_RUNNABLE) noop() else exec(init),
            if (finished == DEFAULT_BOOLEAN_SUPPLIER) noop() else loop(
                { !finished.asBoolean },
                if (execute == DEFAULT_RUNNABLE) noop()
                else exec(execute),
            ),
            if (end == DEFAULT_RUNNABLE) noop() else exec(end),
        ).close(name, k)
    }

    @JvmStatic
    fun command() = Command.DEFAULT_COMMAND

    //
    // jump
    //

    class JumpHandle internal constructor(
        addressRegister: VarRegister<Continuation?>,
        private val jumpName: String?,
        private val jumpK: Continuation,
    ) {
        private var address by addressRegister

        fun jump() = jump(noop())

        /**
         * WARNING: [addr] does not run sequentially with the rest of the code,
         * it is easy to accidentally use un-bound registers within it
         */
        fun jump(addr: Closure): Closure = object : FactoryClosure() {
            override fun close(
                name: String?,
                k: Continuation,
            ) = run {
                val addr = addr.close(jumpName, jumpK)
                Continuation(name ?: "jump") {
                    address = addr
                    k
                }
            }
        }
    }

    @JvmStatic
    fun jumpScope(scope: JumpHandle.() -> IntoContinuation): Closure = object : FactoryClosure() {
        private val addressRegister = VarRegister<Continuation?>()
        override fun close(
            name: String?,
            k: Continuation,
        ) = scope {
            val handle = JumpHandle(
                addressRegister,
                name,
                k,
            )
            val process = scope(handle).intoContinuation()
            val address by bind(addressRegister) { null }
            val fiber by bind(fiberRegister) { Fiber(process) }
            Closure { name, k ->
                Continuation(name ?: "jump") { self ->
                    val fiber = fiber
                    Fiber.SUBSCHEDULE(fiber)
                    address ?: if (fiber.state === Fiber.State.FINISHED) k
                    else self
                }
            }
        }.close(name, k)
    }

    //
    // async
    //

    fun interface AsyncHandle {
        fun detach(): Closure
    }

    interface AwaitHandle {
        fun await(): Closure
        fun cancel(): Closure
    }

    private val detachRegister = VarRegister<Unit?>()
    private var detach by detachRegister
    private val detachExec = exec { detach = Unit }

    @JvmStatic
    fun async(
        scheduler: Supplier<Scheduler>,
        asyncScope: AsyncHandle.() -> IntoContinuation,
        awaitScope: AwaitHandle.() -> Closure,
    ): Closure = run {
        val asyncK = asyncScope { detachExec }.intoContinuation()

        object : FactoryClosure() {
            private val fiberRegister = ValRegister<Fiber>()
            private val fiber by fiberRegister
            private val await = wait { fiber.state != Fiber.State.ACTIVE }
            private val cancel = exec { Fiber.CANCEL(fiber) }
            override fun close(
                name: String?,
                k: Continuation,
            ) = run {
                val k = sequence(
                    awaitScope(object : AwaitHandle {
                        override fun await() = await
                        override fun cancel() = cancel
                    }),
                    exec { Fiber.Registers.DELETE(fiberRegister) },
                ).close(name, k)

                val inner = Continuation(name ?: "async") { self ->
                    val fiber = fiber
                    Fiber.SUBSCHEDULE(fiber)
                    if (detach != null) {
                        Fiber.Registers.DELETE(detachRegister)
                        scheduler.get().schedule(fiber)
                        k
                    } else {
                        val fiber = fiber
                        Fiber.SUBSCHEDULE(fiber)
                        if (fiber.state === Fiber.State.FINISHED) {
                            Fiber.Registers.DELETE(detachRegister)
                            k
                        } else self
                    }
                }

                Continuation(name ?: "async") {
                    Fiber.Registers.CREATE(fiberRegister, Fiber(asyncK))
                    Fiber.Registers.CREATE(detachRegister, null)
                    inner
                }
            }
        }
    }

    @JvmStatic
    fun async(
        asyncScope: AsyncHandle.() -> IntoContinuation,
        awaitScope: AwaitHandle.() -> Closure,
    ) = async(
        Scheduler::currentScheduler,
        asyncScope,
        awaitScope,
    )
}
