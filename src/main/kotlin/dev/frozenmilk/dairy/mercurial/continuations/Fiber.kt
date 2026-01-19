package dev.frozenmilk.dairy.mercurial.continuations

import dev.frozenmilk.dairy.mercurial.continuations.registers.Register
import dev.frozenmilk.dairy.mercurial.continuations.registers.RegisterTree
import dev.frozenmilk.util.collections.Cons
import dev.frozenmilk.util.modifier.Modifier
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract

class Fiber(private var k: Continuation) {
    private var registerTree: RegisterTree<*>? = null

    enum class State { ACTIVE, FINISHED, CANCELLED, }

    var state = State.ACTIVE
        private set

    override fun toString() = k.toString()

    @Suppress("FunctionName")
    object Registers {
        @PublishedApi
        internal fun <T> LOOKUP(register: Register<T>) =
            requireNotNull(LOOKUP(fiberCallstack.get(), register)) {
                "uninitialised register $register"
            }

        private tailrec fun <T> LOOKUP(
            callstack: Cons<Fiber>?,
            register: Register<T>,
        ): RegisterTree<T>? {
            if (callstack == null) return null
            return RegisterTree.get(callstack.car.registerTree, register) ?: return LOOKUP(
                callstack.cdr, register
            )
        }

        @JvmStatic
        fun <T> CREATE(
            register: Register<T>, value: T
        ) {
            val currentFiber = currentFiber
            currentFiber.registerTree = RegisterTree.add(
                currentFiber.registerTree,
                register,
                value,
            )
        }

        @JvmStatic
        fun <T> DELETE(register: Register<T>) {
            val currentFiber = currentFiber
            currentFiber.registerTree = RegisterTree.delete(
                currentFiber.registerTree,
                register,
            )
        }

        @JvmStatic
        fun <T> GET(
            register: Register<T>,
        ) = LOOKUP(register).v

        @JvmStatic
        fun <T> SET(
            register: Register<T>,
            value: T,
        ) {
            LOOKUP(register).v = value
        }

        @JvmStatic
        fun <T> MAP(
            register: Register<T>,
            f: Modifier<T>,
        ): T = run {
            val r = LOOKUP(register)
            r.v = f.modify(r.v)
            r.v
        }

        @OptIn(ExperimentalContracts::class)
        @JvmStatic
        inline fun <T> MAP(
            register: Register<T>,
            f: (T) -> T,
        ): T {
            contract {
                callsInPlace(f, InvocationKind.EXACTLY_ONCE)
            }
            val r = LOOKUP(register)
            r.v = f(r.v)
            return r.v
        }
    }

    @Suppress("FunctionName")
    companion object {
        private val fiberCallstack = ThreadLocal.withInitial<Cons<Fiber>?> { null }

        fun inCallstack(fiber: Fiber) = inCallstack(fiberCallstack.get(), fiber)
        tailrec fun inCallstack(callstack: Cons<Fiber>?, fiber: Fiber): Boolean =
            if (callstack == null) false
            else callstack.car == fiber || inCallstack(callstack.cdr, fiber)

        private fun pushFiber(fiber: Fiber) {
            fiberCallstack.set(
                Cons.cons(fiber, fiberCallstack.get())
            )
        }

        private fun popFiber() {
            val cons =
                checkNotNull(fiberCallstack.get()) { "attempted to pop Fiber off an empty callstack" }
            fiberCallstack.set(cons.cdr)
            Cons.drop(cons)
        }

        @JvmStatic
        @get:JvmName("currentFiber")
        val currentFiber: Fiber
            get() = checkNotNull(fiberCallstack.get()) { "attempted to get current Fiber from empty callstack" }.car

        @JvmStatic
        fun UNRAVEL(fiber: Fiber) {
            when (fiber.state) {
                State.ACTIVE -> {
                    val k = fiber.k
                    try {
                        pushFiber(fiber)
                        fiber.k = fiber.k.apply()
                        if (fiber.k === Continuation.halt) fiber.state = State.FINISHED
                    } catch (e: FiberException) {
                        throw e
                    } catch (e: Throwable) {
                        throw FiberException(e, k)
                    } finally {
                        popFiber()
                    }
                }

                State.FINISHED -> {}
                State.CANCELLED -> throw IllegalStateException("attempted to UNRAVEL cancelled continuation")
            }
        }

        @JvmStatic
        fun CANCEL(fiber: Fiber) {
            when (fiber.state) {
                State.ACTIVE -> {
                    fiber.state = State.CANCELLED
                    val k = fiber.k
                    try {
                        pushFiber(fiber)
                        fiber.k = fiber.k.apply()
                        if (fiber.k === Continuation.halt) fiber.state = State.FINISHED
                    } catch (e: FiberException) {
                        throw e
                    } catch (e: Throwable) {
                        throw FiberException(e, k)
                    } finally {
                        popFiber()
                    }
                }

                State.FINISHED -> {}
                State.CANCELLED -> throw IllegalStateException("attempted to CANCEL already cancelled continuation")
            }
        }

        @JvmStatic
        fun SUBSCHEDULE(child: Fiber) = if (currentFiber.state === State.CANCELLED) {
            CANCEL(child)
        } else if (child.state !== State.CANCELLED) {
            UNRAVEL(child)
        } else {
            // The child is already canceled
            // The parent should then see that it's canceled
            Unit
        }
    }
}