package dev.frozenmilk.mercurial.commands.groups

import dev.frozenmilk.mercurial.commands.Command

class RaceGroup @JvmOverloads constructor(val deadline: Command? = null, commands: Collection<Command>) : ParallelGroup(commands) {
	@JvmOverloads constructor(deadline: Command? = null, vararg commands: Command) : this(deadline, commands.toList())

	override fun initialise() {
		if (deadline != null && !commands.contains(deadline)) throw IllegalStateException("RaceGroup must contain its deadline command")
		super.initialise()
	}
	/**
	 * non-mutating
	 */
	override fun addCommands(vararg commands: Command) = RaceGroup(deadline, this.commands.plus(commands))
	/**
	 * non-mutating
	 */
	override fun addCommands(commands: Collection<Command>) = RaceGroup(deadline, this.commands.plus(commands))
	/**
	 * non-mutating
	 */
	@JvmOverloads
	fun setDeadline(deadline: Command? = null) = RaceGroup(deadline, commands)
	override fun finished(): Boolean {
		return deadline?.finished() ?: !commands.containsAll(activeCommands)
	}
}