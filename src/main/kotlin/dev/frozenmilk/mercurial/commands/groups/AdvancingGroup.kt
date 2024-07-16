package dev.frozenmilk.mercurial.commands.groups

import dev.frozenmilk.mercurial.commands.Command

/**
 * like sequential group, but only advances when you [schedule] this
 */
class AdvancingGroup(commands: Iterable<Command>) : CommandGroup() {
	constructor(vararg commands: Command) : this(commands.toList())
	/**
	 * non-mutating
	 */
	fun addCommands(vararg commands: Command) = AdvancingGroup(this.commands.plus(commands))
	/**
	 * non-mutating
	 */
	fun addCommands(commands: Collection<Command>) = AdvancingGroup(this.commands.plus(commands))
	override val commands = commands.toList()
	private var iterator: Iterator<Command>? = null
	private fun advance() {
		if (iterator?.hasNext() == true) {
			activeCommands.forEach { endQueue.add(it to true) }
			initQueue.add(iterator!!.next())
		}
	}
	override fun initialise() {
		iterator = commands.iterator()
		advance()
	}

	override fun finished(): Boolean {
		return iterator?.hasNext() != true && super.finished()
	}

	override fun end(interrupted: Boolean) {
		iterator = null
	}

	override fun schedule() {
		advance()
		super.schedule()
	}
}