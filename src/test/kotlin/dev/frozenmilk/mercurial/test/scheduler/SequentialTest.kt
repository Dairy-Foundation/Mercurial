package dev.frozenmilk.mercurial.test.scheduler

import dev.frozenmilk.dairy.testrt.OpModeTestRunner
import dev.frozenmilk.dairy.testrt.TestOpMode
import dev.frozenmilk.mercurial.commands.Command
import dev.frozenmilk.mercurial.commands.Lambda
import dev.frozenmilk.mercurial.commands.groups.Sequential
import dev.frozenmilk.mercurial.test.scheduler.ParallelTest.Companion.parallelTest
import dev.frozenmilk.mercurial.test.scheduler.ParallelTest.Companion.testMember
import dev.frozenmilk.util.cell.LateInitCell
import org.junit.Assert
import org.junit.runner.RunWith

@RunWith(OpModeTestRunner::class)
abstract class SequentialTest : EventSequenceCommandTest() {
	companion object {
		fun Command.testMember(currentCommandCell: LateInitCell<String>) = Lambda.from(this)
			.addInit {
				Assert.assertTrue(!currentCommandCell.initialised)
				currentCommandCell.accept(this.toString())
			}
			.addExecute {
				Assert.assertEquals(toString(), currentCommandCell.get())
			}
			.addEnd {
				Assert.assertEquals(toString(), currentCommandCell.get())
				currentCommandCell.invalidate()
			}
		fun TestOpMode.sequentialTest(vararg commands: Command): Command {
			Assert.assertTrue(commands.isNotEmpty())
			val state = LateInitCell<String>()
			return Sequential(
				commands.take(commands.size - 1).map { it.testMember(state) } +
						commands.last().testMember(state).addEnd { cancelled ->
							this.advanceToStop = !cancelled
						}
			)
		}
	}
	class OneCommand : SequentialTest() {
		override var command = sequentialTest(
			lambdaTest("1")
		)
	}
	class TwoCommands : SequentialTest() {
		override var command = sequentialTest(
			lambdaTest("1"),
			lambdaTest("2")
		)
	}
	class TenCommands : SequentialTest() {
		override var command = sequentialTest(
			lambdaTest("1"),
			lambdaTest("2"),
			lambdaTest("3"),
			lambdaTest("4"),
			lambdaTest("5"),
			lambdaTest("6"),
			lambdaTest("7"),
			lambdaTest("8"),
			lambdaTest("9"),
			lambdaTest("10"),
		)
	}
	class Nested : SequentialTest() {
		override var command = sequentialTest(
			lambdaTest("1"),
			sequentialTest(
				lambdaTest("2.1"),
				lambdaTest("2.2")
			),
			lambdaTest("3"),
			sequentialTest(
				lambdaTest("4")
			),
			lambdaTest("5"),
			sequentialTest(
				lambdaTest("6.1"),
				lambdaTest("6.2"),
				lambdaTest("6.3"),
				lambdaTest("6.4"),
				lambdaTest("6.5"),
				lambdaTest("6.6"),
				lambdaTest("6.7"),
				lambdaTest("6.8"),
				lambdaTest("6.9"),
				lambdaTest("6.10"),
			)
		)
	}
}