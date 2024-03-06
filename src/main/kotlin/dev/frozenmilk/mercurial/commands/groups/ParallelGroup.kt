package dev.frozenmilk.mercurial.commands.groups

import dev.frozenmilk.mercurial.commands.Command

/**
 * runs commands in parallel
 */
open class ParallelGroup(override val commands: Collection<Command>) : CommandGroup() {
	constructor(vararg commands: Command) : this(commands.toList())
	/**
	 * non-mutating
	 */
	open fun addCommands(vararg commands: Command) = ParallelGroup(this.commands.plus(commands))
	/**
	 * non-mutating
	 */
	open fun addCommands(commands: Collection<Command>) = ParallelGroup(this.commands.plus(commands))
	override fun initialise() {
		initQueue.addAll(commands)
	}
}