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
	override fun initialise() {
		iterator = commands.iterator()
		if (iterator?.hasNext() == true) initQueue.add(iterator!!.next())
	}

	override fun finished(): Boolean {
		return !iterator!!.hasNext() && super.finished()
	}

	override fun end(interrupted: Boolean) {
		iterator = null
	}

	override fun schedule() {
		if (iterator?.hasNext() == true) initQueue.add(iterator!!.next())
		super.schedule()
	}
}