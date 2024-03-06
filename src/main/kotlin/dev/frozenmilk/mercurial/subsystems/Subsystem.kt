package dev.frozenmilk.mercurial.subsystems

import dev.frozenmilk.dairy.core.Feature
import dev.frozenmilk.dairy.core.dependencyresolution.dependencyset.DependencySet
import dev.frozenmilk.mercurial.Mercurial
import dev.frozenmilk.mercurial.commands.Command
import dev.frozenmilk.sinister.Preload

/**
 * WARNING: all classes that implement [Subsystem] are [Preload]ed by [dev.frozenmilk.sinister.Sinister] via the [SubsystemSinisterFilter], this should most likely not cause issues
 */
interface Subsystem : Feature {
	/**
	 * the default command of the subsystem, null if none, DO NOT OVERRIDE
	 */
	var defaultCommand: Command?
		get() {
			return Mercurial.getDefaultCommand(this)
		}
		set(value) {
			Mercurial.setDefaultCommand(this, value)
		}

	//
	// Impl Feature
	//
	/**
	 * generates
	 */
	fun generateDependencySet() = DependencySet(this).dependsDirectlyOn(Mercurial)
}