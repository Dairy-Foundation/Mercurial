package dev.frozenmilk.mercurial.test.scheduler

import dev.frozenmilk.dairy.testrt.OpModeTestRunner
import dev.frozenmilk.mercurial.commands.Command
import dev.frozenmilk.mercurial.commands.util.IfElse
import org.junit.runner.RunWith

@RunWith(OpModeTestRunner::class)
abstract class IfElseTest : EventSequenceCommandTest() {
	class True : IfElseTest() {
		override val command: Command = IfElse({ true }, lambdaTest("1").addEnd { cancelled -> advanceToStop = !cancelled }, lambdaTest("2"))
	}

	class False : IfElseTest() {
		override val command: Command = IfElse({ false }, lambdaTest("1"), lambdaTest("2").addEnd { cancelled -> advanceToStop = !cancelled })
	}
}