package dev.frozenmilk.mercurial

import dev.frozenmilk.dairy.core.Feature
import dev.frozenmilk.dairy.core.dependency.annotation.SingleAnnotation
import dev.frozenmilk.dairy.core.wrapper.Wrapper
import dev.frozenmilk.dairy.core.wrapper.Wrapper.OpModeState
import dev.frozenmilk.dairy.pasteurized.Pasteurized
import dev.frozenmilk.mercurial.bindings.Binding
import dev.frozenmilk.mercurial.bindings.BoundGamepad
import dev.frozenmilk.mercurial.collections.emptyMutableWeakRefSet
import dev.frozenmilk.mercurial.commands.Command
import dev.frozenmilk.mercurial.subsystems.Subsystem
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
	override val dependency = SingleAnnotation(Attach::class.java)

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

	// Internal
	private val toSchedule = mutableListOf<Command>()
	private val toEnd = mutableListOf<Pair<Boolean, Command>>()
	private val subsystems = mutableSetOf<Subsystem>()
	private val enabledSubsystems = mutableSetOf<Subsystem>()
	private val requirementMap = WeakHashMap<Subsystem, Command>()
	private val defaultCommandMap = HashMap<Subsystem, Command?>()
	private val bindings = emptyMutableWeakRefSet<Binding>()

	//
	// External Functions
	//

	/**
	 * registers the subsystem, so that the Scheduler knows it exists, probably doesn't need to be called by hand
	 */
	@JvmStatic
	fun registerSubsystem(subsystem: Subsystem) = subsystems.add(subsystem)

	/**
	 * @see registerSubsystem
	 */
	@JvmStatic
	fun deregisterSubsystem(subsystem: Subsystem) {
		subsystems.remove(subsystem)
	}

	@JvmStatic
	fun setDefaultCommand(subsystem: Subsystem, command: Command?) {
		defaultCommandMap[subsystem] = command
	}

	@JvmStatic
	fun getDefaultCommand(subsystem: Subsystem): Command? {
		return defaultCommandMap[subsystem]
	}

	@JvmStatic
	fun isScheduled(command: Command): Boolean {
		return requirementMap.containsValue(command)
	}

	@JvmStatic
	fun scheduleCommand(command: Command) {
		toSchedule.add(command)
	}

	@JvmStatic
	fun cancelCommand(command: Command) {
		toEnd.add(true to command)
	}

	@JvmStatic
	fun isActive(subsystem: Subsystem) = enabledSubsystems.contains(subsystem)

	//
	// Internal Functions
	//

	internal fun registerBinding(binding: Binding) {
		bindings.add(binding)
	}

	private fun clearToEnd() {
		toEnd.forEach { (interrupted, command) ->
			if (!isScheduled(command)) return@forEach
			command.end(interrupted)
			for (requirement in command.requiredSubsystems) {
				requirementMap.remove(requirement, command)
			}
		}

		toEnd.clear()
	}

	private fun clearToSchedule(state: OpModeState) {
		var i = 0
		while (i < toSchedule.size) {
			val command = toSchedule[i]
			if (!command.runStates.contains(state)) continue

			// if the subsystems required by the command are not required, register it
			if (Collections.disjoint(command.requiredSubsystems, requirementMap.keys)) {
				initialiseCommand(command, command.requiredSubsystems)
				continue
			}
			else {
				// for each subsystem required, check the command currently requiring it, and make sure that they can all be overwritten
				for (subsystem in command.requiredSubsystems) {
					val requirer: Command? = requirementMap[subsystem]
					if (requirer != null && !requirer.interruptible) {
						continue
					}
				}
			}

			// cancel all required commands
			command.requiredSubsystems.forEach {
				val requiringCommand = requirementMap[it]
				if(requiringCommand != null) { toEnd.add(true to requiringCommand) }
			}
			i += 1
		}

		toSchedule.clear()
	}

	private fun initialiseCommand(command: Command, commandRequirements: Set<Subsystem>) {
		for (requirement in commandRequirements) {
			requirementMap[requirement] = command
		}
		command.initialise()
	}

	private fun resolveSchedulerUpdate(runState: OpModeState) {
		// checks to see if any commands are finished, if so, cancels them
		requirementMap.values.forEach {
			if (it.finished()) toEnd.add(false to it)
		}

		// cancel the commands
		clearToEnd()

		// schedule any default commands that can be scheduled
		val incomingRequirements = toSchedule.flatMap { it.requiredSubsystems }.toSet() // try our hardest not to schedule default commands that have something coming in
		enabledSubsystems.forEach {
			if (requirementMap[it] == null && !incomingRequirements.contains(it)) {
				defaultCommandMap[it]?.schedule()
			}
		}

		// schedule new commands
		clearToSchedule(runState)

		// cancel the commands that got cancelled by the scheduling of new commands
		clearToEnd()

		// execute commands
		requirementMap.values.forEach { it.execute() }
	}

	private fun pollBindings() {
		bindings.forEach { it.run() }
	}

	//
	// Hooks
	//
	override fun preUserInitHook(opMode: Wrapper) = subsystems
				.filter { it.isAttached() }
				.forEach { enabledSubsystems.add(it) }

	override fun preUserInitLoopHook(opMode: Wrapper) = pollBindings()
	override fun postUserInitLoopHook(opMode: Wrapper) = resolveSchedulerUpdate(opMode.state)
	override fun preUserLoopHook(opMode: Wrapper) = pollBindings()
	override fun postUserLoopHook(opMode: Wrapper) = resolveSchedulerUpdate(opMode.state)
	override fun postUserStopHook(opMode: Wrapper) {
		bindings.clear()
		// de-init all subsystems
		enabledSubsystems.clear()
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
