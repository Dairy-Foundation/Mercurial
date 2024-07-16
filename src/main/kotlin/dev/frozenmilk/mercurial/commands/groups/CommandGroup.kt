package dev.frozenmilk.mercurial.commands.groups

import dev.frozenmilk.mercurial.commands.Command

/**
 * Delegators manage command flow behind the scenes, and work to hold onto multiple commands
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
	val activeCommands
		get() = _activeCommands.toList()
	val initQueue = ArrayDeque<Command>()
	val endQueue = ArrayDeque<Pair<Command, Boolean>>()

	override fun execute() {
		_activeCommands.forEach { if (it.finished()) endQueue.add(it to false) }
		endQueue.asReversed().distinctBy { it.first }.asReversed().forEach {
			it.first.end(it.second)
			_activeCommands.remove(it.first)
		}
		endQueue.clear()
		initQueue.asReversed().distinct().asReversed().forEach {
			it.initialise()
			_activeCommands.add(it)
		}
		initQueue.clear()
		_activeCommands.forEach { it.execute() }
	}

	override fun end(interrupted: Boolean) {
		_activeCommands.forEach { it.end(interrupted) }
		_activeCommands.clear()
	}

	override fun finished() = _activeCommands.isEmpty()

	final override val requiredSubsystems by lazy {
		commands.flatMap { it.requiredSubsystems }.toSet()
	}
	final override val runStates by lazy {
		commands.flatMap { it.runStates }.toSet()
	}
}