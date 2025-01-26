package dev.frozenmilk.mercurial.commands

import dev.frozenmilk.dairy.core.wrapper.Wrapper.OpModeState
import dev.frozenmilk.mercurial.Mercurial
import dev.frozenmilk.mercurial.commands.groups.Parallel
import dev.frozenmilk.mercurial.commands.groups.Race
import dev.frozenmilk.mercurial.commands.groups.Sequential
import dev.frozenmilk.mercurial.commands.util.Wait

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
	val requirements: Set<Any>

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

	fun with(vararg commands: Command): Command = Parallel(this, *commands)
	fun timeout(duration: Double): Command = Race(deadline = null, Wait(duration), this)
	fun raceWith(vararg commands: Command): Command = Race(deadline = null, this, *commands)
	fun asDeadline(vararg commands: Command): Command = Race(deadline = this, *commands)
	fun then(vararg commands: Command): Command = Sequential(this, *commands)

	fun unwindStackTrace(command: Command, sub: String): String = if (command == this) sub else this.toString()
	override fun toString(): String
}

/**
 * splits at capital letters
 */
val CAPITAL_SPLIT = Regex("(?=\\p{Lu})")
fun rename(commandName: String) : String {
	if (commandName.isBlank()) throw IllegalArgumentException("cannot have a blank command name")
	val trimmed = commandName.trim()
	return if (trimmed[0] == '\\' && trimmed.length != 1) trimmed.drop(1)
	else trimmed.split(CAPITAL_SPLIT).filter { it.isNotBlank() }.joinToString(separator = "-") { it.lowercase() }
}
