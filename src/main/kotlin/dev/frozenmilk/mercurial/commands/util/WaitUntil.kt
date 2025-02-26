package dev.frozenmilk.mercurial.commands.util

import dev.frozenmilk.dairy.core.wrapper.Wrapper
import dev.frozenmilk.mercurial.commands.Command
import java.lang.Runnable
import java.util.function.BooleanSupplier

/**
 * a command that waits until the supplier returns true
 */
class WaitUntil(val supplier: BooleanSupplier) : Command {
    override fun initialise() {
    }

    override fun execute() {
    }

    override fun end(interrupted: Boolean) {
    }

    override fun finished(): Boolean {
        return supplier.asBoolean
    }

    override val requirements: Set<Any> = emptySet()
    override val runStates: Set<Wrapper.OpModeState> = setOf(Wrapper.OpModeState.INIT, Wrapper.OpModeState.ACTIVE)
    override fun toString() = "WaitUntil"
}