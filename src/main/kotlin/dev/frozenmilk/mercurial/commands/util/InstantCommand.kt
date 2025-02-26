package dev.frozenmilk.mercurial.commands.util

import dev.frozenmilk.dairy.core.wrapper.Wrapper
import dev.frozenmilk.mercurial.commands.Command
import java.lang.Runnable
import java.util.Collections.emptySet

/**
 * a command that runs a lmabda when scheduled
 */
class InstantCommand(val lambda: Runnable) : Command {

    override fun initialise() {
        lambda.run()
    }

    override fun execute() {
    }

    override fun end(interrupted: Boolean) {
    }

    override fun finished(): Boolean {
        return true
    }

    override val requirements: Set<Any> = emptySet()
    override val runStates: Set<Wrapper.OpModeState> = setOf(Wrapper.OpModeState.INIT, Wrapper.OpModeState.ACTIVE)
    override fun toString() = "InstantCommand"
}