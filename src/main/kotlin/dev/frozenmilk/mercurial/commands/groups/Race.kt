package dev.frozenmilk.mercurial.commands.groups

import dev.frozenmilk.mercurial.commands.Command
import dev.frozenmilk.mercurial.commands.UnwindCommandStack
import dev.frozenmilk.mercurial.commands.rename

class Race
/**
 * [deadline] should not also be in [commands]
 *
 * if [deadline] is null, all [commands] [Command.end] when any command is [Command.finished]
 *
 * otherwise, all [commands] [Command.end] when [deadline] is [Command.finished]
 */
constructor(val deadline: Command?, commands: Collection<Command>) : Parallel(if (deadline != null) commands.plus(deadline) else commands) {
	/**
	 * [deadline] should not also be in [commands]
	 */
	constructor(deadline: Command?, vararg commands: Command) : this(deadline, commands.toList())

	/**
	 * non-mutating
	 */
	override fun addCommands(vararg commands: Command) = Race(deadline, this.commands.plus(commands))
	/**
	 * non-mutating
	 */
	override fun addCommands(commands: Collection<Command>) = Race(deadline, this.commands.plus(commands))
	/**
	 * non-mutating
	 */
	fun setDeadline(deadline: Command?) = Race(deadline, commands)
	override fun finished(): Boolean {
		return if (deadline != null) {
			try {
				deadline.finished()
			}
			catch (e: Throwable) {
				if (e !is UnwindCommandStack) throw UnwindCommandStack(deadline, this, "finished?", unwindStackTrace(deadline, "ERR"), e)
				else e.wrapAndRethrow(this)
			}
		}
		else commands.size != activeCommands.size
	}
	override fun unwindStackTrace(command: Command, sub: String): String {
		return if (command == this) sub
		else {
			if (deadline != null) "(${rename(javaClass.simpleName)} ${deadline.unwindStackTrace(command, sub)} (\n\t${ commands.filter { it != deadline }.joinToString(separator = "\n") { it.unwindStackTrace( command, sub ) }.replace("\n", "\n\t") }))"
			else "(${rename(javaClass.simpleName)} (\n\t${ commands.joinToString(separator = "\n") { it.unwindStackTrace( command, sub ) }.replace("\n", "\n\t") }))"
		}
	}
	override fun toString() =
		if (deadline != null) "(${rename(javaClass.simpleName)} $deadline (\n\t${commands.filter { it != deadline }.joinToString(separator = "\n").replace("\n", "\n\t")}))"
		else "(${rename(javaClass.simpleName)} (\n\t${commands.joinToString(separator = "\n").replace("\n", "\n\t")}))"
}