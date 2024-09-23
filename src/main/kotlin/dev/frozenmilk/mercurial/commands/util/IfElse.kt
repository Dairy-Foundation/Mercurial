package dev.frozenmilk.mercurial.commands.util

import dev.frozenmilk.mercurial.commands.Command
import dev.frozenmilk.mercurial.commands.UnwindCommandStack
import java.util.function.BooleanSupplier

class IfElse(val condition: BooleanSupplier, val trueCommand: Command, val falseCommand: Command) : Command {
	private var command = trueCommand
	override fun initialise() {
		command = if (condition.asBoolean) trueCommand
		else falseCommand
		try {
			command.initialise()
		}
		catch (e: Throwable) {
			if (e !is UnwindCommandStack) throw UnwindCommandStack(command, this, "initialise", unwindStackTrace(command, "ERR"), e)
			else e.wrapAndRethrow(this)
		}
	}
	override fun execute() {
		try {
			command.execute()
		}
		catch (e: Throwable) {
			if (e !is UnwindCommandStack) throw UnwindCommandStack(command, this, "execute", unwindStackTrace(command, "ERR"), e)
			else e.wrapAndRethrow(this)
		}
	}
	override fun end(interrupted: Boolean) {
		try {
			command.end(interrupted)
		}
		catch (e: Throwable) {
			if (e !is UnwindCommandStack) throw UnwindCommandStack(command, this, "end", unwindStackTrace(command, "ERR"), e)
			else e.wrapAndRethrow(this)
		}
	}
	override fun finished() = try { command.finished() } catch (e: Throwable) {
		if (e !is UnwindCommandStack) throw UnwindCommandStack(command, this, "finished?", unwindStackTrace(command, "ERR"), e)
		else e.wrapAndRethrow(this)
	}
	override val requirements: Set<Any> by lazy {
		trueCommand.requirements.plus(falseCommand.requirements)
	}
	override val runStates by lazy {
		trueCommand.runStates.plus(falseCommand.runStates)
	}
	override val interruptible: Boolean
		get() = command.interruptible

	override fun unwindStackTrace(command: Command, sub: String) = if (command == this) sub else "(? ${command == trueCommand} ${trueCommand.unwindStackTrace(command, sub)} ${falseCommand.unwindStackTrace(command, sub)})"
	override fun toString() = "(? ${command == trueCommand} $trueCommand $falseCommand)"
}