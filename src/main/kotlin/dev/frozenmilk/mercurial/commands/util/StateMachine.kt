package dev.frozenmilk.mercurial.commands.util

import dev.frozenmilk.mercurial.commands.Command
import dev.frozenmilk.mercurial.commands.UnwindCommandStack
import dev.frozenmilk.mercurial.commands.rename
import dev.frozenmilk.util.cell.LazyCell
import dev.frozenmilk.util.cell.RefCell
import java.util.function.BiFunction

class StateMachine<T: Any> private constructor(private val stateCell: RefCell<T>, private val map: Map<T, Command>) : Command {
	@SafeVarargs
	constructor(initialState: T, vararg states: Pair<T, Command>) : this(RefCell(initialState), mapOf(*states))
	var state by stateCell
	private var changedState = false
	init {
		stateCell.bind {
			changedState = true
		}
	}

	/**
	 * non-mutating
	 */
	fun withState(state: T, commandGenerator: BiFunction<RefCell<T>, String, Command>) = StateMachine(stateCell, map.plus(state to commandGenerator.apply(stateCell, "\\$state")))

	override fun initialise() {
	}

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
		if (changedState) {
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
		return !changedState && currentCommandCell.safeGet() == null
	}

	override val requirements by lazy {
		map.values.flatMap { it.requirements }.toSet()
	}
	override val runStates by lazy {
		map.values.flatMap { it.runStates }.toSet()
	}
	override val interruptible: Boolean
		get() = currentCommandCell.safeInvoke { it.interruptible } != false

	fun schedule(state: T) {
		this.state = state
		schedule()
	}

	override fun unwindStackTrace(command: Command, sub: String): String {
		return if (command == this) sub
		else { "(${rename(javaClass.simpleName)} $state (\n\t${map.map { (state, stateCommand) -> 
			if (state.toString() == stateCommand.toString()) stateCommand.unwindStackTrace(command, sub)
			else "(${state} ${stateCommand.unwindStackTrace(command, sub)})"
		}.joinToString(separator = "\n").replace("\n", "\n\t")}))" }
	}
	override fun toString() = "(${rename(javaClass.simpleName)} $state (\n\t${map.map { (state, stateCommand) ->
		val stateString = state.toString()
		if (stateString == stateCommand.toString()) stateString
		else "(${stateString} ${stateCommand})"
	}.joinToString(separator = "\n").replace("\n", "\n\t")}))"

	private val currentCommandCell = LazyCell {
		changedState = false
		val command = map[state] ?: throw NullPointerException("no command in map for state $state")
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