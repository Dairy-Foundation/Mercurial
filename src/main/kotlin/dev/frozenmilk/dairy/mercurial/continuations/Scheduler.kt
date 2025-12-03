package dev.frozenmilk.dairy.mercurial.continuations

import dev.frozenmilk.util.collections.Cons
import dev.frozenmilk.util.collections.Q
import java.util.function.BooleanSupplier

interface Scheduler {
    /**
     * run [fiber]
     */
    fun schedule(fiber: Fiber): Fiber

    /**
     * run [k] and return the [Fiber] its running in
     */
    fun schedule(k: Continuation) = schedule(Fiber(k))

    /**
     * polls the scheduler until [cond] returns false
     */
    fun start(cond: BooleanSupplier)

    /**
     * Cancels all active [Fiber]s
     */
    fun shutdown()

    companion object {
        private val schedulerCallstack = ThreadLocal.withInitial<Cons<Scheduler>?> { null }

        /**
         * WARNING: not for general use
         */
        fun pushScheduler(scheduler: Scheduler) {
            schedulerCallstack.set(
                Cons.cons(scheduler, schedulerCallstack.get())
            )
        }

        /**
         * WARNING: not for general use
         */
        fun popScheduler() {
            val cons =
                checkNotNull(schedulerCallstack.get()) { "attempted to pop Scheduler off an empty callstack" }
            schedulerCallstack.set(cons.cdr)
            Cons.drop(cons)
        }

        @JvmStatic
        @get:JvmName("currentScheduler")
        val currentScheduler: Scheduler
            get() = checkNotNull(schedulerCallstack.get()) { "attempted to get current Scheduler from empty callstack" }.car
    }

    class Standard : Scheduler {
        private var collector = Q<Fiber>()
        private var runner = Q<Fiber>()

        private fun swap() {
            val tmp = runner
            runner = collector
            collector = tmp
        }

        override fun schedule(fiber: Fiber) = run {
            collector.append(fiber)
            fiber
        }

        fun step() = run {
            pushScheduler(this)
            swap()
            while (!runner.empty()) {
                val fiber = runner.pop()
                if (fiber.state != Fiber.State.ACTIVE) continue
                Fiber.UNRAVEL(fiber)
                if (fiber.state == Fiber.State.ACTIVE) collector.append(fiber)
            }
            popScheduler()
            collector.empty()
        }

        override fun start(cond: BooleanSupplier) {
            while (cond.asBoolean) step()
        }

        override fun shutdown() {
            while (!runner.empty() && !collector.empty()) {
                swap()
                while (!runner.empty()) {
                    val fiber = runner.pop()
                    if (fiber.state == Fiber.State.ACTIVE) Fiber.CANCEL(fiber)
                }
            }
        }
    }
}

