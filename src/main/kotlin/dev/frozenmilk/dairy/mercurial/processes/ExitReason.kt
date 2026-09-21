package dev.frozenmilk.dairy.mercurial.processes

/**
 * a marker interface for reasons why a fiber might exit
 *
 * you can pass these to [Fiber.exit] in order to terminate the fiber
 *
 * note that the actual mechanics are more complex than that, so termination is not guaranteed
 *
 * additionally, termination is not guaranteed to target just one fiber
 */
abstract class ExitReason : ProcessStatus(false) {
    companion object {
        @JvmField
        val normally = Normally
        @JvmField
        val kill = Kill
        @JvmField
        val killed = Killed
        @JvmField
        val interrupt = Interrupt
    }

    /**
     * normal exit, as expected
     */
    data object Normally : ExitReason()

    /**
     * special kill signal
     *
     * sending this to a fiber to end it will bypass exit trapping if turned on
     *
     * this signal is not propagated directly, instead it is transformed into [Killed]
     *
     * to let linked fibers know that it has been killed with [Kill], but not [Kill] them
     */
    object Kill : ExitReason()

    /**
     * marker signal for [Kill]
     */
    object Killed : ExitReason()

    /**
     * generic interruption signal
     */
    object Interrupt : ExitReason()

    /**
     * bridges the java exception system with fibers
     *
     * used when a fiber throws an exception
     */
    data class Exceptionally(val e: Throwable) : ExitReason()
}
