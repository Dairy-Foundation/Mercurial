package dev.frozenmilk.mercurial.commands.groups

import dev.frozenmilk.mercurial.commands.Command

/**
 * runs commands in sequence
 */
class Sequential(commands: Iterable<Command>) : CommandGroup() {
	constructor(vararg commands: Command) : this(commands.toList())
	/**
	 * non-mutating
	 */
	fun addCommands(vararg commands: Command) = Sequential(this.commands.plus(commands))
	/**
	 * non-mutating
	 */
	fun addCommands(commands: Collection<Command>) = Sequential(this.commands.plus(commands))
	private var iterator = commands.iterator()
	override val commands = commands.toList()
	override fun initialise() {
		iterator = commands.iterator()
		if (iterator.hasNext()) initQueue.add(iterator.next())
	}
	override fun finished(): Boolean {
		if (activeCommands.isEmpty() && iterator.hasNext()) {
			initQueue.add(iterator.next())
			return false
		}
		return super.finished()
	}
}