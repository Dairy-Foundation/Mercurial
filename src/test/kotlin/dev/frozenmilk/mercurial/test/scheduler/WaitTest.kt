package dev.frozenmilk.mercurial.test.scheduler

import dev.frozenmilk.dairy.testrt.OpModeTestRunner
import dev.frozenmilk.mercurial.commands.Command
import dev.frozenmilk.mercurial.commands.Lambda
import dev.frozenmilk.mercurial.commands.util.Wait
import org.junit.rules.Timeout
import org.junit.runner.RunWith

@RunWith(OpModeTestRunner::class)
class WaitTest : EventSequenceCommandTest() {
	override var command: Command
	init {
		val random = Math.random() * 2
		command = Lambda.from(Wait(random))
			.setEnd { cancelled -> advanceToStop = !cancelled }
		globalTimeout = Timeout.millis(((random + 0.1) * 1000).toLong())
	}
}