package dev.frozenmilk.mercurial.test.scheduler

import dev.frozenmilk.dairy.testrt.OpModeTestRunner
import dev.frozenmilk.dairy.testrt.TestOpMode
import dev.frozenmilk.mercurial.commands.Command
import dev.frozenmilk.mercurial.commands.Lambda
import dev.frozenmilk.mercurial.commands.groups.Race
import dev.frozenmilk.util.cell.RefCell
import org.junit.Assert
import org.junit.runner.RunWith

@RunWith(OpModeTestRunner::class)
abstract class RaceTest : EventSequenceCommandTest() {
	companion object {
		fun Command.lastMember(currentCommandCell: RefCell<String?>) = Lambda.from(this)
			.addInit {
				Assert.assertNotEquals(this.toString(), currentCommandCell.get())
			}
			.addExecute {
				Assert.assertNotEquals(this.toString(), currentCommandCell.get())
			}
			.addEnd { cancelled ->
				Assert.assertFalse(cancelled)
				Assert.assertNotEquals(this.toString(), currentCommandCell.get())
			}
		fun Command.testMember(currentCommandCell: RefCell<String?>) = Lambda.from(this)
			.addInit {
				Assert.assertNotEquals(this.toString(), currentCommandCell.get())
			}
			.addExecute {
				Assert.assertNotEquals(this.toString(), currentCommandCell.get())
			}
			.addFinish {
				false
			}
			.addEnd { cancelled ->
				Assert.assertFalse(cancelled)
				Assert.assertNotEquals(this.toString(), currentCommandCell.get())
			}
		fun nestedRaceTest(vararg commands: Command): Command {
			Assert.assertTrue(commands.isNotEmpty())
			val state = RefCell<String?>(null)

			return Race(null,
				commands.take(commands.size - 1).map { it.testMember(state) } +
						commands.last().lastMember(state)
			)
		}

		fun TestOpMode.raceTest(vararg commands: Command): Command {
			Assert.assertTrue(commands.isNotEmpty())
			val state = RefCell<String?>(null)

			return Race(null,
				commands.take(commands.size - 1).map { it.testMember(state) } +
						commands.last().lastMember(state).addEnd { cancelled ->
							this.advanceToStop = !cancelled
						}
			)
		}
	}
	class TwoCommands : RaceTest() {
		override var command = raceTest(
			lambdaTest("1"),
			lambdaTest("2")
		)
	}
	class TenCommands : RaceTest() {
		override var command = raceTest(
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
	class Nested : RaceTest() {
		override var command = raceTest(
			lambdaTest("1"),
			nestedRaceTest(
				lambdaTest("2.1"),
				lambdaTest("2.2")
			),
			lambdaTest("3"),
			nestedRaceTest(
				lambdaTest("4.1"),
				lambdaTest("4.2"),
				lambdaTest("4.3"),
				lambdaTest("4.4"),
			),
			lambdaTest("5"),
			nestedRaceTest(
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