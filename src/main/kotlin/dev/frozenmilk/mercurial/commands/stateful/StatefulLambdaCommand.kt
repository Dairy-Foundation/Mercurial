package dev.frozenmilk.mercurial.commands.stateful

import dev.frozenmilk.dairy.core.wrapper.Wrapper
import dev.frozenmilk.mercurial.commands.Command
import dev.frozenmilk.mercurial.commands.LambdaCommand
import dev.frozenmilk.mercurial.subsystems.Subsystem
import dev.frozenmilk.util.modifier.BiModifier
import dev.frozenmilk.util.modifier.Modifier
import java.util.Collections
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier

/**
 * used to build [LambdaCommand]s around some [state]
 */
class StatefulLambdaCommand<STATE> private constructor(
		private val state: STATE,
		requiredSubsystemsSupplier: Supplier<Set<Subsystem>>,
		commandInit: Runnable,
		commandMethod: Runnable,
		commandFinish: Supplier<Boolean>,
		commandEnd: Consumer<Boolean>,
		interruptibleSupplier: Supplier<Boolean>,
		runStatesSupplier: Supplier<Set<Wrapper.OpModeState>>
) : LambdaCommand(
		requiredSubsystemsSupplier,
		commandInit,
		commandMethod,
		commandFinish,
		commandEnd,
		interruptibleSupplier,
		runStatesSupplier
) {
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
	constructor(state: STATE) : this(
			state,
			Supplier<Set<Subsystem>> { DEFAULT_REQUIREMENTS },
			Runnable {},
			Runnable {},
			Supplier { true },
			Consumer<Boolean> { },
			Supplier { true },
			Supplier<Set<Wrapper.OpModeState>> { DEFAULT_RUN_STATES }
	)

	//
	// Unique
	//

	/**
	 * non-mutating, sets the init method, overriding the previous contents
	 *
	 * @param initialise the new initialise method of the command
	 * @return a new [StatefulLambdaCommand]
	 */
	fun setInit(initialise: Consumer<STATE>): StatefulLambdaCommand<STATE> {
		return StatefulLambdaCommand(
				state,
				requiredSubsystemsSupplier,
				{ initialise.accept(state) },
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
	 * @return a new [StatefulLambdaCommand]
	 */
	fun setExecute(execute: Consumer<STATE>): StatefulLambdaCommand<STATE> {
		return StatefulLambdaCommand(
				state,
				requiredSubsystemsSupplier,
				commandInit,
				{ execute.accept(state) },
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
	 * @return a new [StatefulLambdaCommand]
	 */
	fun setFinish(finish: Function<STATE, Boolean>): StatefulLambdaCommand<STATE> {
		return StatefulLambdaCommand(
				state,
				requiredSubsystemsSupplier,
				commandInit,
				commandMethod,
				{ finish.apply(state) },
				commandEnd,
				interruptibleSupplier,
				runStatesSupplier
		)
	}

	/**
	 * non-mutating, sets the end method, overriding the previous contents
	 *
	 * @param end the new end method of the command
	 * @return a new [StatefulLambdaCommand]
	 */
	fun setEnd(end: BiConsumer<Boolean, STATE>): StatefulLambdaCommand<STATE> {
		return StatefulLambdaCommand(
				state,
				requiredSubsystemsSupplier,
				commandInit,
				commandMethod,
				commandFinish,
				{ end.accept(it, state) },
				interruptibleSupplier,
				runStatesSupplier
		)
	}

	/**
	 * non-mutating, sets if interruption is allowed
	 *
	 * @param interruptibleSupplier if interruption is allowed
	 * @return a new [StatefulLambdaCommand]
	 */
	fun setInterruptible(interruptibleSupplier: Function<STATE, Boolean>): StatefulLambdaCommand<STATE> {
		return StatefulLambdaCommand(
				state,
				requiredSubsystemsSupplier,
				commandInit,
				commandMethod,
				commandFinish,
				commandEnd,
				{ interruptibleSupplier.apply(state) },
				runStatesSupplier
		)
	}

	/**
	 * non-mutating, adds additional if interruption is allowed conditions, either the preexisting method OR the new one returning true will allow interruption
	 *
	 * @param interruptibleSupplier if interruption is allowed
	 * @return a new [StatefulLambdaCommand]
	 */
	fun addInterruptible(interruptibleSupplier: Function<STATE, Boolean>): StatefulLambdaCommand<STATE> {
		return StatefulLambdaCommand(
				state,
				requiredSubsystemsSupplier,
				commandInit,
				commandMethod,
				commandFinish,
				commandEnd,
				{ this.interruptibleSupplier.get() || interruptibleSupplier.apply(state) },
				runStatesSupplier
		)
	}

	/**
	 * non-mutating, adds to the current init method
	 *
	 * @param initialise the additional method to run after the preexisting init
	 * @return a new [StatefulLambdaCommand]
	 */
	fun addInit(initialise: Consumer<STATE>): StatefulLambdaCommand<STATE> {
		return StatefulLambdaCommand(
				state,
				requiredSubsystemsSupplier,
				{
					commandInit.run()
					initialise.accept(state)
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
	 * @return a new [StatefulLambdaCommand]
	 */
	fun addExecute(execute: Consumer<STATE>): StatefulLambdaCommand<STATE> {
		return StatefulLambdaCommand(
				state,
				requiredSubsystemsSupplier,
				commandInit,
				{
					commandMethod.run()
					execute.accept(state)
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
	 * @return a new [StatefulLambdaCommand]
	 */
	fun addFinish(finish: Predicate<STATE>): StatefulLambdaCommand<STATE> {
		return StatefulLambdaCommand(
				state,
				requiredSubsystemsSupplier,
				commandInit,
				commandMethod,
				{ commandFinish.get() || finish.test(state) },
				commandEnd,
				interruptibleSupplier,
				runStatesSupplier
		)
	}

	/**
	 * non-mutating, adds to the current finish method, passing the result of the pre-existing method to this one
	 *
	 * @param finish the additional condition to consider after the preexisting finish
	 * @return a new [StatefulLambdaCommand]
	 */
	fun addFinish(finish: BiModifier<Boolean, STATE>): StatefulLambdaCommand<STATE> {
		return StatefulLambdaCommand(
				state,
				requiredSubsystemsSupplier,
				commandInit,
				commandMethod,
				{ finish.modify(commandFinish.get(), state) },
				commandEnd,
				interruptibleSupplier,
				runStatesSupplier
		)
	}

	/**
	 * non-mutating, adds to the current end method
	 *
	 * @param end the additional method to run after the preexisting end
	 * @return a new [StatefulLambdaCommand]
	 */
	fun addEnd(end: BiConsumer<Boolean, STATE>): StatefulLambdaCommand<STATE> {
		return StatefulLambdaCommand(
				state,
				requiredSubsystemsSupplier,
				commandInit,
				commandMethod,
				commandFinish,
				{ interrupted: Boolean ->
					commandEnd.accept(interrupted)
					end.accept(interrupted, state)
				},
				interruptibleSupplier,
				runStatesSupplier
		)
	}

	//
	// LambdaCommand overrides
	//
	/**
	 * non-mutating, sets the init method, overriding the previous contents
	 *
	 * @param initialise the new initialise method of the command
	 * @return a new [StatefulLambdaCommand]
	 */
	override fun setInit(initialise: Runnable): StatefulLambdaCommand<STATE> {
		return StatefulLambdaCommand(
				state,
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
	 * @return a new [StatefulLambdaCommand]
	 */
	override fun setExecute(execute: Runnable): StatefulLambdaCommand<STATE> {
		return StatefulLambdaCommand(
				state,
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
	 * @return a new [StatefulLambdaCommand]
	 */
	override fun setFinish(finish: Supplier<Boolean>): StatefulLambdaCommand<STATE> {
		return StatefulLambdaCommand(
				state,
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
	 * @return a new [StatefulLambdaCommand]
	 */
	override fun setEnd(end: Consumer<Boolean>): StatefulLambdaCommand<STATE> {
		return StatefulLambdaCommand(
				state,
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
	 * @return a new [StatefulLambdaCommand]
	 */
	override fun setInterruptible(interruptible: Boolean): StatefulLambdaCommand<STATE> {
		return StatefulLambdaCommand(
				state,
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
	 * @return a new [StatefulLambdaCommand]
	 */
	override fun setInterruptible(interruptibleSupplier: Supplier<Boolean>): StatefulLambdaCommand<STATE> {
		return StatefulLambdaCommand(
				state,
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
	 * @return a new [StatefulLambdaCommand]
	 */
	override fun addInterruptible(interruptibleSupplier: Supplier<Boolean>): StatefulLambdaCommand<STATE> {
		return StatefulLambdaCommand(
				state,
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
	 * @return a new [StatefulLambdaCommand]
	 */
	override fun addRequirements(vararg requiredSubsystems: Subsystem): StatefulLambdaCommand<STATE> {
		val requirements: MutableSet<Subsystem> = this.requiredSubsystems.toMutableSet()
		Collections.addAll(requirements, *requiredSubsystems)
		return StatefulLambdaCommand(
				state,
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
	 * @return a new [StatefulLambdaCommand]
	 */
	override fun addRequirements(requiredSubsystems: MutableSet<Subsystem>): StatefulLambdaCommand<STATE> {
		requiredSubsystems.addAll(this.requiredSubsystems)
		return StatefulLambdaCommand(
				state,
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
	 * @return a new [StatefulLambdaCommand]
	 */
	override fun addInit(initialise: Runnable): StatefulLambdaCommand<STATE> {
		return StatefulLambdaCommand(
				state,
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
	 * @return a new [StatefulLambdaCommand]
	 */
	override fun addExecute(execute: Runnable): StatefulLambdaCommand<STATE> {
		return StatefulLambdaCommand(
				state,
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
	 * @return a new [StatefulLambdaCommand]
	 */
	override fun addFinish(finish: Supplier<Boolean>): StatefulLambdaCommand<STATE> {
		return StatefulLambdaCommand(
				state,
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
	 * @return a new [StatefulLambdaCommand]
	 */
	override fun addFinish(finish: Modifier<Boolean>): StatefulLambdaCommand<STATE> {
		return StatefulLambdaCommand(
				state,
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
	 * @return a new [StatefulLambdaCommand]
	 */
	override fun addEnd(end: Consumer<Boolean>): StatefulLambdaCommand<STATE> {
		return StatefulLambdaCommand(
				state,
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

	/**
	 * non-mutating, sets the RunStates, overriding the previous contents
	 *
	 * @param runStates allowed RunStates of the command
	 * @return a new [StatefulLambdaCommand]
	 */
	override fun setRunStates(vararg runStates: Wrapper.OpModeState): StatefulLambdaCommand<STATE> {
		val runstatesSet: MutableSet<Wrapper.OpModeState> = HashSet(runStates.size)
		Collections.addAll(runstatesSet, *runStates)
		return StatefulLambdaCommand(
				state,
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
	 * @return a new [StatefulLambdaCommand]
	 */
	override fun setRunStates(runStates: Set<Wrapper.OpModeState>): StatefulLambdaCommand<STATE> {
		return StatefulLambdaCommand(
				state,
				requiredSubsystemsSupplier,
				commandInit,
				commandMethod,
				commandFinish,
				commandEnd,
				interruptibleSupplier
		) { runStates }
	}
}