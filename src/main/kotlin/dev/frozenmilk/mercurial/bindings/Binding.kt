package dev.frozenmilk.mercurial.bindings

import dev.frozenmilk.mercurial.Mercurial
import dev.frozenmilk.mercurial.commands.Command
import java.util.function.Supplier

class Binding private constructor(runnable: Runnable) : Runnable by runnable {
	init {
		Mercurial.registerBinding(this)
	}

	companion object {
		@JvmStatic
		fun runCommand(activationCondition: Supplier<Boolean>, toRun: Command) = Binding { if (activationCondition.get()) toRun.schedule() }

		@JvmStatic
		fun cancelCommand(activationCondition: Supplier<Boolean>, toCancel: Command) = Binding { if (activationCondition.get()) toCancel.cancel() }
	}
}