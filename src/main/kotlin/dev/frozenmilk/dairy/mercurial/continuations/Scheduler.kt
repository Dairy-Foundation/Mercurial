package dev.frozenmilk.dairy.mercurial.continuations

import dev.frozenmilk.util.collections.Cons
import java.util.function.BooleanSupplier

interface Scheduler {
    /**
     * run [k] and return the [Fiber] its running in
     */
    fun schedule(k: Continuation): Fiber

    /**
     * polls the scheduler until [cond] returns false
     */
    fun start(cond: BooleanSupplier)

    /**
     * Cancels all active [Fiber]s
     */
    fun shutdown()

    class Standard : Scheduler {
        private var fibers: Cons<Fiber>? = null

        override fun schedule(k: Continuation) = Fiber(k).also { fiber ->
            fibers = Cons.cons(fiber, fibers)
        }

        fun step() = run {
            fibers = Cons.filter(fibers) { fiber ->
                if (fiber.state != Fiber.State.ACTIVE) false
                else {
                    Fiber.UNRAVEL(fiber)
                    fiber.state == Fiber.State.ACTIVE
                }
            }
            fibers == null
        }

        override fun start(cond: BooleanSupplier) {
            while (cond.asBoolean) step()
        }

        override fun shutdown() {
            Cons.drainForEach(fibers) { fiber ->
                if (fiber.state == Fiber.State.ACTIVE) Fiber.CANCEL(fiber)
            }
        }
    }
}
