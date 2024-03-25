package dev.frozenmilk.mercurial.subsystems

import dev.frozenmilk.mercurial.Mercurial
import dev.frozenmilk.sinister.SinisterFilter
import dev.frozenmilk.sinister.staticInstancesOf
import dev.frozenmilk.sinister.targeting.NarrowSearch

private object SubsystemSinisterFilter : SinisterFilter {
	override val targets = NarrowSearch()

	override fun init() {}

	override fun filter(clazz: Class<*>) {
		clazz.staticInstancesOf(Subsystem::class.java)
				.forEach {
					Mercurial.registerSubsystem(it)
				}
	}
}