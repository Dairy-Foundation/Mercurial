package dev.frozenmilk.mercurial

import dev.frozenmilk.dairy.core.Feature
import dev.frozenmilk.dairy.core.dependency.Dependency
import dev.frozenmilk.dairy.core.dependency.annotation.SingleAnnotation
import dev.frozenmilk.dairy.core.wrapper.Wrapper
import dev.frozenmilk.dairy.core.wrapper.Wrapper.OpModeState
import dev.frozenmilk.dairy.pasteurized.Pasteurized
import dev.frozenmilk.mercurial.bindings.Binding
import dev.frozenmilk.mercurial.bindings.BoundGamepad
import dev.frozenmilk.mercurial.commands.Command
import dev.frozenmilk.mercurial.commands.MercurialException
import dev.frozenmilk.mercurial.commands.UnwindCommandStack
import dev.frozenmilk.util.cell.LazyCell
import java.lang.annotation.Inherited
import java.util.Collections
import java.util.WeakHashMap

/**
 * Mercurial's scheduler feature
 */
object Mercurial : Feature {
	//
	// Dependencies
	//
	override var dependency: Dependency<*> = SingleAnnotation(Attach::class.java)

	//
	// Fields
	//

	// External
	private val gamepad1Cell = LazyCell { BoundGamepad(Pasteurized.gamepad1) }
	@JvmStatic
	@get:JvmName("gamepad1")
	@set:JvmName("gamepad1")
	var gamepad1: BoundGamepad by gamepad1Cell
	private val gamepad2Cell = LazyCell { BoundGamepad(Pasteurized.gamepad2) }
	@JvmStatic
	@get:JvmName("gamepad2")
	@set:JvmName("gamepad2")
	var gamepad2: BoundGamepad by gamepad2Cell

	/**
	 * returns a snapshot of the currently active commands
	 */
	val activeCommandSnapshot
		get() = activeCommands.toList()

	// Internal
	private val toSchedule = mutableListOf<Command>()
	private val toEnd = mutableListOf<Pair<Boolean, Command>>()
	private val requirementMap = WeakHashMap<Any, Command>()
	private val defaultCommandMap = HashMap<Any, Command>()
	private val activeCommands = mutableListOf<Command>()
	private val bindings = mutableSetOf<Binding>()

	//
	// External Functions
	//

	@JvmStatic
	fun setDefaultCommand(requirement: Any, command: Command?) {
		if (command == null) defaultCommandMap.remove(requirement)
		else defaultCommandMap[requirement] = command
	}

	@JvmStatic
	fun getDefaultCommand(requirement: Any): Command? {
		return defaultCommandMap[requirement]
	}

	@JvmStatic
	fun isScheduled(command: Command): Boolean {
		return activeCommands.contains(command) || toSchedule.contains(command)
	}

	internal fun scheduleCommand(command: Command) {
		toSchedule.add(command)
	}

	internal fun cancelCommand(command: Command) {
		toEnd.add(true to command)
	}

	//
	// Internal Functions
	//

	internal fun registerBinding(binding: Binding) {
		bindings.add(binding)
	}

	private fun clearToEnd() {
		var i = 0
		while (i < toEnd.size) {
			try {
				val (interrupted, command) = toEnd[i]
				if (!isScheduled(command)) continue
				try {
					command.end(interrupted)
				}
				catch (e: Throwable) {
					if (e !is UnwindCommandStack) throw MercurialException("exception thrown in end:\n${command.toString().trim()}", e)
					else throw MercurialException("exception thrown in ${e.phase}:\n" +
							"caused by: ${e.causeCommand}\n" +
							"cause is marked as 'ERR' in this command s-expr\n" +
							e.message, e.cause)
				}
				finally {
					for (requirement in command.requirements) {
						requirementMap.remove(requirement, command)
					}
					activeCommands.remove(command)
				}
			}
			finally {
				i++
			}
		}

		toEnd.clear()
	}

	private fun clearToSchedule(state: OpModeState) {
		var i = 0
		while (i < toSchedule.size) {
			try {
				val command = toSchedule[i]
				if (!command.runStates.contains(state)) continue

				// if the subsystems required by the command are not required, register it
				if (Collections.disjoint(command.requirements, requirementMap.keys)) {
					initialiseCommand(command, command.requirements)
					continue
				}
				else {
					// for each subsystem required, check the command currently requiring it, and make sure that they can all be overwritten
					for (subsystem in command.requirements) {
						val requirer: Command? = requirementMap[subsystem]
						if (requirer != null && !requirer.interruptible) {
							continue
						}
					}
				}

				// cancel all required commands
				command.requirements.forEach {
					val requiringCommand = requirementMap[it]
					if(requiringCommand != null) { toEnd.add(true to requiringCommand) }
				}
			}
			finally {
				i++
			}
		}

		toSchedule.clear()
	}

	private fun initialiseCommand(command: Command, commandRequirements: Set<Any>) {
		for (requirement in commandRequirements) {
			requirementMap[requirement] = command
		}
		activeCommands.add(command)
		try {
			command.initialise()
		}
		catch (e: Throwable) {
			for (requirement in command.requirements) {
				requirementMap.remove(requirement, command)
			}
			activeCommands.remove(command)
			if (e !is UnwindCommandStack) throw MercurialException("exception thrown in initialise:\n${command.toString().trim()}", e)
			else throw MercurialException("exception thrown in ${e.phase}:\n" +
										"caused by: ${e.causeCommand}\n" +
										"cause is marked as 'ERR' in this command s-expr\n" +
										e.message, e.cause)
		}
	}

	private fun resolveSchedulerUpdate(runState: OpModeState) {
		// checks to see if any commands are finished, if so, cancels them
		activeCommands.forEach { command ->
			try {
				if (command.finished()) toEnd.add(false to command)
			}
			catch (e: Throwable) {
				for (requirement in command.requirements) {
					requirementMap.remove(requirement, command)
				}
				activeCommands.remove(command)
				if (e !is UnwindCommandStack) throw MercurialException("exception thrown in finished?:\n${command.toString().trim()}", e)
				else throw MercurialException("exception thrown in ${e.phase}:\n" +
						"caused by: ${e.causeCommand}\n" +
						"cause is marked as 'ERR' in this command s-expr\n" +
						e.message, e.cause)
			}
		}

		// cancel the commands
		clearToEnd()

		// schedule any default commands that can be scheduled
		val incomingRequirements = toSchedule.flatMap { it.requirements }.toSet() // try our hardest not to schedule default commands that have something coming in
		defaultCommandMap.forEach { (requirement, command) ->
			if (requirementMap[requirement] == null && !incomingRequirements.contains(requirement)) {
				command.schedule()
			}
		}

		// schedule new commands
		clearToSchedule(runState)

		// cancel the commands that got cancelled by the scheduling of new commands
		clearToEnd()

		// execute commands
		activeCommands.forEach { command ->
			try {
				command.execute()
			}
			catch (e: Throwable) {
				for (requirement in command.requirements) {
					requirementMap.remove(requirement, command)
				}
				activeCommands.remove(command)
				if (e !is UnwindCommandStack) throw MercurialException("exception thrown in execute:\n${command.toString().trim()}", e)
				else throw MercurialException("exception thrown in ${e.phase}:\n" +
											"caused by: ${e.causeCommand}\n" +
											"cause is marked as 'ERR' in this command s-expr\n" +
											e.message, e.cause)
			}
		}
	}

	private fun pollBindings() {
		bindings.forEach { it.run() }
	}

	//
	// Impl Feature
	//

	// clears state
	private fun clearState() {
		// checks to see if any commands are finished, if so, cancels them
		activeCommands.forEach { command ->
			try {
				if (command.finished()) toEnd.add(false to command)
			}
			catch (e: Throwable) {
				for (requirement in command.requirements) {
					requirementMap.remove(requirement, command)
				}
				activeCommands.remove(command)
				if (e !is UnwindCommandStack) throw MercurialException("exception thrown in finished?:\n${command.toString().trim()}", e)
				else throw MercurialException("exception thrown in ${e.phase}:\n" +
						"caused by: ${e.causeCommand}\n" +
						"cause is marked as 'ERR' in this command s-expr\n" +
						e.message, e.cause)
			}
		}
		clearToEnd()
		activeCommands.forEach { toEnd.add(true to it) }
		clearToEnd()
		toSchedule.clear()
		requirementMap.clear()
		defaultCommandMap.clear()
		activeCommands.clear()
		bindings.clear()
	}

	//override fun preUserInitHook(opMode: Wrapper) = clearState()
	override fun postUserInitHook(opMode: Wrapper) = resolveSchedulerUpdate(opMode.state)
	override fun preUserInitLoopHook(opMode: Wrapper) = pollBindings()
	override fun postUserInitLoopHook(opMode: Wrapper) = resolveSchedulerUpdate(opMode.state)
	override fun preUserStartHook(opMode: Wrapper) = pollBindings()
	override fun postUserStartHook(opMode: Wrapper) = resolveSchedulerUpdate(opMode.state)
	override fun preUserLoopHook(opMode: Wrapper) = pollBindings()
	override fun postUserLoopHook(opMode: Wrapper) = resolveSchedulerUpdate(opMode.state)
	override fun cleanup(opMode: Wrapper) {
		clearState()
		// invalidate gamepads
		gamepad1Cell.invalidate()
		gamepad2Cell.invalidate()
	}

	@Retention(AnnotationRetention.RUNTIME)
	@Target(AnnotationTarget.CLASS)
	@MustBeDocumented
	@Inherited
	annotation class Attach
}
