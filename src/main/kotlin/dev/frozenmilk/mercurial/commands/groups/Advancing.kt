package dev.frozenmilk.mercurial.commands.groups

import dev.frozenmilk.mercurial.commands.Command
import dev.frozenmilk.mercurial.commands.UnwindCommandStack
import dev.frozenmilk.mercurial.commands.rename
import dev.frozenmilk.util.cell.InvalidatingCell
import dev.frozenmilk.util.cell.LazyCell

/**
 * like sequential group, but only advances when you [schedule] this
 */
class Advancing(private val commands: List<Command>) : Command {
	private val iteratorCell = InvalidatingCell({ commands.iterator() }, { _, ref -> !ref.hasNext() })
	private val iterator by iteratorCell
	private var advance = false
	constructor(vararg commands: Command) : this(commands.toList())

	/**
	 * non-mutating
	 */
	fun addCommands(vararg commands: Command) = Advancing(this.commands.plus(commands))

	/**
	 * non-mutating
	 */
	fun addCommands(commands: Collection<Command>) = Advancing(this.commands.plus(commands))

	fun advance() {
		advance = true
	}

	override fun initialise() {}

	override fun execute() {
		val finished = currentCommandCell.safeInvoke { command ->
			try {
				command.finished()
			}
			catch (e: Throwable) {
				if (e !is UnwindCommandStack) throw UnwindCommandStack(command, this, "finished?", unwindStackTrace(command, "ERR"), e)
				else e.wrapAndRethrow(this)
			}
		} ?: false
		if (finished) end(false)
		if (advance) {
			end(true)
			currentCommandCell.safeEvaluate()
		}
		currentCommandCell.safeInvoke { command ->
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
		currentCommandCell.safeInvoke { command ->
			try {
				command.end(interrupted)
			}
			catch (e: Throwable) {
				if (e !is UnwindCommandStack) throw UnwindCommandStack(command, this, "end", unwindStackTrace(command, "ERR"), e)
				else e.wrapAndRethrow(this)
			}
		}
		currentCommandCell.invalidate()
	}

	override fun finished(): Boolean {
		return !advance && currentCommandCell.safeGet() == null
	}

	override val requirements by lazy {
		commands.flatMap { it.requirements }.toSet()
	}
	override val runStates by lazy {
		commands.flatMap { it.runStates }.toSet()
	}
	override val interruptible: Boolean
		get() = currentCommandCell.safeInvoke { it.interruptible } ?: true

	override fun schedule() {
		advance()
		super.schedule()
	}

	override fun unwindStackTrace(command: Command, sub: String): String {
		return if (command == this) sub
		else { "(${rename(javaClass.simpleName)} (\n\t${commands.joinToString(separator = "\n") { it.unwindStackTrace(command, sub) }.replace("\n", "\n\t")}))" }
	}
	override fun toString() = "(${rename(javaClass.simpleName)} (\n\t${commands.joinToString(separator = "\n").replace("\n", "\n\t")}))"

	private val currentCommandCell = LazyCell {
		advance = false
		val command = iterator.next()
		try {
			command.initialise()
		}
		catch (e: Throwable) {
			if (e !is UnwindCommandStack) throw UnwindCommandStack(command, this, "initialise", unwindStackTrace(command, "ERR"), e)
			else e.wrapAndRethrow(this)
		}
		command
	}
}