package dev.frozenmilk.dairy.mercurial.continuations.mutexes

import dev.frozenmilk.dairy.mercurial.continuations.Closure
import dev.frozenmilk.dairy.mercurial.continuations.Continuation
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.letrecStrict
import dev.frozenmilk.dairy.mercurial.continuations.FactoryClosure
import dev.frozenmilk.dairy.mercurial.continuations.Fiber
import dev.frozenmilk.dairy.mercurial.continuations.IntoContinuation
import dev.frozenmilk.dairy.mercurial.continuations.registers.ValRegister
import dev.frozenmilk.dairy.mercurial.continuations.registers.VarRegister
import java.util.function.Supplier

object Mutexes {
    fun interface Prioritiser<PRIORITY> {
        /**
         * returns `true` if [a] should interrupt [b]
         */
        fun greaterPriority(a: PRIORITY, b: PRIORITY): Boolean
    }

    fun interface MutexGuardClosureScope<T> {
        fun bind(guardRegister: VarRegister<T>): IntoContinuation
    }

    private object REJECTED

    private val fiberRegister = ValRegister<Fiber>()
    private val statusRegister = VarRegister<REJECTED?>()

    @JvmStatic
    fun <PRIORITY, T> guardPoll(
        mutex: Mutex<PRIORITY, T>,
        priority: Supplier<PRIORITY>,
        scope: MutexGuardClosureScope<T>,
        rejected: Closure,
        cancelled: Closure,
    ): Closure = run {
        val scope = scope.bind(mutex.register).intoContinuation()

        object : FactoryClosure() {
            override fun close(
                name: String?,
                k: Continuation,
            ) = run {
                val fiber by fiberRegister
                var status by statusRegister

                val rejected = rejected.close(name, k)
                val cancelled = cancelled.close(name, k)

                val inner = Continuation(name ?: "mutex-guard") { self ->
                    val fiber = fiber
                    Fiber.SUBSCHEDULE(fiber)
                    when (fiber.state) {
                        Fiber.State.ACTIVE -> if (status === REJECTED) {
                            Fiber.Registers.DELETE(fiberRegister)
                            Fiber.Registers.DELETE(statusRegister)
                            rejected
                        } else self

                        Fiber.State.FINISHED -> {
                            mutex.popFiber(fiber)
                            Fiber.Registers.DELETE(fiberRegister)
                            Fiber.Registers.DELETE(statusRegister)
                            k
                        }

                        Fiber.State.CANCELLED -> {
                            mutex.popFiber(fiber)
                            Fiber.Registers.DELETE(fiberRegister)
                            Fiber.Registers.DELETE(statusRegister)
                            cancelled
                        }
                    }
                }

                val acquire = Continuation(name ?: "mutex-acquire") {
                    mutex.acquire(priority.get()) {
                        status = REJECTED
                    }
                    scope
                }

                Continuation(name ?: "mutex-acquire") {
                    val fiber = Fiber(acquire)
                    Fiber.Registers.CREATE(fiberRegister, fiber)
                    Fiber.Registers.CREATE(statusRegister, null)
                    inner
                }
            }
        }
    }

    @JvmStatic
    fun <PRIORITY, T> guard(
        mutex: Mutex<PRIORITY, T>,
        priority: Supplier<PRIORITY>,
        scope: MutexGuardClosureScope<T>,
        cancelled: Closure,
    ) = letrecStrict { self ->
        guardPoll(
            mutex,
            priority,
            scope,
            self,
            cancelled,
        )
    }
}