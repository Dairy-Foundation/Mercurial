package dev.frozenmilk.mercurial.commands.groups

import dev.frozenmilk.mercurial.commands.Command
import dev.frozenmilk.mercurial.commands.UnwindCommandStack
import dev.frozenmilk.mercurial.commands.rename

/**
 * CommandGroups manage command flow behind the scenes, and work to hold onto multiple commands
 */
abstract class CommandGroup : Command {
	/**
	 * all commands in this delegation
	 */
	abstract val commands: Collection<Command>

	/**
	 * the current active commands that are being delegated to
	 */
	private val _activeCommands = mutableListOf<Command>()

	/**
	 * read only list of commands
	 */
	val activeCommands: List<Command>
		get() = _activeCommands
	val initQueue = ArrayDeque<Command>()
	val endQueue = ArrayDeque<Pair<Command, Boolean>>()

	override fun execute() {
		_activeCommands.forEach { command ->
			val finished = try {
				command.finished()
			}
			catch (e: Throwable) {
				if (e !is UnwindCommandStack) throw UnwindCommandStack(command, this, "finished?", unwindStackTrace(command, "ERR"), e)
				else e.wrapAndRethrow(this)
			}
			if (finished) {
				endQueue.add(command to false)
			}
		}
		endQueue.asReversed().distinctBy { it.first }.asReversed().forEach { (command, cancel) ->
			try {
				command.end(cancel)
			}
			catch (e: Throwable) {
				if (e !is UnwindCommandStack) throw UnwindCommandStack(command, this, "end", unwindStackTrace(command, "ERR"), e)
				else e.wrapAndRethrow(this)
			}
			_activeCommands.remove(command)
		}
		endQueue.clear()
		initQueue.asReversed().distinct().asReversed().forEach { command ->
			try {
				command.initialise()
			}
			catch (e: Throwable) {
				if (e !is UnwindCommandStack) throw UnwindCommandStack(command, this, "initialise", unwindStackTrace(command, "ERR"), e)
				else e.wrapAndRethrow(this)
			}
			_activeCommands.add(command)
		}
		initQueue.clear()
		_activeCommands.forEach { command ->
			try {
				command.execute()
			}
			catch (e: Throwable) {
				if (e !is UnwindCommandStack) throw UnwindCommandStack(command, this, "execute", unwindStackTrace(command, "ERR"), e)
				else e.wrapAndRethrow(this)
			}
		}
	}

	override fun end(interrupted: Boolean) {
		_activeCommands.forEach { command ->
			try {
				command.end(interrupted)
			}
			catch (e: Throwable) {
				if (e !is UnwindCommandStack) throw UnwindCommandStack(command, this, "end", unwindStackTrace(command, "ERR"), e)
				else e.wrapAndRethrow(this)
			}
		}
		_activeCommands.clear()
	}

	override fun finished() = _activeCommands.isEmpty()

	final override val requirements: Set<Any> by lazy {
		commands.flatMap { it.requirements }.toSet()
	}
	final override val runStates by lazy {
		commands.flatMap { it.runStates }.toSet()
	}
	final override val interruptible: Boolean
		get() = _activeCommands.all { it.interruptible }

	override fun unwindStackTrace(command: Command, sub: String): String {
		return if (command == this) sub
		else { "(${rename(javaClass.simpleName)} (\n\t${commands.joinToString(separator = "\n") { it.unwindStackTrace(command, sub) }.replace("\n", "\n\t")}))" }
	}
	// turns "ParallelGroup" of "lambda1 lambda2" into "(parallel-group lambda1 lambda2)"
	// s-exprs!
	override fun toString() = "(${rename(javaClass.simpleName)} (\n\t${commands.joinToString(separator = "\n").replace("\n", "\n\t")}))"
}