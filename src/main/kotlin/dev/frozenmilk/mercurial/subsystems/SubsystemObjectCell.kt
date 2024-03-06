package dev.frozenmilk.mercurial.subsystems

import dev.frozenmilk.dairy.core.Feature
import dev.frozenmilk.dairy.core.dependencyresolution.dependencyset.DependencySet
import dev.frozenmilk.dairy.core.wrapper.Wrapper
import dev.frozenmilk.mercurial.Mercurial
import dev.frozenmilk.util.cell.LazyCell
import java.util.function.Supplier

class SubsystemObjectCell<T>(val subsystem: Subsystem, supplier: Supplier<T>) : LazyCell<T>(supplier), Feature {
	override val dependencies = DependencySet(this)
			.dependsDirectlyOn(Mercurial)
	override fun postUserInitHook(opMode: Wrapper) {
		if (subsystem.isAttached()) get()
	}
	override fun postUserStopHook(opMode: Wrapper) = invalidate()
}