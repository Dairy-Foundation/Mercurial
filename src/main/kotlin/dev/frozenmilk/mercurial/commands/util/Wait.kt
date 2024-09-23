package dev.frozenmilk.mercurial.commands.util

import dev.frozenmilk.dairy.core.wrapper.Wrapper
import dev.frozenmilk.mercurial.commands.Command

/**
 * a command that waits for the specified [duration], in seconds
 */
class Wait(val duration: Double) : Command {
	var startTime = 0L
		private set
	override fun initialise() {
		startTime = System.nanoTime()
	}

	override fun execute() {
	}

	override fun end(interrupted: Boolean) {
	}

	override fun finished(): Boolean {
		return (System.nanoTime() - startTime) / 1E9 >= duration
	}

	override val requirements: Set<Any> = emptySet()
	override val runStates: Set<Wrapper.OpModeState> = setOf(Wrapper.OpModeState.INIT, Wrapper.OpModeState.ACTIVE)
	override fun toString() = "(wait $duration)"
}