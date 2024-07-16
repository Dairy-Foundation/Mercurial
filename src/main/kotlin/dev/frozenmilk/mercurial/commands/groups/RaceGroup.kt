package dev.frozenmilk.mercurial.commands.groups

import dev.frozenmilk.mercurial.commands.Command

/**
 * [deadline] should not also be in [commands]
 */
class RaceGroup @JvmOverloads constructor(val deadline: Command? = null, commands: Collection<Command>) : ParallelGroup(if (deadline != null) commands.plus(deadline) else commands) {
	@JvmOverloads constructor(deadline: Command? = null, vararg commands: Command) : this(deadline, commands.toList())

	override fun initialise() {
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