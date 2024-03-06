package dev.frozenmilk.mercurial.commands

import dev.frozenmilk.dairy.core.wrapper.Wrapper.OpModeState
import dev.frozenmilk.mercurial.Mercurial
import dev.frozenmilk.mercurial.subsystems.Subsystem

interface Command {
	/**
	 * Gets run once when a command is first scheduled
	 */
	fun initialise()

	/**
	 * Gets run once per loop until [finished]
	 */
	fun execute()

	/**
	 * Gets run once when [finished] or when interrupted
	 */
	fun end(interrupted: Boolean)

	/**
	 * the supplier for the natural end condition of the command
	 *
	 * true when the command should finish
	 */
	fun finished(): Boolean

	/**
	 * the set of subsystems required by this command
	 *
	 * this should not change
	 */
	val requiredSubsystems: Set<Subsystem>

	/**
	 * the set of OpMode [OpModeState]s during which this command is allowed to start, being in an invalid [OpModeState] does not prematurely finish a command
	 *
	 * this should not change
	 */
	val runStates: Set<OpModeState>

	/**
	 * @return if this command is allowed to be interrupted by others
	 */
	val interruptible: Boolean
		get() = true

	/**
	 * schedule the command with the scheduler
	 */
	fun schedule() {
		if (!Mercurial.isScheduled(this)) Mercurial.scheduleCommand(this)
	}

	/**
	 * Cancel the command if scheduled
	 */
	fun cancel() {
		if (Mercurial.isScheduled(this)) Mercurial.cancelCommand(this)
	}

	fun intoLambdaCommand(): LambdaCommand = LambdaCommand.from(this)
}
