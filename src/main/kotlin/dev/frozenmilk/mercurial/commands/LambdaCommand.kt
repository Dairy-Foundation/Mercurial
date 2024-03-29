package dev.frozenmilk.mercurial.commands

import dev.frozenmilk.util.modifier.Modifier
import dev.frozenmilk.dairy.core.wrapper.Wrapper
import dev.frozenmilk.mercurial.subsystems.Subsystem
import java.util.Collections
import java.util.function.Consumer
import java.util.function.Supplier

open class LambdaCommand protected constructor(
		protected val requiredSubsystemsSupplier: Supplier<Set<Subsystem>>,
		protected val commandInit: Runnable,
		protected val commandMethod: Runnable,
		protected val commandFinish: Supplier<Boolean>,
		protected val commandEnd: Consumer<Boolean>,
		protected val interruptibleSupplier: Supplier<Boolean>,
		protected val runStatesSupplier: Supplier<Set<Wrapper.OpModeState>>
) : Command {
	/**
	 * constructs a default lambda command with the following default behaviours:
	 *
	 * no requirements
	 *
	 * an empty init method
	 *
	 * an empty execute method
	 *
	 * instantly finishes
	 *
	 * an empty end method
	 *
	 * is interruptible
	 *
	 * allowed to run in LOOP only
	 *
	 * these are sensible defaults for a command that is meant to run in LOOP
	 */
	constructor() : this(
			Supplier<Set<Subsystem>> { DEFAULT_REQUIREMENTS },
			Runnable {},
			Runnable {},
			Supplier { true },
			Consumer<Boolean> { },
			Supplier { true },
			Supplier<Set<Wrapper.OpModeState>> { DEFAULT_RUN_STATES }
	)

	/**
	 * non-mutating, sets the requirements, overriding the previous contents
	 *
	 * @param requiredSubsystems subsystem requirements of this command
	 * @return a new [LambdaCommand]
	 */
	fun setRequirements(vararg requiredSubsystems: Subsystem): LambdaCommand {
		val requirements = HashSet<Subsystem>(requiredSubsystems.size)
		Collections.addAll(requirements, *requiredSubsystems)
		return LambdaCommand(
				{ requirements },
				commandInit,
				commandMethod,
				commandFinish,
				commandEnd,
				interruptibleSupplier,
				runStatesSupplier
		)
	}

	/**
	 * non-mutating, sets the requirements, overriding the previous contents
	 *
	 * @param requiredSubsystems subsystem requirements of this command
	 * @return a new [LambdaCommand]
	 */
	fun setRequirements(requiredSubsystems: Set<Subsystem>): LambdaCommand {
		return LambdaCommand(
				{ requiredSubsystems },
				commandInit,
				commandMethod,
				commandFinish,
				commandEnd,
				interruptibleSupplier,
				runStatesSupplier
		)
	}

	/**
	 * non-mutating, sets the init method, overriding the previous contents
	 *
	 * @param initialise the new initialise method of the command
	 * @return a new [LambdaCommand]
	 */
	open fun setInit(initialise: Runnable): LambdaCommand {
		return LambdaCommand(
				requiredSubsystemsSupplier,
				initialise,
				commandMethod,
				commandFinish,
				commandEnd,
				interruptibleSupplier,
				runStatesSupplier
		)
	}

	/**
	 * non-mutating, sets the execute method, overriding the previous contents
	 *
	 * @param execute the new execute method of the command
	 * @return a new [LambdaCommand]
	 */
	open fun setExecute(execute: Runnable): LambdaCommand {
		return LambdaCommand(
				requiredSubsystemsSupplier,
				commandInit,
				execute,
				commandFinish,
				commandEnd,
				interruptibleSupplier,
				runStatesSupplier
		)
	}

	/**
	 * non-mutating, sets the finish method, overriding the previous contents
	 *
	 * @param finish the new finish method of the command
	 * @return a new [LambdaCommand]
	 */
	open fun setFinish(finish: Supplier<Boolean>): LambdaCommand {
		return LambdaCommand(
				requiredSubsystemsSupplier,
				commandInit,
				commandMethod,
				finish,
				commandEnd,
				interruptibleSupplier,
				runStatesSupplier
		)
	}

	/**
	 * non-mutating, sets the end method, overriding the previous contents
	 *
	 * @param end the new end method of the command
	 * @return a new [LambdaCommand]
	 */
	open fun setEnd(end: Consumer<Boolean>): LambdaCommand {
		return LambdaCommand(
				requiredSubsystemsSupplier,
				commandInit,
				commandMethod,
				commandFinish,
				end,
				interruptibleSupplier,
				runStatesSupplier
		)
	}

	/**
	 * non-mutating, sets if interruption is allowed
	 *
	 * @param interruptible if interruption is allowed
	 * @return a new [LambdaCommand]
	 */
	open fun setInterruptible(interruptible: Boolean): LambdaCommand {
		return LambdaCommand(
				requiredSubsystemsSupplier,
				commandInit,
				commandMethod,
				commandFinish,
				commandEnd,
				{ interruptible },
				runStatesSupplier
		)
	}

	/**
	 * non-mutating, sets if interruption is allowed
	 *
	 * @param interruptibleSupplier if interruption is allowed
	 * @return a new [LambdaCommand]
	 */
	open fun setInterruptible(interruptibleSupplier: Supplier<Boolean>): LambdaCommand {
		return LambdaCommand(
				requiredSubsystemsSupplier,
				commandInit,
				commandMethod,
				commandFinish,
				commandEnd,
				interruptibleSupplier,
				runStatesSupplier
		)
	}

	/**
	 * non-mutating, adds additional if interruption is allowed conditions, either the preexisting method OR the new one returning true will allow interruption
	 *
	 * @param interruptibleSupplier if interruption is allowed
	 * @return a new [LambdaCommand]
	 */
	open fun addInterruptible(interruptibleSupplier: Supplier<Boolean>): LambdaCommand {
		return LambdaCommand(
				requiredSubsystemsSupplier,
				commandInit,
				commandMethod,
				commandFinish,
				commandEnd,
				{ this.interruptibleSupplier.get() || interruptibleSupplier.get() },
				runStatesSupplier
		)
	}

	/**
	 * non-mutating, adds to the current requirements
	 *
	 * @param requiredSubsystems the additional required subsystems
	 * @return a new [LambdaCommand]
	 */
	open fun addRequirements(vararg requiredSubsystems: Subsystem): LambdaCommand {
		val requirements: MutableSet<Subsystem> = this.requiredSubsystems.toMutableSet()
		Collections.addAll(requirements, *requiredSubsystems)
		return LambdaCommand(
				{ requirements },
				commandInit,
				commandMethod,
				commandFinish,
				commandEnd,
				interruptibleSupplier,
				runStatesSupplier
		)
	}

	/**
	 * non-mutating, adds to the current requirements
	 *
	 * @param requiredSubsystems the additional required subsystems
	 * @return a new [LambdaCommand]
	 */
	open fun addRequirements(requiredSubsystems: MutableSet<Subsystem>): LambdaCommand {
		requiredSubsystems.addAll(this.requiredSubsystems)
		return LambdaCommand(
				{ requiredSubsystems },
				commandInit,
				commandMethod,
				commandFinish,
				commandEnd,
				interruptibleSupplier,
				runStatesSupplier
		)
	}

	/**
	 * non-mutating, adds to the current init method
	 *
	 * @param initialise the additional method to run after the preexisting init
	 * @return a new [LambdaCommand]
	 */
	open fun addInit(initialise: Runnable): LambdaCommand {
		return LambdaCommand(
				requiredSubsystemsSupplier,
				{
					commandInit.run()
					initialise.run()
				},
				commandMethod,
				commandFinish,
				commandEnd,
				interruptibleSupplier,
				runStatesSupplier
		)
	}

	/**
	 * non-mutating, adds to the current execute method
	 *
	 * @param execute the additional method to run after the preexisting execute
	 * @return a new [LambdaCommand]
	 */
	open fun addExecute(execute: Runnable): LambdaCommand {
		return LambdaCommand(
				requiredSubsystemsSupplier,
				commandInit,
				{
					commandMethod.run()
					execute.run()
				},
				commandFinish,
				commandEnd,
				interruptibleSupplier,
				runStatesSupplier
		)
	}

	/**
	 * non-mutating, adds to the current finish method, either the preexisting method OR the new one will end the command
	 *
	 * @param finish the additional condition to consider after the preexisting finish
	 * @return a new [LambdaCommand]
	 */
	open fun addFinish(finish: Supplier<Boolean>): LambdaCommand {
		return LambdaCommand(
				requiredSubsystemsSupplier,
				commandInit,
				commandMethod,
				{ commandFinish.get() || finish.get() },
				commandEnd,
				interruptibleSupplier,
				runStatesSupplier
		)
	}

	/**
	 * non-mutating, adds to the current finish method, passing the result of the pre-existing method to this one
	 *
	 * @param finish the additional condition to consider after the preexisting finish
	 * @return a new [LambdaCommand]
	 */
	open fun addFinish(finish: Modifier<Boolean>): LambdaCommand {
		return LambdaCommand(
				requiredSubsystemsSupplier,
				commandInit,
				commandMethod,
				{ finish.modify(commandFinish.get()) },
				commandEnd,
				interruptibleSupplier,
				runStatesSupplier
		)
	}

	/**
	 * non-mutating, adds to the current end method
	 *
	 * @param end the additional method to run after the preexisting end
	 * @return a new [LambdaCommand]
	 */
	open fun addEnd(end: Consumer<Boolean>): LambdaCommand {
		return LambdaCommand(
				requiredSubsystemsSupplier,
				commandInit,
				commandMethod,
				commandFinish,
				{ interrupted: Boolean ->
					commandEnd.accept(interrupted)
					end.accept(interrupted)
				},
				interruptibleSupplier,
				runStatesSupplier
		)
	}

	// Wrapper methods:
	override fun initialise() {
		commandInit.run()
	}

	override fun execute() {
		commandMethod.run()
	}

	override fun finished(): Boolean {
		return commandFinish.get()
	}

	override val requiredSubsystems: Set<Subsystem>
		get() = requiredSubsystemsSupplier.get()

	override fun end(interrupted: Boolean) {
		commandEnd.accept(interrupted)
	}

	override val interruptible: Boolean
		get() {
			return interruptibleSupplier.get()
		}

	override val runStates: Set<Wrapper.OpModeState>
		get() = runStatesSupplier.get()

	/**
	 * non-mutating, sets the RunStates, overriding the previous contents
	 *
	 * @param runStates allowed RunStates of the command
	 * @return a new [LambdaCommand]
	 */
	open fun setRunStates(vararg runStates: Wrapper.OpModeState): LambdaCommand {
		val runstatesSet: MutableSet<Wrapper.OpModeState> = HashSet(runStates.size)
		Collections.addAll(runstatesSet, *runStates)
		return LambdaCommand(
				requiredSubsystemsSupplier,
				commandInit,
				commandMethod,
				commandFinish,
				commandEnd,
				interruptibleSupplier
		) { runstatesSet }
	}

	/**
	 * non-mutating, sets the RunStates, overriding the previous contents
	 *
	 * @param runStates allowed RunStates of the command
	 * @return a new [LambdaCommand]
	 */
	open fun setRunStates(runStates: Set<Wrapper.OpModeState>): LambdaCommand {
		return LambdaCommand(
				requiredSubsystemsSupplier,
				commandInit,
				commandMethod,
				commandFinish,
				commandEnd,
				interruptibleSupplier
		) { runStates }
	}

	companion object {
		@JvmStatic
		protected val DEFAULT_REQUIREMENTS = HashSet<Subsystem>()
		@JvmStatic
		protected val DEFAULT_RUN_STATES = hashSetOf(Wrapper.OpModeState.ACTIVE)

		/**
		 * Composes a Command into a LambdaCommand
		 *
		 * @param command the command to convert
		 * @return a new [LambdaCommand] with the features of the argument
		 */
		fun from(command: Command): LambdaCommand {
			return if (command is LambdaCommand) command else LambdaCommand(command::requiredSubsystems, { command.initialise() }, { command.execute() }, { command.finished() }, { interrupted: Boolean -> command.end(interrupted) }, { command.interruptible }, command::runStates)
		}
	}
}
