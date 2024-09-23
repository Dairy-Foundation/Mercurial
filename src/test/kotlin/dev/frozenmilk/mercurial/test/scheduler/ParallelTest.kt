package dev.frozenmilk.mercurial.test.scheduler

import dev.frozenmilk.dairy.testrt.OpModeTestRunner
import dev.frozenmilk.dairy.testrt.TestOpMode
import dev.frozenmilk.mercurial.commands.Command
import dev.frozenmilk.mercurial.commands.Lambda
import dev.frozenmilk.mercurial.commands.groups.Parallel
import dev.frozenmilk.util.cell.RefCell
import org.junit.Assert
import org.junit.runner.RunWith

@RunWith(OpModeTestRunner::class)
abstract class ParallelTest : EventSequenceCommandTest() {
	companion object {
		fun Command.testMember(currentCommandCell: RefCell<String?>) = Lambda.from(this)
			.addInit {
				Assert.assertNotEquals(this.toString(), currentCommandCell.get())
			}
			.addExecute {
				Assert.assertNotEquals(this.toString(), currentCommandCell.get())
			}
			.addEnd {
				Assert.assertNotEquals(this.toString(), currentCommandCell.get())
			}
		fun TestOpMode.parallelTest(vararg commands: Command): Command {
			Assert.assertTrue(commands.isNotEmpty())
			val state = RefCell<String?>(null)
			return Parallel(
				commands.take(commands.size - 1).map { it.testMember(state) } +
						commands.last().testMember(state).addEnd { cancelled ->
							this.advanceToStop = !cancelled
						}
			)
		}
	}
	class OneCommand : ParallelTest() {
		override var command = parallelTest(
			lambdaTest("1")
		)
	}
	class TwoCommands : ParallelTest() {
		override var command = parallelTest(
			lambdaTest("1"),
			lambdaTest("2")
		)
	}
	class TenCommands : ParallelTest() {
		override var command = parallelTest(
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
	class Nested : ParallelTest() {
		override var command = parallelTest(
			lambdaTest("1"),
			parallelTest(
				lambdaTest("2.1"),
				lambdaTest("2.2")
			),
			lambdaTest("3"),
			parallelTest(
				lambdaTest("4")
			),
			lambdaTest("5"),
			parallelTest(
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