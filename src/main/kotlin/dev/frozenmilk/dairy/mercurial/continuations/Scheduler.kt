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


        private val SENTINEL_K: Continuation = object : Continuation {
            override fun apply() = SENTINEL_K
            override val stackTrace = null
            override fun toString() = "SENTINEL"
        }

        @JvmStatic
        @get:JvmName("SENTINEL")
        val SENTINEL = Fiber(SENTINEL_K)
    }

    class Standard : Scheduler {
        private val q = Q<Fiber>()

        override fun schedule(fiber: Fiber) = run {
            q.append(fiber)
            fiber
        }

        fun step() = run {
            pushScheduler(this)
            q.append(SENTINEL)
            while (true) {
                val fiber = q.pop()
                if (fiber == SENTINEL) break
                if (fiber.state != Fiber.State.ACTIVE) continue
                Fiber.UNRAVEL(fiber)
                if (fiber.state == Fiber.State.ACTIVE) q.append(fiber)
            }
            popScheduler()
            q.empty()
        }

        override fun start(cond: BooleanSupplier) {
            while (cond.asBoolean) step()
        }

        override fun shutdown() {
            while (!q.empty()) {
                val fiber = q.pop()
                if (fiber.state == Fiber.State.ACTIVE) Fiber.CANCEL(fiber)
            }
        }

        override fun toString() = q.toString()
    }
}

