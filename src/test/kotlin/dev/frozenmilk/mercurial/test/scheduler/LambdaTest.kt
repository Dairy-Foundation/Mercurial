package dev.frozenmilk.mercurial.test.scheduler

import dev.frozenmilk.dairy.testrt.OpModeTestRunner
import dev.frozenmilk.mercurial.commands.Command
import org.junit.runner.RunWith

// tests standard commands
@RunWith(OpModeTestRunner::class)
abstract class LambdaTest : EventSequenceCommandTest() {
	class Standard : LambdaTest() {
		override var command: Command = lambdaTest("standard")
			.addEnd { cancelled ->
				advanceToStop = !cancelled
			}
	}

	class Cancelled : LambdaTest() {
		override var command: Command = lambdaTest("cancelled")
			.addEnd { cancelled ->
				advanceToStop = cancelled
			}

		override fun loop() {
			command.cancel()
		}
	}

	class Loops : LambdaTest() {
		private var loops = 0
		private var reqLoops = 10 + (Math.random() * 100).toInt()
		override var command: Command = lambdaTest("loops")
			.addFinish { finish ->
				finish && loops++ > reqLoops
			}
			.addEnd { cancelled ->
				advanceToStop = !cancelled
			}
	}

	class LoopsCancelled : LambdaTest() {
		private var loops = 0
		private var reqLoops = 10
		override var command: Command = lambdaTest("loops")
			.addFinish { finish ->
				finish && loops++ > reqLoops
			}
			.addEnd { cancelled ->
				advanceToStop = cancelled
			}

		override fun loop() {
			if (loops == 5) command.cancel()
			super.loop()
		}
	}
}