package dev.frozenmilk.mercurial.commands.stateful

import dev.frozenmilk.dairy.core.wrapper.Wrapper
import dev.frozenmilk.mercurial.commands.Command
import dev.frozenmilk.mercurial.commands.Lambda
import dev.frozenmilk.mercurial.commands.StackUnwinder
import dev.frozenmilk.mercurial.commands.rename
import dev.frozenmilk.util.modifier.BiModifier
import dev.frozenmilk.util.modifier.Modifier
import java.util.Collections
import java.util.function.BiConsumer
import java.util.function.Consumer
import java.util.function.Function
import java.util.function.Predicate
import java.util.function.Supplier

/**
 * used to build [Lambda]s around some [state]
 */
open class StatefulLambda<STATE> private constructor(
	private val state: STATE,
	nameSupplier: Supplier<String>,
	stackUnwinder: StackUnwinder?,
	requirementsSupplier: Supplier<Set<Any>>,
	commandInit: Runnable,
	commandMethod: Runnable,
	commandFinish: Supplier<Boolean>,
	commandEnd: Consumer<Boolean>,
	interruptibleSupplier: Supplier<Boolean>,
	runStatesSupplier: Supplier<Set<Wrapper.OpModeState>>
) : Lambda(
	nameSupplier,
	stackUnwinder,
	requirementsSupplier,
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
	 * allowed to scheduled in `OpModeState.ACTIVE` (start and loop) only
	 *
	 * these are sensible defaults
	 *
	 * @param name should contain no whitespace, all whitespace will be replaced with '_', words will be split at capital letters and separated by '-'
	 *
	 * E.g.: MyCommand will become my-command
	 */
	constructor(name: String, state: STATE) : this(
		state,
		rename(name.trim().replace(REGEX, "_")).run {{ this }},
		null,
		DEFAULT_REQUIREMENTS,
		DEFAULT_RUNNABLE,
		DEFAULT_RUNNABLE,
		DEFAULT_TRUE,
		DEFAULT_CONSUME,
		DEFAULT_TRUE,
		DEFAULT_RUN_STATES
	)

	//
	// Unique
	//

	/**
	 * non-mutating, sets the init method, overriding the previous contents
	 *
	 * @param initialise the new initialise method of the command
	 * @return a new [StatefulLambda]
	 */
	fun setInit(initialise: Consumer<STATE>): StatefulLambda<STATE> {
		return StatefulLambda(
			state,
			nameSupplier,
			stackUnwinder,
			requirementSupplier,
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
	 * @return a new [StatefulLambda]
	 */
	fun setExecute(execute: Consumer<STATE>): StatefulLambda<STATE> {
		return StatefulLambda(
			state,
			nameSupplier,
			stackUnwinder,
			requirementSupplier,
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
	 * @return a new [StatefulLambda]
	 */
	fun setFinish(finish: Function<STATE, Boolean>): StatefulLambda<STATE> {
		return StatefulLambda(
			state,
			nameSupplier,
			stackUnwinder,
			requirementSupplier,
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
	 * @return a new [StatefulLambda]
	 */
	fun setEnd(end: BiConsumer<Boolean, STATE>): StatefulLambda<STATE> {
		return StatefulLambda(
			state,
			nameSupplier,
			stackUnwinder,
			requirementSupplier,
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
	 * @return a new [StatefulLambda]
	 */
	fun setInterruptible(interruptibleSupplier: Function<STATE, Boolean>): StatefulLambda<STATE> {
		return StatefulLambda(
			state,
			nameSupplier,
			stackUnwinder,
			requirementSupplier,
			commandInit,
			commandMethod,
			commandFinish,
			commandEnd,
			{ interruptibleSupplier.apply(state) },
			runStatesSupplier
		)
	}

	/**
	 * non-mutating, adds to the current interruptible method, passing the result of the pre-existing method to this one
	 *
	 * @param interruptible if interruption is allowed
	 * @return a new [StatefulLambda]
	 */
	fun addInterruptible(interruptible: BiModifier<Boolean, STATE>): StatefulLambda<STATE> {
		return StatefulLambda(
			state,
			nameSupplier,
			stackUnwinder,
			requirementSupplier,
			commandInit,
			commandMethod,
			commandFinish,
			commandEnd,
			{ interruptible.modify(this.interruptibleSupplier.get(), state) },
			runStatesSupplier
		)
	}

	/**
	 * non-mutating, adds to the current init method
	 *
	 * @param initialise the additional method to run after the preexisting init
	 * @return a new [StatefulLambda]
	 */
	fun addInit(initialise: Consumer<STATE>): StatefulLambda<STATE> {
		return StatefulLambda(
			state,
			nameSupplier,
			stackUnwinder,
			requirementSupplier,
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
	 * @return a new [StatefulLambda]
	 */
	fun addExecute(execute: Consumer<STATE>): StatefulLambda<STATE> {
		return StatefulLambda(
			state,
			nameSupplier,
			stackUnwinder,
			requirementSupplier,
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
	 * non-mutating, adds to the current finish method, passing the result of the pre-existing method to this one
	 *
	 * @param finish the additional condition to consider after the preexisting finish
	 * @return a new [StatefulLambda]
	 */
	fun addFinish(finish: BiModifier<Boolean, STATE>): StatefulLambda<STATE> {
		return StatefulLambda(
			state,
			nameSupplier,
			stackUnwinder,
			requirementSupplier,
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
	 * @return a new [StatefulLambda]
	 */
	fun addEnd(end: BiConsumer<Boolean, STATE>): StatefulLambda<STATE> {
		return StatefulLambda(
			state,
			nameSupplier,
			stackUnwinder,
			requirementSupplier,
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
	 * @return a new [StatefulLambda]
	 */
	override fun setInit(initialise: Runnable): StatefulLambda<STATE> {
		return StatefulLambda(
			state,
			nameSupplier,
			stackUnwinder,
			requirementSupplier,
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
	 * @return a new [StatefulLambda]
	 */
	override fun setExecute(execute: Runnable): StatefulLambda<STATE> {
		return StatefulLambda(
			state,
			nameSupplier,
			stackUnwinder,
			requirementSupplier,
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
	 * @return a new [StatefulLambda]
	 */
	override fun setFinish(finish: Supplier<Boolean>): StatefulLambda<STATE> {
		return StatefulLambda(
			state,
			nameSupplier,
			stackUnwinder,
			requirementSupplier,
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
	 * @return a new [StatefulLambda]
	 */
	override fun setEnd(end: Consumer<Boolean>): StatefulLambda<STATE> {
		return StatefulLambda(
			state,
			nameSupplier,
			stackUnwinder,
			requirementSupplier,
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
	 * @return a new [StatefulLambda]
	 */
	override fun setInterruptible(interruptible: Boolean): StatefulLambda<STATE> {
		return StatefulLambda(
			state,
			nameSupplier,
			stackUnwinder,
			requirementSupplier,
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
	 * @return a new [StatefulLambda]
	 */
	override fun setInterruptible(interruptibleSupplier: Supplier<Boolean>): StatefulLambda<STATE> {
		return StatefulLambda(
			state,
			nameSupplier,
			stackUnwinder,
			requirementSupplier,
			commandInit,
			commandMethod,
			commandFinish,
			commandEnd,
			interruptibleSupplier,
			runStatesSupplier
		)
	}

	/**
	 * non-mutating, adds to the current interruptible method, passing the result of the pre-existing method to this one
	 *
	 * @param interruptible the additional condition to consider after the preexisting interruptible
	 * @return a new [StatefulLambda]
	 */
	override fun addInterruptible(interruptible: Modifier<Boolean>): StatefulLambda<STATE> {
		return StatefulLambda(
			state,
			nameSupplier,
			stackUnwinder,
			requirementSupplier,
			commandInit,
			commandMethod,
			commandFinish,
			commandEnd,
			{ interruptible.modify(this.interruptibleSupplier.get()) },
			runStatesSupplier
		)
	}

	/**
	 * non-mutating, adds to the current requirements
	 *
	 * @param requirements the additional requirements
	 * @return a new [StatefulLambda]
	 */
	override fun addRequirements(vararg requirements: Any): StatefulLambda<STATE> {
		val union = this.requirements + requirements
		return StatefulLambda(
			state,
			nameSupplier,
			stackUnwinder,
			{ union },
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
	 * @param requirements the additional requirements
	 * @return a new [StatefulLambda]
	 */
	override fun addRequirements(requirements: Set<Any>): StatefulLambda<STATE> {
		val union = this.requirements + requirements
		return StatefulLambda(
			state,
			nameSupplier,
			stackUnwinder,
			{ union },
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
	 * @return a new [StatefulLambda]
	 */
	override fun addInit(initialise: Runnable): StatefulLambda<STATE> {
		return StatefulLambda(
			state,
			nameSupplier,
			stackUnwinder,
			requirementSupplier,
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
	 * @return a new [StatefulLambda]
	 */
	override fun addExecute(execute: Runnable): StatefulLambda<STATE> {
		return StatefulLambda(
			state,
			nameSupplier,
			stackUnwinder,
			requirementSupplier,
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
	 * non-mutating, adds to the current finish method, passing the result of the pre-existing method to this one
	 *
	 * @param finish the additional condition to consider after the preexisting finish
	 * @return a new [StatefulLambda]
	 */
	override fun addFinish(finish: Modifier<Boolean>): StatefulLambda<STATE> {
		return StatefulLambda(
			state,
			nameSupplier,
			stackUnwinder,
			requirementSupplier,
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
	 * @return a new [StatefulLambda]
	 */
	override fun addEnd(end: Consumer<Boolean>): StatefulLambda<STATE> {
		return StatefulLambda(
			state,
			nameSupplier,
			stackUnwinder,
			requirementSupplier,
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
	 * @return a new [StatefulLambda]
	 */
	override fun setRunStates(vararg runStates: Wrapper.OpModeState): StatefulLambda<STATE> {
		val runstatesSet: MutableSet<Wrapper.OpModeState> = HashSet(runStates.size)
		Collections.addAll(runstatesSet, *runStates)
		return StatefulLambda(
			state,
			nameSupplier,
			stackUnwinder,
			requirementSupplier,
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
	 * @return a new [StatefulLambda]
	 */
	override fun setRunStates(runStates: Set<Wrapper.OpModeState>): StatefulLambda<STATE> {
		return StatefulLambda(
			state,
			nameSupplier,
			stackUnwinder,
			requirementSupplier,
			commandInit,
			commandMethod,
			commandFinish,
			commandEnd,
			interruptibleSupplier
		) { runStates }
	}

	companion object {
		@JvmStatic
		fun <STATE> from(command: Command, state: STATE) = StatefulLambda(state, command::toString, command::unwindStackTrace, command::requirements, command::initialise, command::execute, command::finished, command::end, command::interruptible, command::runStates)
	}
}