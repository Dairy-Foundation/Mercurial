package dev.frozenmilk.mercurial.commands

import dev.frozenmilk.util.modifier.Modifier
import dev.frozenmilk.dairy.core.wrapper.Wrapper
import java.util.Collections
import java.util.function.Consumer
import java.util.function.Supplier

open class Lambda protected constructor(
	protected val nameSupplier: Supplier<String>,
	protected val stackUnwinder: StackUnwinder?,
	protected val requirementSupplier: Supplier<Set<Any>>,
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
	 * allowed to scheduled in `OpModeState.ACTIVE` (start and loop) only
	 *
	 * these are sensible defaults
	 *
	 * @param name should contain no whitespace, all whitespace will be replaced with '_', words will be split at capital letters and separated by '-'
	 *
	 * E.g.: MyCommand will become my-command
	 */
	constructor(name: String) : this(
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

	/**
	 * non-mutating, sets the requirements, overriding the previous contents
	 *
	 * @param requirements requirements of this command
	 * @return a new [Lambda]
	 */
	fun setRequirements(vararg requirements: Any): Lambda {
		val set = hashSetOf(*requirements)
		return Lambda(
			nameSupplier,
			stackUnwinder,
			{ set },
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
	 * @param requirements requirements of this command
	 * @return a new [Lambda]
	 */
	fun setRequirements(requirements: Set<Any>): Lambda {
		return Lambda(
			nameSupplier,
			stackUnwinder,
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
	 * non-mutating, sets the init method, overriding the previous contents
	 *
	 * @param initialise the new initialise method of the command
	 * @return a new [Lambda]
	 */
	open fun setInit(initialise: Runnable): Lambda {
		return Lambda(
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
	 * @return a new [Lambda]
	 */
	open fun setExecute(execute: Runnable): Lambda {
		return Lambda(
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
	 * @return a new [Lambda]
	 */
	open fun setFinish(finish: Supplier<Boolean>): Lambda {
		return Lambda(
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
	 * @return a new [Lambda]
	 */
	open fun setEnd(end: Consumer<Boolean>): Lambda {
		return Lambda(
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
	 * @return a new [Lambda]
	 */
	open fun setInterruptible(interruptible: Boolean): Lambda {
		return Lambda(
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
	 * @return a new [Lambda]
	 */
	open fun setInterruptible(interruptibleSupplier: Supplier<Boolean>): Lambda {
		return Lambda(
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
	 * @return a new [Lambda]
	 */
	open fun addInterruptible(interruptible: Modifier<Boolean>): Lambda {
		return Lambda(
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
	 * @return a new [Lambda]
	 */
	open fun addRequirements(vararg requirements: Any): Lambda {
		val union = this.requirements + requirements
		return Lambda(
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
	 * @return a new [Lambda]
	 */
	open fun addRequirements(requirements: Set<Any>): Lambda {
		val union = this.requirements + requirements
		return Lambda(
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
	 * @return a new [Lambda]
	 */
	open fun addInit(initialise: Runnable): Lambda {
		return Lambda(
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
	 * @return a new [Lambda]
	 */
	open fun addExecute(execute: Runnable): Lambda {
		return Lambda(
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
	 * @return a new [Lambda]
	 */
	open fun addFinish(finish: Modifier<Boolean>): Lambda {
		return Lambda(
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
	 * @return a new [Lambda]
	 */
	open fun addEnd(end: Consumer<Boolean>): Lambda {
		return Lambda(
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

	override val requirements: Set<Any>
		get() = requirementSupplier.get()

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
	 * @return a new [Lambda]
	 */
	open fun setRunStates(vararg runStates: Wrapper.OpModeState): Lambda {
		val runstatesSet: MutableSet<Wrapper.OpModeState> = HashSet(runStates.size)
		Collections.addAll(runstatesSet, *runStates)
		return Lambda(
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
	 * @return a new [Lambda]
	 */
	open fun setRunStates(runStates: Set<Wrapper.OpModeState>): Lambda {
		return Lambda(
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

	override fun unwindStackTrace(command: Command, sub: String) = stackUnwinder?.unwindStack(command, sub) ?: super.unwindStackTrace(command, sub)
	override fun toString() = nameSupplier.get()

	companion object {
		@JvmStatic
		protected val REGEX = Regex("\\s\\+")
		private val DEFAULT_REQUIREMENTS_DATA = HashSet<Any>()
		private val DEFAULT_RUN_STATES_DATA = hashSetOf(Wrapper.OpModeState.ACTIVE)
		@JvmStatic
		protected val DEFAULT_REQUIREMENTS = Supplier<Set<Any>> { DEFAULT_REQUIREMENTS_DATA }
		@JvmStatic
		protected val DEFAULT_RUN_STATES = Supplier<Set<Wrapper.OpModeState>> { DEFAULT_RUN_STATES_DATA }
		@JvmStatic
		protected val DEFAULT_RUNNABLE = Runnable {}
		@JvmStatic
		protected val DEFAULT_TRUE = Supplier { true }
		@JvmStatic
		protected val DEFAULT_CONSUME = Consumer<Boolean> {}

		/**
		 * Composes a Command into a LambdaCommand
		 *
		 * @param command the command to convert
		 * @return a new [Lambda] with the features of the argument
		 */
		@JvmStatic
		fun from(command: Command): Lambda {
			return if (command is Lambda) command else Lambda(command::toString, command::unwindStackTrace, command::requirements, command::initialise, command::execute, command::finished, command::end, command::interruptible, command::runStates)
		}
	}
}
