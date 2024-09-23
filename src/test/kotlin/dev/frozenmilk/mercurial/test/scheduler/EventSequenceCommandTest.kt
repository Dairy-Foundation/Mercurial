package dev.frozenmilk.mercurial.test.scheduler

import dev.frozenmilk.dairy.testrt.TestOpMode
import dev.frozenmilk.mercurial.Mercurial
import dev.frozenmilk.mercurial.commands.Command
import dev.frozenmilk.mercurial.commands.Lambda
import dev.frozenmilk.util.cell.RefCell
import org.junit.Assert
import org.junit.Rule
import org.junit.rules.Timeout


@Mercurial.Attach
abstract class EventSequenceCommandTest : TestOpMode() {
	enum class Phase {
		INIT,
		EXECUTE,
		END;
	}

	fun lambdaTest(name: String): Lambda {
		val looping = RefCell(false)
		val phase = RefCell(Phase.END)
		return Lambda(name)
			.setInit {
				Assert.assertEquals(false, looping.get())
				Assert.assertEquals(Phase.END, phase.get())
				phase.accept(Phase.INIT)
			}
			.setExecute {
				if (looping.get()) Assert.assertEquals(Phase.EXECUTE, phase.get())
				else Assert.assertEquals(Phase.INIT, phase.get())
				phase.accept(Phase.EXECUTE)
				looping.accept(true)
			}
			.setEnd { _ ->
				Assert.assertEquals(true, looping.get())
				Assert.assertEquals(Phase.EXECUTE, phase.get())
				phase.accept(Phase.END)
				looping.accept(false)
			}
	}

	@Rule
	@JvmField
	var globalTimeout: Timeout = Timeout.seconds(10)

	abstract val command: Command
	override fun start() {
		command.schedule()
		advanceToStop = false
	}

	override fun loop() {
	}
}