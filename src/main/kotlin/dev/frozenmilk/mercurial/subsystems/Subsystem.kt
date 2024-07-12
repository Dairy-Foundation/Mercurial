package dev.frozenmilk.mercurial.subsystems

import dev.frozenmilk.dairy.core.Feature
import dev.frozenmilk.dairy.core.dependency.feature.SingleFeature
import dev.frozenmilk.mercurial.Mercurial
import dev.frozenmilk.mercurial.commands.Command
import dev.frozenmilk.sinister.Preload
import java.util.function.Supplier

/**
 * WARNING: all classes that implement [Subsystem] are [Preload]ed by [dev.frozenmilk.sinister.Sinister] via the [SubsystemSinisterFilter], this should most likely not cause issues
 */
interface Subsystem : Feature {
	/**
	 * the default command of the subsystem, null if none
	 *
	 * DO NOT OVERRIDE EITHER GET OR SET
	 */
	var defaultCommand: Command?
		get() {
			return Mercurial.getDefaultCommand(this)
		}
		set(value) {
			Mercurial.setDefaultCommand(this, value)
		}

	fun <T> subsystemCell(supplier: Supplier<T>) = SubsystemObjectCell(this, supplier)

	companion object {
		@JvmField
		val DEFAULT_DEPENDENCY = SingleFeature(Mercurial)
	}
}