package dev.frozenmilk.dairy.mercurial.continuations

import dev.frozenmilk.dairy.mercurial.Mercurial
import dev.frozenmilk.dairy.mercurial.continuations.Continuation.Builder
import dev.frozenmilk.dairy.mercurial.continuations.Continuation.IOExec
import dev.frozenmilk.dairy.mercurial.continuations.Continuation.Value
import dev.frozenmilk.dairy.mercurial.environments.Reference
import dev.frozenmilk.dairy.mercurial.processes.Channel
import dev.frozenmilk.dairy.mercurial.processes.ExitReason
import dev.frozenmilk.dairy.mercurial.processes.Fiber
import dev.frozenmilk.dairy.mercurial.processes.Messages
import dev.frozenmilk.dairy.mercurial.Tracing.traced
import dev.frozenmilk.util.collections.WBT
import org.jetbrains.annotations.Contract
import java.util.function.BooleanSupplier
import java.util.function.DoubleSupplier
import java.util.function.IntSupplier
import java.util.function.Supplier
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark

object Continuations {
    //
    // halt
    //

    @JvmField
    val halt: Continuation = Continuation.Halt

    //
    // noop
    //

    @JvmField
    val noop: Builder<*> = Continuation.Halt

    //
    // value
    //

    @Suppress("ClassName")
    object value {
        @JvmStatic
        @Contract(pure = true)
        fun <T> o(f: Supplier<out T>): Builder<T> = traced { trace ->
            Value.O.Builder(trace, f)
        }

        @JvmStatic
        @Contract(pure = true)
        fun d(f: DoubleSupplier): Builder<Double> = traced { trace ->
            Value.D.Builder(trace, f)
        }

        @JvmStatic
        @Contract(pure = true)
        fun i(f: IntSupplier): Builder<Int> = traced { trace ->
            Value.I.Builder(trace, f)
        }

        @JvmStatic
        @Contract(pure = true)
        fun b(f: BooleanSupplier): Builder<Boolean> = traced { trace ->
            Value.B.Builder(trace, f)
        }
    }

    // TODO: top level maps (all 16)

    //
    // exec
    //

    @JvmStatic
    @Contract(pure = true)
    fun exec(f: Runnable): Builder<Any?> = traced { trace ->
        IOExec.Builder(trace, f)
    }

    //
    // sequence
    //

    @JvmStatic
    @Contract(pure = true)
    fun sequence(vararg builders: Builder<*>): Builder<Any?> =
        builders.reduceRightOrNull { a, b -> a.then(b) } ?: noop

    @JvmStatic
    @Contract(pure = true)
    fun sequence(builders: List<Builder<*>>): Builder<Any?> =
        builders.reduceRightOrNull { a, b -> a.then(b) } ?: noop

    //
    // if
    //

    @JvmStatic
    @Contract(pure = true)
    fun <T> ifThen(
        cond: Builder<Boolean>,
        t: Builder<T>,
    ): IfThen<T> = traced { trace ->
        IfThen(
            null,
            trace,
            cond,
            t,
        )
    }

    @JvmStatic
    @Contract(pure = true)
    fun <T> ifThen(
        cond: Builder<Boolean>,
        t: Builder<T>,
        f: Builder<T>,
    ) = traced { ifThen(cond, t).elseThen(f) }

    @JvmStatic
    @Contract(pure = true)
    fun <T> ifThen(
        cond: BooleanSupplier,
        t: Builder<T>,
    ) = traced {
        ifThen(value.b(cond), t)
    }

    @JvmStatic
    @Contract(pure = true)
    fun <T> ifThen(
        cond: BooleanSupplier,
        t: Builder<T>,
        f: Builder<T>,
    ) = traced {
        ifThen(cond, t).elseThen(f)
    }

    //
    // match
    //

    @JvmStatic
    @Contract(pure = true)
    fun <M, T> match(
        value: Builder<M>,
        with: Cases.Inexhaustive<M, T>,
    ): Builder<Any?> = traced { trace ->
        Match.Inexhaustive(
            trace,
            value,
            with,
        )
    }

    @JvmStatic
    @Contract(pure = true)
    fun <M, T> match(
        value: Builder<M>,
        with: Cases.Exhaustive<M, T>,
    ): Builder<T> = traced { trace ->
        Match.Exhaustive(
            trace,
            value,
            with,
        )
    }

    @JvmStatic
    @Contract(pure = true)
    fun <M, T> match(
        f: Supplier<M>,
        with: Cases.Inexhaustive<M, T>,
    ) = traced {
        match(
            value.o(f),
            with,
        )
    }

    @JvmStatic
    @Contract(pure = true)
    fun <M, T> match(
        f: Supplier<M>,
        with: Cases.Exhaustive<M, T>,
    ) = traced {
        match(
            value.o(f),
            with,
        )
    }

    @JvmStatic
    @Contract(pure = true)
    fun <M, T> cases() = Cases.Inexhaustive<M, T>()

    //
    // panic
    //

    @JvmStatic
    @Contract(pure = true)
    fun panic(msg: Supplier<String>) = traced {
        value.o { throw RuntimeException(msg.get()) }
    }

    @JvmStatic
    @Contract(pure = true)
    fun panic(msg: String) = traced {
        value.o { throw RuntimeException(msg) }
    }

    @JvmField
    val unreachable = value.o {
        throw IllegalStateException("Reached unreachable state, your program is invalid")
    }

    //
    // expression
    //

    @JvmStatic
    @Contract(pure = true)
    fun <T> expression(f: Expression<*>.(self: Builder<T>) -> Builder<T>): Builder<T> = traced {
        Expression(f).lambda
    }

    //
    // loop
    //

    @JvmStatic
    @Contract(pure = true)
    fun loop(body: Builder<*>) = expression<Nothing> { self ->
        body.then(self)
    }

    @JvmStatic
    @Contract(pure = true)
    fun loop(cond: Builder<Boolean>, body: Builder<*>) = expression { self ->
        ifThen(
            cond,
            body.then(self),
        )
    }

    @JvmStatic
    @Contract(pure = true)
    fun loop(cond: BooleanSupplier, body: Builder<*>) = traced {
        expression { self ->
            ifThen(
                cond,
                body.then(self),
            )
        }
    }

    //
    // repeat
    //

    @JvmStatic
    @Contract(pure = true)
    fun <T> repeat(n: Int, body: Builder<T>) = run {
        require(n > 0) { "must repeat at least once" }
        repeat(body, n - 1, body)
    }

    private tailrec fun <T> repeat(
        acc: Builder<T>,
        n: Int,
        body: Builder<T>,
    ): Builder<T> = if (n == 0) acc
    else repeat(acc.then(body), n - 1, body)

    //
    // waitUntil
    //

    @JvmStatic
    @Contract(pure = true)
    fun waitUntil(cond: Builder<Boolean>) = expression { self ->
        ifThen(
            cond,
            noop,
            self,
        )
    }

    @JvmStatic
    @Contract(pure = true)
    fun waitUntil(cond: BooleanSupplier) = expression { self ->
        ifThen(
            cond,
            noop,
            self,
        )
    }

    //
    // waitFor
    //

    @JvmField
    val waitFor = function<TimeMark, Any?> { _, timeMark ->
        waitUntil { timeMark().hasPassedNow() }
    }

    @JvmStatic
    @Contract(pure = true)
    fun duration(duration: Supplier<Duration>) = Supplier {
        Mercurial.timeSource.markNow() + duration.get()
    }

    @JvmStatic
    @Contract(pure = true)
    fun duration(duration: Duration) = duration { duration }

    @JvmStatic
    @Contract(pure = true)
    fun seconds(seconds: DoubleSupplier) = duration { seconds.asDouble.seconds }

    @JvmStatic
    @Contract(pure = true)
    fun seconds(seconds: Double) = duration(seconds.seconds)

    @JvmField
    val forever = duration { Duration.INFINITE }

    @JvmField
    val zero = duration { Duration.ZERO }

    //
    // command
    //

    @JvmField
    val command = Command.DEFAULT

    //
    // spawn / await / exit
    //

    @JvmStatic
    @Contract(pure = true)
    fun <T> spawn(spawn: Builder<T>): Builder<Fiber<T>> = traced { trace ->
        Continuation.Spawn.Builder(
            trace,
            spawn,
            Fiber.SpawnFlag.None,
        )
    }

    @JvmStatic
    @SafeVarargs
    @Contract(pure = true)
    fun <T> spawn(vararg spawns: Builder<out T>): Builder<WBT.Tree<Fiber<T>>?> = traced { trace ->
        Continuation.Spawn.Set.Builder(
            trace,
            spawns.toList(),
            Fiber.SpawnFlag.None,
        )
    }

    @Suppress("ClassName")
    object spawn {
        @JvmStatic
        @Contract(pure = true)
        fun <T> link(spawn: Builder<T>): Builder<Fiber<T>> = traced { trace ->
            Continuation.Spawn.Builder(
                trace,
                spawn,
                Fiber.SpawnFlag.Link,
            )
        }

        @JvmStatic
        @SafeVarargs
        @Contract(pure = true)
        fun <T> link(vararg spawns: Builder<out T>): Builder<WBT.Tree<Fiber<T>>?> = traced { trace ->
            Continuation.Spawn.Set.Builder(
                trace,
                spawns.toList(),
                Fiber.SpawnFlag.Link,
            )
        }

        @JvmStatic
        @Contract(pure = true)
        fun <T> monitor(
            channel: Supplier<out Channel<in Messages.Down>>,
            spawn: Builder<T>,
        ): Builder<Fiber<T>> = traced { trace ->
            Continuation.Spawn.Builder(
                trace,
                spawn,
                Fiber.SpawnFlag.Monitor(channel),
            )
        }

        @JvmStatic
        @Contract(pure = true)
        fun <T> monitor(
            channel: Channel<in Messages.Down>,
            spawn: Builder<T>,
        ): Builder<Fiber<T>> = traced { trace ->
            Continuation.Spawn.Builder(
                trace,
                spawn,
                Fiber.SpawnFlag.Monitor { channel },
            )
        }

        @JvmStatic
        @SafeVarargs
        @Contract(pure = true)
        fun <T> monitor(
            channel: Supplier<out Channel<in Messages.Down>>,
            vararg spawns: Builder<out T>,
        ): Builder<WBT.Tree<Fiber<T>>?> = traced { trace ->
            Continuation.Spawn.Set.Builder(
                trace,
                spawns.toList(),
                Fiber.SpawnFlag.Monitor(channel),
            )
        }

        @JvmStatic
        @SafeVarargs
        @Contract(pure = true)
        fun <T> monitor(
            channel: Channel<in Messages.Down>,
            vararg spawns: Builder<out T>,
        ): Builder<WBT.Tree<Fiber<T>>?> = traced { trace ->
            Continuation.Spawn.Set.Builder(
                trace,
                spawns.toList(),
                Fiber.SpawnFlag.Monitor { channel },
            )
        }
    }

    @Suppress("ClassName")
    object await : Parameter.O<Fiber<*>, Return<Any?>>(function { _, fiber ->
        waitUntil { !fiber().status.alive }
    }) {
        @JvmField
        val exit = this

        @JvmField
        val status = function<Fiber<*>, ExitReason> { _, fiber ->
            !waitUntil { !fiber().status.alive }
            value.o { fiber().status as ExitReason }
        }

        private val result = function<Fiber<*>, Fiber.Result<Any?>> { _, fiber ->
            !waitUntil { !fiber().status.alive }
            value.o {
                val fiber = fiber()
                val exitReason = fiber.status as ExitReason
                if (exitReason is ExitReason.Normally) Fiber.Result.Ok(fiber.returnRegister.o)
                else exitReason
            }
        }

        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        @Contract(pure = true)
        fun <T> result() = result as O<Fiber<T>, Return<Fiber.Result<T>>>

        private val expect = function<Fiber<*>, Any?> { _, fiber ->
            !waitUntil { !fiber().status.alive }
            ifThen(
                { fiber().status === ExitReason.Normally },
                Continuation.CopyRegister.Builder(fiber)
            ).elseThen(value.o {
                throw IllegalStateException("Expected fiber ${fiber()} to exit normally")
            })
        }

        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        @Contract(pure = true)
        fun <T> expect() = expect as O<Fiber<T>, Return<T>>

        @JvmField
        val all = function<WBT.Tree<out Fiber<*>>?, Any?> { _, fibers ->
            waitUntil { Fiber.Set.all(fibers()) { !it.status.alive } }
        }

        @JvmField
        val any = function<WBT.Tree<out Fiber<*>>?, Any?> { _, fibers ->
            waitUntil { Fiber.Set.any(fibers()) { !it.status.alive } }
        }
    }

    @Suppress("ClassName")
    object exit : Parameter.O<Fiber<*>, Parameter.O<ExitReason, Return<Any?>>>(
        function { _, fiber, exitReason ->
            exec { fiber().exit(exitReason()) }
        }
    ) {
        @JvmField
        val one = this

        @JvmField
        val all = function<WBT.Tree<out Fiber<*>>?, ExitReason, Any?> { _, fibers, exitReason ->
            exec {
                val exitReason = exitReason()
                Fiber.Set.foreach(fibers()) { fiber ->
                    fiber.unlink()
                    fiber.exit(exitReason)
                }
            }
        }
    }

    @Suppress("ClassName")
    object interrupt : Parameter.O<Fiber<*>, Return<Any?>>(
        function { _, fiber ->
            exec { fiber().exit(ExitReason.Interrupt) }
        }
    ) {
        @JvmField
        val one = this

        @JvmField
        val all = function<WBT.Tree<out Fiber<*>>?, Any?> { _, fibers ->
            exec {
                Fiber.Set.foreach(fibers()) { fiber ->
                    fiber.unlink()
                    fiber.exit(ExitReason.Interrupt)
                }
            }
        }
    }

    @Suppress("ClassName")
    object kill : Parameter.O<Fiber<*>, Return<Any?>>(function { _, fiber ->
        exec { fiber().exit(ExitReason.Kill) }
    }) {
        @JvmField
        val one = this

        @JvmField
        val all = function<WBT.Tree<out Fiber<*>>?, Any?> { _, fibers ->
            exec {
                Fiber.Set.foreach(fibers()) { fiber ->
                    fiber.unlink()
                    fiber.exit(ExitReason.Kill)
                }
            }
        }
    }

    //
    // parallel
    //

    @JvmStatic
    @Contract(pure = true)
    fun parallel(vararg spawns: Builder<*>) =
        await.all * spawn.link(*spawns)

    //
    // race
    //

    @JvmStatic
    @Contract(pure = true)
    fun race(vararg spawns: Builder<*>) = expression {
        val fibers = o(spawn.link(*spawns))
        !(await.any * fibers)
        interrupt.all * fibers
    }

    //
    // deadline
    //

    @JvmStatic
    @Contract(pure = true)
    fun <T> deadline(deadline: Builder<T>, vararg spawns: Builder<*>) = expression {
        val deadline = o(spawn.link(deadline))
        val fibers = o(spawn.link(*spawns))
        !(await * deadline)
        interrupt.all * fibers
    }

    //
    // receive
    //

    @JvmStatic
    @Contract(pure = true)
    fun <T> receive() = Receive<T>()

    //
    // functions
    //

    @JvmStatic
    @Contract(pure = true)
    fun <A, T> function(
        f: Expression<*>.(
            self: Parameter.O<A, Return<T>>,
            Reference.O<A>,
        ) -> Builder<T>,
    ) = function.O(f)

    @JvmStatic
    @Contract(pure = true)
    fun <A, B, T> function(
        f: Expression<*>.(
            self: Parameter.O<A, Parameter.O<B, Return<T>>>,
            Reference.O<A>,
            Reference.O<B>,
        ) -> Builder<T>,
    ) = function.OO(f)

    @JvmStatic
    @Contract(pure = true)
    fun <A, B, C, T> function(
        f: Expression<*>.(
            self: Parameter.O<A, Parameter.O<B, Parameter.O<C, Return<T>>>>,
            Reference.O<A>,
            Reference.O<B>,
            Reference.O<C>,
        ) -> Builder<T>,
    ) = function.OOO(f)

    @Suppress("ClassName")
    object param {
        @JvmStatic
        @Contract(pure = true)
        fun <T, K : Lambda<K>> o(
            f: LambdaBuilder.O<T, *>.(
                self: Parameter.O<T, K>,
                Reference.O<T>,
            ) -> LambdaBuilder<K>,
        ) = traced {
            LambdaBuilder.O(f).lambda
        }

        @JvmStatic
        @Contract(pure = true)
        fun <K : Lambda<K>> d(
            f: LambdaBuilder.D<*>.(
                self: Parameter.D<K>,
                Reference.D,
            ) -> LambdaBuilder<K>,
        ) = traced {
            LambdaBuilder.D(f).lambda
        }

        @JvmStatic
        @Contract(pure = true)
        fun <K : Lambda<K>> i(
            f: LambdaBuilder.I<*>.(
                self: Parameter.I<K>,
                Reference.I,
            ) -> LambdaBuilder<K>,
        ) = traced {
            LambdaBuilder.I(f).lambda
        }

        @JvmStatic
        @Contract(pure = true)
        fun <K : Lambda<K>> b(
            f: LambdaBuilder.B<*>.(
                self: Parameter.B<K>,
                Reference.B,
            ) -> LambdaBuilder<K>,
        ) = traced {
            LambdaBuilder.B(f).lambda
        }
    }

    @Suppress("ClassName", "FunctionName", "Unused")
    object function {
        @JvmStatic
        @Contract(pure = true)
        fun <A, T> O(
            f: Expression<*>.(
                self: Parameter.O<A, Return<T>>,
                Reference.O<A>,
            ) -> Builder<T>,
        ): Parameter.O<A, Return<T>> =
            param.o { self, a ->
                body {
                    f(self, a)
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> D(
            f: Expression<*>.(
                self: Parameter.D<Return<T>>,
                Reference.D,
            ) -> Builder<T>,
        ): Parameter.D<Return<T>> =
            param.d { self, a ->
                body {
                    f(self, a)
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> I(
            f: Expression<*>.(
                self: Parameter.I<Return<T>>,
                Reference.I,
            ) -> Builder<T>,
        ): Parameter.I<Return<T>> =
            param.i { self, a ->
                body {
                    f(self, a)
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> B(
            f: Expression<*>.(
                self: Parameter.B<Return<T>>,
                Reference.B,
            ) -> Builder<T>,
        ): Parameter.B<Return<T>> =
            param.b { self, a ->
                body {
                    f(self, a)
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <A, B, T> OO(
            f: Expression<*>.(
                self: Parameter.O<A, Parameter.O<B, Return<T>>>,
                Reference.O<A>,
                Reference.O<B>,
            ) -> Builder<T>,
        ): Parameter.O<A, Parameter.O<B, Return<T>>> =
            param.o { self, a ->
                o { b ->
                    body {
                        f(self, a, b)
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <B, T> DO(
            f: Expression<*>.(
                self: Parameter.D<Parameter.O<B, Return<T>>>,
                Reference.D,
                Reference.O<B>,
            ) -> Builder<T>,
        ): Parameter.D<Parameter.O<B, Return<T>>> =
            param.d { self, a ->
                o { b ->
                    body {
                        f(self, a, b)
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <B, T> IO(
            f: Expression<*>.(
                self: Parameter.I<Parameter.O<B, Return<T>>>,
                Reference.I,
                Reference.O<B>,
            ) -> Builder<T>,
        ): Parameter.I<Parameter.O<B, Return<T>>> =
            param.i { self, a ->
                o { b ->
                    body {
                        f(self, a, b)
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <B, T> BO(
            f: Expression<*>.(
                self: Parameter.B<Parameter.O<B, Return<T>>>,
                Reference.B,
                Reference.O<B>,
            ) -> Builder<T>,
        ): Parameter.B<Parameter.O<B, Return<T>>> =
            param.b { self, a ->
                o { b ->
                    body {
                        f(self, a, b)
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <A, B, C, T> OOO(
            f: Expression<*>.(
                self: Parameter.O<A, Parameter.O<B, Parameter.O<C, Return<T>>>>,
                Reference.O<A>,
                Reference.O<B>,
                Reference.O<C>,
            ) -> Builder<T>,
        ): Parameter.O<A, Parameter.O<B, Parameter.O<C, Return<T>>>> =
            param.o { self, a ->
                o { b ->
                    o { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <B, C, T> DOO(
            f: Expression<*>.(
                self: Parameter.D<Parameter.O<B, Parameter.O<C, Return<T>>>>,
                Reference.D,
                Reference.O<B>,
                Reference.O<C>,
            ) -> Builder<T>,
        ): Parameter.D<Parameter.O<B, Parameter.O<C, Return<T>>>> =
            param.d { self, a ->
                o { b ->
                    o { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <B, C, T> IOO(
            f: Expression<*>.(
                self: Parameter.I<Parameter.O<B, Parameter.O<C, Return<T>>>>,
                Reference.I,
                Reference.O<B>,
                Reference.O<C>,
            ) -> Builder<T>,
        ): Parameter.I<Parameter.O<B, Parameter.O<C, Return<T>>>> =
            param.i { self, a ->
                o { b ->
                    o { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <B, C, T> BOO(
            f: Expression<*>.(
                self: Parameter.B<Parameter.O<B, Parameter.O<C, Return<T>>>>,
                Reference.B,
                Reference.O<B>,
                Reference.O<C>,
            ) -> Builder<T>,
        ): Parameter.B<Parameter.O<B, Parameter.O<C, Return<T>>>> =
            param.b { self, a ->
                o { b ->
                    o { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <A, C, T> ODO(
            f: Expression<*>.(
                self: Parameter.O<A, Parameter.D<Parameter.O<C, Return<T>>>>,
                Reference.O<A>,
                Reference.D,
                Reference.O<C>,
            ) -> Builder<T>,
        ): Parameter.O<A, Parameter.D<Parameter.O<C, Return<T>>>> =
            param.o { self, a ->
                d { b ->
                    o { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <C, T> DDO(
            f: Expression<*>.(
                self: Parameter.D<Parameter.D<Parameter.O<C, Return<T>>>>,
                Reference.D,
                Reference.D,
                Reference.O<C>,
            ) -> Builder<T>,
        ): Parameter.D<Parameter.D<Parameter.O<C, Return<T>>>> =
            param.d { self, a ->
                d { b ->
                    o { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <C, T> IDO(
            f: Expression<*>.(
                self: Parameter.I<Parameter.D<Parameter.O<C, Return<T>>>>,
                Reference.I,
                Reference.D,
                Reference.O<C>,
            ) -> Builder<T>,
        ): Parameter.I<Parameter.D<Parameter.O<C, Return<T>>>> =
            param.i { self, a ->
                d { b ->
                    o { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <C, T> BDO(
            f: Expression<*>.(
                self: Parameter.B<Parameter.D<Parameter.O<C, Return<T>>>>,
                Reference.B,
                Reference.D,
                Reference.O<C>,
            ) -> Builder<T>,
        ): Parameter.B<Parameter.D<Parameter.O<C, Return<T>>>> =
            param.b { self, a ->
                d { b ->
                    o { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <A, C, T> OIO(
            f: Expression<*>.(
                self: Parameter.O<A, Parameter.I<Parameter.O<C, Return<T>>>>,
                Reference.O<A>,
                Reference.I,
                Reference.O<C>,
            ) -> Builder<T>,
        ): Parameter.O<A, Parameter.I<Parameter.O<C, Return<T>>>> =
            param.o { self, a ->
                i { b ->
                    o { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <C, T> DIO(
            f: Expression<*>.(
                self: Parameter.D<Parameter.I<Parameter.O<C, Return<T>>>>,
                Reference.D,
                Reference.I,
                Reference.O<C>,
            ) -> Builder<T>,
        ): Parameter.D<Parameter.I<Parameter.O<C, Return<T>>>> =
            param.d { self, a ->
                i { b ->
                    o { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <C, T> IIO(
            f: Expression<*>.(
                self: Parameter.I<Parameter.I<Parameter.O<C, Return<T>>>>,
                Reference.I,
                Reference.I,
                Reference.O<C>,
            ) -> Builder<T>,
        ): Parameter.I<Parameter.I<Parameter.O<C, Return<T>>>> =
            param.i { self, a ->
                i { b ->
                    o { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <C, T> BIO(
            f: Expression<*>.(
                self: Parameter.B<Parameter.I<Parameter.O<C, Return<T>>>>,
                Reference.B,
                Reference.I,
                Reference.O<C>,
            ) -> Builder<T>,
        ): Parameter.B<Parameter.I<Parameter.O<C, Return<T>>>> =
            param.b { self, a ->
                i { b ->
                    o { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <A, C, T> OBO(
            f: Expression<*>.(
                self: Parameter.O<A, Parameter.B<Parameter.O<C, Return<T>>>>,
                Reference.O<A>,
                Reference.B,
                Reference.O<C>,
            ) -> Builder<T>,
        ): Parameter.O<A, Parameter.B<Parameter.O<C, Return<T>>>> =
            param.o { self, a ->
                b { b ->
                    o { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <C, T> DBO(
            f: Expression<*>.(
                self: Parameter.D<Parameter.B<Parameter.O<C, Return<T>>>>,
                Reference.D,
                Reference.B,
                Reference.O<C>,
            ) -> Builder<T>,
        ): Parameter.D<Parameter.B<Parameter.O<C, Return<T>>>> =
            param.d { self, a ->
                b { b ->
                    o { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <C, T> IBO(
            f: Expression<*>.(
                self: Parameter.I<Parameter.B<Parameter.O<C, Return<T>>>>,
                Reference.I,
                Reference.B,
                Reference.O<C>,
            ) -> Builder<T>,
        ): Parameter.I<Parameter.B<Parameter.O<C, Return<T>>>> =
            param.i { self, a ->
                b { b ->
                    o { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <C, T> BBO(
            f: Expression<*>.(
                self: Parameter.B<Parameter.B<Parameter.O<C, Return<T>>>>,
                Reference.B,
                Reference.B,
                Reference.O<C>,
            ) -> Builder<T>,
        ): Parameter.B<Parameter.B<Parameter.O<C, Return<T>>>> =
            param.b { self, a ->
                b { b ->
                    o { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <A, T> OD(
            f: Expression<*>.(
                self: Parameter.O<A, Parameter.D<Return<T>>>,
                Reference.O<A>,
                Reference.D,
            ) -> Builder<T>,
        ): Parameter.O<A, Parameter.D<Return<T>>> =
            param.o { self, a ->
                d { b ->
                    body {
                        f(self, a, b)
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> DD(
            f: Expression<*>.(
                self: Parameter.D<Parameter.D<Return<T>>>,
                Reference.D,
                Reference.D,
            ) -> Builder<T>,
        ): Parameter.D<Parameter.D<Return<T>>> =
            param.d { self, a ->
                d { b ->
                    body {
                        f(self, a, b)
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> ID(
            f: Expression<*>.(
                self: Parameter.I<Parameter.D<Return<T>>>,
                Reference.I,
                Reference.D,
            ) -> Builder<T>,
        ): Parameter.I<Parameter.D<Return<T>>> =
            param.i { self, a ->
                d { b ->
                    body {
                        f(self, a, b)
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> BD(
            f: Expression<*>.(
                self: Parameter.B<Parameter.D<Return<T>>>,
                Reference.B,
                Reference.D,
            ) -> Builder<T>,
        ): Parameter.B<Parameter.D<Return<T>>> =
            param.b { self, a ->
                d { b ->
                    body {
                        f(self, a, b)
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <A, B, T> OOD(
            f: Expression<*>.(
                self: Parameter.O<A, Parameter.O<B, Parameter.D<Return<T>>>>,
                Reference.O<A>,
                Reference.O<B>,
                Reference.D,
            ) -> Builder<T>,
        ): Parameter.O<A, Parameter.O<B, Parameter.D<Return<T>>>> =
            param.o { self, a ->
                o { b ->
                    d { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <B, T> DOD(
            f: Expression<*>.(
                self: Parameter.D<Parameter.O<B, Parameter.D<Return<T>>>>,
                Reference.D,
                Reference.O<B>,
                Reference.D,
            ) -> Builder<T>,
        ): Parameter.D<Parameter.O<B, Parameter.D<Return<T>>>> =
            param.d { self, a ->
                o { b ->
                    d { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <B, T> IOD(
            f: Expression<*>.(
                self: Parameter.I<Parameter.O<B, Parameter.D<Return<T>>>>,
                Reference.I,
                Reference.O<B>,
                Reference.D,
            ) -> Builder<T>,
        ): Parameter.I<Parameter.O<B, Parameter.D<Return<T>>>> =
            param.i { self, a ->
                o { b ->
                    d { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <B, T> BOD(
            f: Expression<*>.(
                self: Parameter.B<Parameter.O<B, Parameter.D<Return<T>>>>,
                Reference.B,
                Reference.O<B>,
                Reference.D,
            ) -> Builder<T>,
        ): Parameter.B<Parameter.O<B, Parameter.D<Return<T>>>> =
            param.b { self, a ->
                o { b ->
                    d { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <A, T> ODD(
            f: Expression<*>.(
                self: Parameter.O<A, Parameter.D<Parameter.D<Return<T>>>>,
                Reference.O<A>,
                Reference.D,
                Reference.D,
            ) -> Builder<T>,
        ): Parameter.O<A, Parameter.D<Parameter.D<Return<T>>>> =
            param.o { self, a ->
                d { b ->
                    d { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> DDD(
            f: Expression<*>.(
                self: Parameter.D<Parameter.D<Parameter.D<Return<T>>>>,
                Reference.D,
                Reference.D,
                Reference.D,
            ) -> Builder<T>,
        ): Parameter.D<Parameter.D<Parameter.D<Return<T>>>> =
            param.d { self, a ->
                d { b ->
                    d { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> IDD(
            f: Expression<*>.(
                self: Parameter.I<Parameter.D<Parameter.D<Return<T>>>>,
                Reference.I,
                Reference.D,
                Reference.D,
            ) -> Builder<T>,
        ): Parameter.I<Parameter.D<Parameter.D<Return<T>>>> =
            param.i { self, a ->
                d { b ->
                    d { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> BDD(
            f: Expression<*>.(
                self: Parameter.B<Parameter.D<Parameter.D<Return<T>>>>,
                Reference.B,
                Reference.D,
                Reference.D,
            ) -> Builder<T>,
        ): Parameter.B<Parameter.D<Parameter.D<Return<T>>>> =
            param.b { self, a ->
                d { b ->
                    d { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <A, T> OID(
            f: Expression<*>.(
                self: Parameter.O<A, Parameter.I<Parameter.D<Return<T>>>>,
                Reference.O<A>,
                Reference.I,
                Reference.D,
            ) -> Builder<T>,
        ): Parameter.O<A, Parameter.I<Parameter.D<Return<T>>>> =
            param.o { self, a ->
                i { b ->
                    d { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> DID(
            f: Expression<*>.(
                self: Parameter.D<Parameter.I<Parameter.D<Return<T>>>>,
                Reference.D,
                Reference.I,
                Reference.D,
            ) -> Builder<T>,
        ): Parameter.D<Parameter.I<Parameter.D<Return<T>>>> =
            param.d { self, a ->
                i { b ->
                    d { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> IID(
            f: Expression<*>.(
                self: Parameter.I<Parameter.I<Parameter.D<Return<T>>>>,
                Reference.I,
                Reference.I,
                Reference.D,
            ) -> Builder<T>,
        ): Parameter.I<Parameter.I<Parameter.D<Return<T>>>> =
            param.i { self, a ->
                i { b ->
                    d { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> BID(
            f: Expression<*>.(
                self: Parameter.B<Parameter.I<Parameter.D<Return<T>>>>,
                Reference.B,
                Reference.I,
                Reference.D,
            ) -> Builder<T>,
        ): Parameter.B<Parameter.I<Parameter.D<Return<T>>>> =
            param.b { self, a ->
                i { b ->
                    d { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <A, T> OBD(
            f: Expression<*>.(
                self: Parameter.O<A, Parameter.B<Parameter.D<Return<T>>>>,
                Reference.O<A>,
                Reference.B,
                Reference.D,
            ) -> Builder<T>,
        ): Parameter.O<A, Parameter.B<Parameter.D<Return<T>>>> =
            param.o { self, a ->
                b { b ->
                    d { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> DBD(
            f: Expression<*>.(
                self: Parameter.D<Parameter.B<Parameter.D<Return<T>>>>,
                Reference.D,
                Reference.B,
                Reference.D,
            ) -> Builder<T>,
        ): Parameter.D<Parameter.B<Parameter.D<Return<T>>>> =
            param.d { self, a ->
                b { b ->
                    d { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> IBD(
            f: Expression<*>.(
                self: Parameter.I<Parameter.B<Parameter.D<Return<T>>>>,
                Reference.I,
                Reference.B,
                Reference.D,
            ) -> Builder<T>,
        ): Parameter.I<Parameter.B<Parameter.D<Return<T>>>> =
            param.i { self, a ->
                b { b ->
                    d { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> BBD(
            f: Expression<*>.(
                self: Parameter.B<Parameter.B<Parameter.D<Return<T>>>>,
                Reference.B,
                Reference.B,
                Reference.D,
            ) -> Builder<T>,
        ): Parameter.B<Parameter.B<Parameter.D<Return<T>>>> =
            param.b { self, a ->
                b { b ->
                    d { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <A, T> OI(
            f: Expression<*>.(
                self: Parameter.O<A, Parameter.I<Return<T>>>,
                Reference.O<A>,
                Reference.I,
            ) -> Builder<T>,
        ): Parameter.O<A, Parameter.I<Return<T>>> =
            param.o { self, a ->
                i { b ->
                    body {
                        f(self, a, b)
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> DI(
            f: Expression<*>.(
                self: Parameter.D<Parameter.I<Return<T>>>,
                Reference.D,
                Reference.I,
            ) -> Builder<T>,
        ): Parameter.D<Parameter.I<Return<T>>> =
            param.d { self, a ->
                i { b ->
                    body {
                        f(self, a, b)
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> II(
            f: Expression<*>.(
                self: Parameter.I<Parameter.I<Return<T>>>,
                Reference.I,
                Reference.I,
            ) -> Builder<T>,
        ): Parameter.I<Parameter.I<Return<T>>> =
            param.i { self, a ->
                i { b ->
                    body {
                        f(self, a, b)
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> BI(
            f: Expression<*>.(
                self: Parameter.B<Parameter.I<Return<T>>>,
                Reference.B,
                Reference.I,
            ) -> Builder<T>,
        ): Parameter.B<Parameter.I<Return<T>>> =
            param.b { self, a ->
                i { b ->
                    body {
                        f(self, a, b)
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <A, B, T> OOI(
            f: Expression<*>.(
                self: Parameter.O<A, Parameter.O<B, Parameter.I<Return<T>>>>,
                Reference.O<A>,
                Reference.O<B>,
                Reference.I,
            ) -> Builder<T>,
        ): Parameter.O<A, Parameter.O<B, Parameter.I<Return<T>>>> =
            param.o { self, a ->
                o { b ->
                    i { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <B, T> DOI(
            f: Expression<*>.(
                self: Parameter.D<Parameter.O<B, Parameter.I<Return<T>>>>,
                Reference.D,
                Reference.O<B>,
                Reference.I,
            ) -> Builder<T>,
        ): Parameter.D<Parameter.O<B, Parameter.I<Return<T>>>> =
            param.d { self, a ->
                o { b ->
                    i { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <B, T> IOI(
            f: Expression<*>.(
                self: Parameter.I<Parameter.O<B, Parameter.I<Return<T>>>>,
                Reference.I,
                Reference.O<B>,
                Reference.I,
            ) -> Builder<T>,
        ): Parameter.I<Parameter.O<B, Parameter.I<Return<T>>>> =
            param.i { self, a ->
                o { b ->
                    i { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <B, T> BOI(
            f: Expression<*>.(
                self: Parameter.B<Parameter.O<B, Parameter.I<Return<T>>>>,
                Reference.B,
                Reference.O<B>,
                Reference.I,
            ) -> Builder<T>,
        ): Parameter.B<Parameter.O<B, Parameter.I<Return<T>>>> =
            param.b { self, a ->
                o { b ->
                    i { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <A, T> ODI(
            f: Expression<*>.(
                self: Parameter.O<A, Parameter.D<Parameter.I<Return<T>>>>,
                Reference.O<A>,
                Reference.D,
                Reference.I,
            ) -> Builder<T>,
        ): Parameter.O<A, Parameter.D<Parameter.I<Return<T>>>> =
            param.o { self, a ->
                d { b ->
                    i { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> DDI(
            f: Expression<*>.(
                self: Parameter.D<Parameter.D<Parameter.I<Return<T>>>>,
                Reference.D,
                Reference.D,
                Reference.I,
            ) -> Builder<T>,
        ): Parameter.D<Parameter.D<Parameter.I<Return<T>>>> =
            param.d { self, a ->
                d { b ->
                    i { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> IDI(
            f: Expression<*>.(
                self: Parameter.I<Parameter.D<Parameter.I<Return<T>>>>,
                Reference.I,
                Reference.D,
                Reference.I,
            ) -> Builder<T>,
        ): Parameter.I<Parameter.D<Parameter.I<Return<T>>>> =
            param.i { self, a ->
                d { b ->
                    i { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> BDI(
            f: Expression<*>.(
                self: Parameter.B<Parameter.D<Parameter.I<Return<T>>>>,
                Reference.B,
                Reference.D,
                Reference.I,
            ) -> Builder<T>,
        ): Parameter.B<Parameter.D<Parameter.I<Return<T>>>> =
            param.b { self, a ->
                d { b ->
                    i { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <A, T> OII(
            f: Expression<*>.(
                self: Parameter.O<A, Parameter.I<Parameter.I<Return<T>>>>,
                Reference.O<A>,
                Reference.I,
                Reference.I,
            ) -> Builder<T>,
        ): Parameter.O<A, Parameter.I<Parameter.I<Return<T>>>> =
            param.o { self, a ->
                i { b ->
                    i { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> DII(
            f: Expression<*>.(
                self: Parameter.D<Parameter.I<Parameter.I<Return<T>>>>,
                Reference.D,
                Reference.I,
                Reference.I,
            ) -> Builder<T>,
        ): Parameter.D<Parameter.I<Parameter.I<Return<T>>>> =
            param.d { self, a ->
                i { b ->
                    i { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> III(
            f: Expression<*>.(
                self: Parameter.I<Parameter.I<Parameter.I<Return<T>>>>,
                Reference.I,
                Reference.I,
                Reference.I,
            ) -> Builder<T>,
        ): Parameter.I<Parameter.I<Parameter.I<Return<T>>>> =
            param.i { self, a ->
                i { b ->
                    i { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> BII(
            f: Expression<*>.(
                self: Parameter.B<Parameter.I<Parameter.I<Return<T>>>>,
                Reference.B,
                Reference.I,
                Reference.I,
            ) -> Builder<T>,
        ): Parameter.B<Parameter.I<Parameter.I<Return<T>>>> =
            param.b { self, a ->
                i { b ->
                    i { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <A, T> OBI(
            f: Expression<*>.(
                self: Parameter.O<A, Parameter.B<Parameter.I<Return<T>>>>,
                Reference.O<A>,
                Reference.B,
                Reference.I,
            ) -> Builder<T>,
        ): Parameter.O<A, Parameter.B<Parameter.I<Return<T>>>> =
            param.o { self, a ->
                b { b ->
                    i { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> DBI(
            f: Expression<*>.(
                self: Parameter.D<Parameter.B<Parameter.I<Return<T>>>>,
                Reference.D,
                Reference.B,
                Reference.I,
            ) -> Builder<T>,
        ): Parameter.D<Parameter.B<Parameter.I<Return<T>>>> =
            param.d { self, a ->
                b { b ->
                    i { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> IBI(
            f: Expression<*>.(
                self: Parameter.I<Parameter.B<Parameter.I<Return<T>>>>,
                Reference.I,
                Reference.B,
                Reference.I,
            ) -> Builder<T>,
        ): Parameter.I<Parameter.B<Parameter.I<Return<T>>>> =
            param.i { self, a ->
                b { b ->
                    i { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> BBI(
            f: Expression<*>.(
                self: Parameter.B<Parameter.B<Parameter.I<Return<T>>>>,
                Reference.B,
                Reference.B,
                Reference.I,
            ) -> Builder<T>,
        ): Parameter.B<Parameter.B<Parameter.I<Return<T>>>> =
            param.b { self, a ->
                b { b ->
                    i { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <A, T> OB(
            f: Expression<*>.(
                self: Parameter.O<A, Parameter.B<Return<T>>>,
                Reference.O<A>,
                Reference.B,
            ) -> Builder<T>,
        ): Parameter.O<A, Parameter.B<Return<T>>> =
            param.o { self, a ->
                b { b ->
                    body {
                        f(self, a, b)
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> DB(
            f: Expression<*>.(
                self: Parameter.D<Parameter.B<Return<T>>>,
                Reference.D,
                Reference.B,
            ) -> Builder<T>,
        ): Parameter.D<Parameter.B<Return<T>>> =
            param.d { self, a ->
                b { b ->
                    body {
                        f(self, a, b)
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> IB(
            f: Expression<*>.(
                self: Parameter.I<Parameter.B<Return<T>>>,
                Reference.I,
                Reference.B,
            ) -> Builder<T>,
        ): Parameter.I<Parameter.B<Return<T>>> =
            param.i { self, a ->
                b { b ->
                    body {
                        f(self, a, b)
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> BB(
            f: Expression<*>.(
                self: Parameter.B<Parameter.B<Return<T>>>,
                Reference.B,
                Reference.B,
            ) -> Builder<T>,
        ): Parameter.B<Parameter.B<Return<T>>> =
            param.b { self, a ->
                b { b ->
                    body {
                        f(self, a, b)
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <A, B, T> OOB(
            f: Expression<*>.(
                self: Parameter.O<A, Parameter.O<B, Parameter.B<Return<T>>>>,
                Reference.O<A>,
                Reference.O<B>,
                Reference.B,
            ) -> Builder<T>,
        ): Parameter.O<A, Parameter.O<B, Parameter.B<Return<T>>>> =
            param.o { self, a ->
                o { b ->
                    b { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <B, T> DOB(
            f: Expression<*>.(
                self: Parameter.D<Parameter.O<B, Parameter.B<Return<T>>>>,
                Reference.D,
                Reference.O<B>,
                Reference.B,
            ) -> Builder<T>,
        ): Parameter.D<Parameter.O<B, Parameter.B<Return<T>>>> =
            param.d { self, a ->
                o { b ->
                    b { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <B, T> IOB(
            f: Expression<*>.(
                self: Parameter.I<Parameter.O<B, Parameter.B<Return<T>>>>,
                Reference.I,
                Reference.O<B>,
                Reference.B,
            ) -> Builder<T>,
        ): Parameter.I<Parameter.O<B, Parameter.B<Return<T>>>> =
            param.i { self, a ->
                o { b ->
                    b { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <B, T> BOB(
            f: Expression<*>.(
                self: Parameter.B<Parameter.O<B, Parameter.B<Return<T>>>>,
                Reference.B,
                Reference.O<B>,
                Reference.B,
            ) -> Builder<T>,
        ): Parameter.B<Parameter.O<B, Parameter.B<Return<T>>>> =
            param.b { self, a ->
                o { b ->
                    b { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <A, T> ODB(
            f: Expression<*>.(
                self: Parameter.O<A, Parameter.D<Parameter.B<Return<T>>>>,
                Reference.O<A>,
                Reference.D,
                Reference.B,
            ) -> Builder<T>,
        ): Parameter.O<A, Parameter.D<Parameter.B<Return<T>>>> =
            param.o { self, a ->
                d { b ->
                    b { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> DDB(
            f: Expression<*>.(
                self: Parameter.D<Parameter.D<Parameter.B<Return<T>>>>,
                Reference.D,
                Reference.D,
                Reference.B,
            ) -> Builder<T>,
        ): Parameter.D<Parameter.D<Parameter.B<Return<T>>>> =
            param.d { self, a ->
                d { b ->
                    b { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> IDB(
            f: Expression<*>.(
                self: Parameter.I<Parameter.D<Parameter.B<Return<T>>>>,
                Reference.I,
                Reference.D,
                Reference.B,
            ) -> Builder<T>,
        ): Parameter.I<Parameter.D<Parameter.B<Return<T>>>> =
            param.i { self, a ->
                d { b ->
                    b { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> BDB(
            f: Expression<*>.(
                self: Parameter.B<Parameter.D<Parameter.B<Return<T>>>>,
                Reference.B,
                Reference.D,
                Reference.B,
            ) -> Builder<T>,
        ): Parameter.B<Parameter.D<Parameter.B<Return<T>>>> =
            param.b { self, a ->
                d { b ->
                    b { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <A, T> OIB(
            f: Expression<*>.(
                self: Parameter.O<A, Parameter.I<Parameter.B<Return<T>>>>,
                Reference.O<A>,
                Reference.I,
                Reference.B,
            ) -> Builder<T>,
        ): Parameter.O<A, Parameter.I<Parameter.B<Return<T>>>> =
            param.o { self, a ->
                i { b ->
                    b { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> DIB(
            f: Expression<*>.(
                self: Parameter.D<Parameter.I<Parameter.B<Return<T>>>>,
                Reference.D,
                Reference.I,
                Reference.B,
            ) -> Builder<T>,
        ): Parameter.D<Parameter.I<Parameter.B<Return<T>>>> =
            param.d { self, a ->
                i { b ->
                    b { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> IIB(
            f: Expression<*>.(
                self: Parameter.I<Parameter.I<Parameter.B<Return<T>>>>,
                Reference.I,
                Reference.I,
                Reference.B,
            ) -> Builder<T>,
        ): Parameter.I<Parameter.I<Parameter.B<Return<T>>>> =
            param.i { self, a ->
                i { b ->
                    b { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> BIB(
            f: Expression<*>.(
                self: Parameter.B<Parameter.I<Parameter.B<Return<T>>>>,
                Reference.B,
                Reference.I,
                Reference.B,
            ) -> Builder<T>,
        ): Parameter.B<Parameter.I<Parameter.B<Return<T>>>> =
            param.b { self, a ->
                i { b ->
                    b { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <A, T> OBB(
            f: Expression<*>.(
                self: Parameter.O<A, Parameter.B<Parameter.B<Return<T>>>>,
                Reference.O<A>,
                Reference.B,
                Reference.B,
            ) -> Builder<T>,
        ): Parameter.O<A, Parameter.B<Parameter.B<Return<T>>>> =
            param.o { self, a ->
                b { b ->
                    b { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> DBB(
            f: Expression<*>.(
                self: Parameter.D<Parameter.B<Parameter.B<Return<T>>>>,
                Reference.D,
                Reference.B,
                Reference.B,
            ) -> Builder<T>,
        ): Parameter.D<Parameter.B<Parameter.B<Return<T>>>> =
            param.d { self, a ->
                b { b ->
                    b { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> IBB(
            f: Expression<*>.(
                self: Parameter.I<Parameter.B<Parameter.B<Return<T>>>>,
                Reference.I,
                Reference.B,
                Reference.B,
            ) -> Builder<T>,
        ): Parameter.I<Parameter.B<Parameter.B<Return<T>>>> =
            param.i { self, a ->
                b { b ->
                    b { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }

        @JvmStatic
        @Contract(pure = true)
        fun <T> BBB(
            f: Expression<*>.(
                self: Parameter.B<Parameter.B<Parameter.B<Return<T>>>>,
                Reference.B,
                Reference.B,
                Reference.B,
            ) -> Builder<T>,
        ): Parameter.B<Parameter.B<Parameter.B<Return<T>>>> =
            param.b { self, a ->
                b { b ->
                    b { c ->
                        body {
                            f(self, a, b, c)
                        }
                    }
                }
            }
    }
}
