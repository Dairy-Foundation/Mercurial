package dev.frozenmilk.mercurial.test.scheduler

import dev.frozenmilk.dairy.testrt.OpModeTestRunner
import dev.frozenmilk.dairy.testrt.TestOpMode
import dev.frozenmilk.mercurial.commands.Command
import dev.frozenmilk.mercurial.commands.Lambda
import dev.frozenmilk.mercurial.commands.util.StateMachine
import dev.frozenmilk.util.cell.LateInitCell
import dev.frozenmilk.util.cell.RefCell
import org.junit.Assert
import org.junit.runner.RunWith

@RunWith(OpModeTestRunner::class)
abstract class StateMachineTest : EventSequenceCommandTest() {
	companion object {
		fun Command.testMember(stateCell: RefCell<Int>, currentCommandCell: LateInitCell<String>) =
			Lambda.from(this)
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
					stateCell.accept(stateCell.get() + 1)
				}

		fun Command.lastMember(currentCommandCell: LateInitCell<String>) =
			Lambda.from(this)
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

		fun nestedStateMachineTest(vararg commands: Command): StateMachine<Int> {
			Assert.assertTrue(commands.isNotEmpty())
			val state = LateInitCell<String>()
			return StateMachine(0).run {
				commands.foldIndexed(this) { i, stateMachine, command ->
					stateMachine.withState(i) { stateRef, _ ->
						if (i != commands.size - 1) command.testMember(stateRef, state)
						else command.lastMember(state)
					}
				}
			}
		}
		fun TestOpMode.stateMachineTest(vararg commands: Command): StateMachine<Int> {
			Assert.assertTrue(commands.isNotEmpty())
			val state = LateInitCell<String>()
			return StateMachine(0).run {
				commands.foldIndexed(this) { i, stateMachine, command ->
					stateMachine.withState(i) { stateRef, _ ->
						if (i != commands.size - 1) command.testMember(stateRef, state)
						else command.lastMember(state)
							.addEnd { cancelled ->
								advanceToStop = !cancelled
							}
					}
				}
			}
		}
	}

	class OneCommand : StateMachineTest() {
		override var command: StateMachine<Int> = stateMachineTest(
			lambdaTest("1")
		)
	}

	class TwoCommands : StateMachineTest() {
		override var command: StateMachine<Int> = stateMachineTest(
			lambdaTest("1"),
			lambdaTest("2")
		)
	}

	class TenCommands : StateMachineTest() {
		override var command: StateMachine<Int> = stateMachineTest(
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

	class Nested : StateMachineTest() {
		private val c2: StateMachine<Int> = nestedStateMachineTest(
			lambdaTest("2.1"),
			lambdaTest("2.2")
		)
		private val c4: StateMachine<Int> = nestedStateMachineTest(
			lambdaTest("4")
		)
		private val c6: StateMachine<Int> = nestedStateMachineTest(
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
		override var command: StateMachine<Int> = stateMachineTest(
			lambdaTest("1"),
			c2,
			lambdaTest("3"),
			c4,
			lambdaTest("5"),
			c6
		)
	}
}