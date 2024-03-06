package dev.frozenmilk.mercurial.commands.util

import dev.frozenmilk.mercurial.commands.Command
import java.util.function.BooleanSupplier

class ConditionalCommand(val conditional: BooleanSupplier, val trueCommand: Command, val falseCommand: Command) : Command {
	private var command = trueCommand
	override fun initialise() {
		command = if (conditional.asBoolean) trueCommand
		else falseCommand
		command.initialise()
	}
	override fun execute() = command.execute()
	override fun end(interrupted: Boolean) = command.end(interrupted)
	override fun finished() = command.finished()
	override val requiredSubsystems by lazy {
		trueCommand.requiredSubsystems.plus(falseCommand.requiredSubsystems)
	}
	override val runStates by lazy {
		trueCommand.runStates.plus(falseCommand.runStates)
	}
}