package dev.frozenmilk.mercurial.commands.util

import dev.frozenmilk.mercurial.commands.Command
import dev.frozenmilk.mercurial.commands.LambdaCommand
import dev.frozenmilk.mercurial.commands.groups.CommandGroup
import java.util.function.Supplier

/**
 * map-based selection command, use [schedule] with a selection argument to change the selection, and schedule the command at the same time if not selected
 *
 * ends when nothing is selected
 */
class SelectionCommand<T> @JvmOverloads constructor(private val initialSelectionSupplier: Supplier<T>, val selectionMap: Map<T, Command> = emptyMap()) : CommandGroup() {
	@JvmOverloads
	constructor(initialSelection: T, selectionMap: Map<T, Command> = emptyMap()) : this(Supplier { initialSelection }, selectionMap)
	/**
	 * non-mutating
	 */
	fun addCommands(selectionMap: Map<T, Command>) = SelectionCommand<T>(initialSelectionSupplier, this.selectionMap.plus(selectionMap))
	override val commands: Collection<Command>
		get() = selectionMap.map { it.value }
	override fun initialise() {}
	override fun schedule() {
		schedule(initialSelectionSupplier.get())
	}
	@JvmOverloads
	fun selectionCommand(t: T = initialSelectionSupplier.get()) = LambdaCommand().setInit { schedule(t) }
	fun schedule(selection: T) {
		activeCommands.forEach {
			endQueue.add(it to true)
		}
		val selected = selectionMap[selection]
		if (selected != null) initQueue.add(selected)
		super.schedule()
	}
}