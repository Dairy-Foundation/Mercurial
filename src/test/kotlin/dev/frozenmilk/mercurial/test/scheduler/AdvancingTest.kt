package dev.frozenmilk.mercurial.test.scheduler

import dev.frozenmilk.dairy.testrt.OpModeTestRunner
import dev.frozenmilk.dairy.testrt.TestOpMode
import dev.frozenmilk.mercurial.commands.Command
import dev.frozenmilk.mercurial.commands.Lambda
import dev.frozenmilk.mercurial.commands.groups.Advancing
import dev.frozenmilk.util.cell.LateInitCell
import org.junit.Assert
import org.junit.runner.RunWith

@RunWith(OpModeTestRunner::class)
abstract class AdvancingTest : EventSequenceCommandTest() {
	companion object {
		fun Command.testMember(advancer: () -> Advancing, currentCommandCell: LateInitCell<String>) = Lambda.from(this)
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
				advancer().advance()
			}
		fun Command.lastMember(currentCommandCell: LateInitCell<String>) = Lambda.from(this)
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
		fun TestOpMode.nestedAdvancingTest(advancer: () -> Advancing, vararg commands: Command): Advancing {
			Assert.assertTrue(commands.isNotEmpty())
			val state = LateInitCell<String>()
			return Advancing(commands.take(commands.size - 1).map {
				it.testMember(advancer, state)
			} + commands.last().lastMember(state)).apply { advance() }
		}
		fun TestOpMode.advancingTest(advancer: () -> Advancing, vararg commands: Command): Advancing {
			Assert.assertTrue(commands.isNotEmpty())
			val state = LateInitCell<String>()
			return Advancing(commands.take(commands.size - 1).map {
				it.testMember(advancer, state)
			} + commands.last().lastMember(state)
				.addEnd { cancelled ->
					this.advanceToStop = !cancelled
				})
		}
	}
	class OneCommand : AdvancingTest() {
		override var command: Advancing = advancingTest(
			this::command,
			lambdaTest("1")
		)
	}
	class TwoCommands : AdvancingTest() {
		override var command: Advancing = advancingTest(
			this::command,
			lambdaTest("1"),
			lambdaTest("2")
		)
	}
	class TenCommands : AdvancingTest() {
		override var command: Advancing = advancingTest(
			this::command,
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
	class Nested : AdvancingTest() {
		private val c2: Advancing = nestedAdvancingTest(
			this::c2,
			lambdaTest("2.1"),
			lambdaTest("2.2")
		)
		private val c4: Advancing = nestedAdvancingTest(
			this::c4,
			lambdaTest("4")
		)
		private val c6: Advancing = nestedAdvancingTest(
			this::c6,
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
		override var command: Advancing = advancingTest(
			this::command,
			lambdaTest("1"),
			c2,
			lambdaTest("3"),
			c4,
			lambdaTest("5"),
			c6
		)
	}
}