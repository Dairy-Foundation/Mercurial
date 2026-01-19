package dev.frozenmilk.dairy.mercurial.continuations.mutexes

import dev.frozenmilk.dairy.mercurial.continuations.Fiber
import dev.frozenmilk.dairy.mercurial.continuations.registers.VarRegister
import dev.frozenmilk.util.collections.Cons

class Mutex<PRIORITY, T>(
    private val prioritiser: Mutexes.Prioritiser<PRIORITY>,
    internal var value: T,
) {
    internal val register = VarRegister<T>()

    private inner class Frame(val fiber: Fiber, val priority: PRIORITY)

    private var callstack: Cons<Frame>? = null

    /**
     * returns true if the current holder of the mutex is higher in the callstack
     */
    private fun inCallstack() = run {
        val curr = callstack?.car
        if (curr == null) true
        else Fiber.inCallstack(curr.fiber)
    }

    internal fun acquire(priority: PRIORITY, rejected: Runnable) {
        if (inCallstack()) callstack = Cons.cons(Frame(Fiber.currentFiber, priority), callstack)
        else {
            var interruptible = true
            var prev: Frame? = null
            var curr = callstack
            while (interruptible && curr != null && !Fiber.inCallstack(curr.car.fiber)) {
                interruptible = prioritiser.greaterPriority(priority, curr.car.priority)
                prev = curr.car
                curr = curr.cdr
            }
            if (!interruptible || (curr != null && !Fiber.inCallstack(curr.car.fiber))) rejected.run()
            else Fiber.CANCEL(prev!!.fiber)
        }
    }

    internal fun popFiber(fiber: Fiber) {
        if (callstack?.car?.fiber != fiber) return
        val cons = checkNotNull(callstack) { "attempted to pop fiber off an empty callstack" }
        callstack = cons.cdr
        Cons.drop(cons)
    }
}