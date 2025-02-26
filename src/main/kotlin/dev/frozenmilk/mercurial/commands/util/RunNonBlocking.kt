package dev.frozenmilk.mercurial.commands.util

import dev.frozenmilk.dairy.core.wrapper.Wrapper
import dev.frozenmilk.mercurial.commands.Command
import java.lang.Runnable

/**
 * a command that runs without blocking the command group
 */
class RunNonBlocking(val command: Command) : Command {

    override fun initialise() {
        command.schedule()
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
    override fun toString() = "RunNonBlocking"
}