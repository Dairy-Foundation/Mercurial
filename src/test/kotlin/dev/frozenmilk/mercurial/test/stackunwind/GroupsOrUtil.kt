package dev.frozenmilk.mercurial.test.stackunwind

import dev.frozenmilk.dairy.testrt.OpModeTestRunner
import dev.frozenmilk.mercurial.commands.Command
import dev.frozenmilk.mercurial.commands.Lambda
import dev.frozenmilk.mercurial.commands.groups.Advancing
import dev.frozenmilk.mercurial.commands.groups.Parallel
import dev.frozenmilk.mercurial.commands.groups.Race
import dev.frozenmilk.mercurial.commands.groups.Sequential
import dev.frozenmilk.mercurial.commands.util.IfElse
import dev.frozenmilk.mercurial.commands.util.StateMachine
import dev.frozenmilk.util.modifier.Modifier
import org.hamcrest.CoreMatchers
import org.junit.runner.RunWith

@RunWith(OpModeTestRunner::class)
abstract class GroupsOrUtil(phase: Phase, mod: Modifier<Command>, error: String) : PhaseStackUnwindTest(phase, mod) {
	init {
		exceptionRule.expectMessage(CoreMatchers.`is`(phase.errorPrefix + error))
	}
}

abstract class ParallelTest(phase: Phase) : GroupsOrUtil(phase, {
	Parallel(it, Lambda("1"))
},
	"caused by: ${phase.commandName}\n" +
			"cause is marked as 'ERR' in this command s-expr\n" +
			"(parallel (\n" +
			"\tERR\n" +
			"\t1))"
) {
	class Init : ParallelTest(Phase.INIT)
	class Execute : ParallelTest(Phase.EXECUTE)
	class Finished : ParallelTest(Phase.FINISHED)
	class End : ParallelTest(Phase.END)
}

abstract class SequentialTest(phase: Phase) : GroupsOrUtil(phase, {
	Sequential(it, Lambda("1"))
},
	"caused by: ${phase.commandName}\n" +
			"cause is marked as 'ERR' in this command s-expr\n" +
			"(sequential (\n" +
			"\tERR\n" +
			"\t1))"
) {
	class Init : SequentialTest(Phase.INIT)
	class Execute : SequentialTest(Phase.EXECUTE)
	class Finished : SequentialTest(Phase.FINISHED)
	class End : SequentialTest(Phase.END)
}

abstract class RaceTest1(phase: Phase) : GroupsOrUtil(phase, {
	Race(
		Lambda("1").setFinish { false },
		it,
		Lambda("2")
	)
},
	"caused by: ${phase.commandName}\n" +
			"cause is marked as 'ERR' in this command s-expr\n" +
			"(race 1 (\n" +
			"\tERR\n" +
			"\t2))"
) {
	class Init : RaceTest1(Phase.INIT)
	class Execute : RaceTest1(Phase.EXECUTE)
	class Finished : RaceTest1(Phase.FINISHED)
	class End : RaceTest1(Phase.END)
}

abstract class RaceTest2(phase: Phase) : GroupsOrUtil(phase, {
	Race(it,
		Lambda("1"),
		Lambda("2")
	)
},
	"caused by: ${phase.commandName}\n" +
			"cause is marked as 'ERR' in this command s-expr\n" +
			"(race ERR (\n" +
			"\t1\n" +
			"\t2))"
) {
	class Init : RaceTest2(Phase.INIT)
	class Execute : RaceTest2(Phase.EXECUTE)
	class Finished : RaceTest2(Phase.FINISHED)
	class End : RaceTest2(Phase.END)
}

abstract class RaceTest3(phase: Phase) : GroupsOrUtil(phase, {
	Race(
		null,
		Lambda("1"),
		Lambda("2"),
		it
	)
},
	"caused by: ${phase.commandName}\n" +
			"cause is marked as 'ERR' in this command s-expr\n" +
			"(race (\n" +
			"\t1\n" +
			"\t2\n" +
			"\tERR))"
) {
	class Init : RaceTest3(Phase.INIT)
	class Execute : RaceTest3(Phase.EXECUTE)
	class Finished : RaceTest3(Phase.FINISHED)
	class End : RaceTest3(Phase.END)
}

abstract class AdvancingTest(phase: Phase) : GroupsOrUtil(phase, {
	Advancing(it, Lambda("1"))
},
	"caused by: ${phase.commandName}\n" +
			"cause is marked as 'ERR' in this command s-expr\n" +
			"(advancing (\n" +
			"\tERR\n" +
			"\t1))"
) {
	class Init : AdvancingTest(Phase.INIT)
	class Execute : AdvancingTest(Phase.EXECUTE)
	class Finished : AdvancingTest(Phase.FINISHED)
	class End : AdvancingTest(Phase.END)
}

abstract class IfElseTest1(phase: Phase) : GroupsOrUtil(phase, {
	IfElse({ true }, it, Lambda("1"))
},
	"caused by: ${phase.commandName}\n" +
			"cause is marked as 'ERR' in this command s-expr\n" +
			"(? true ERR 1)"
) {
	class Init : IfElseTest1(Phase.INIT)
	class Execute : IfElseTest1(Phase.EXECUTE)
	class Finished : IfElseTest1(Phase.FINISHED)
	class End : IfElseTest1(Phase.END)
}

abstract class IfElseTest2(phase: Phase) : GroupsOrUtil(phase, {
	IfElse({ false }, Lambda("1"), it)
},
	"caused by: ${phase.commandName}\n" +
			"cause is marked as 'ERR' in this command s-expr\n" +
			"(? false 1 ERR)"
) {
	class Init : IfElseTest2(Phase.INIT)
	class Execute : IfElseTest2(Phase.EXECUTE)
	class Finished : IfElseTest2(Phase.FINISHED)
	class End : IfElseTest2(Phase.END)
}

abstract class StateMachineTest1(phase: Phase) : GroupsOrUtil(phase, {
	StateMachine(States.One)
		.withState(States.One) { state, name -> it }
		.withState(States.Two) { state, name -> Lambda(name) }
},
	"caused by: ${phase.commandName}\n" +
			"cause is marked as 'ERR' in this command s-expr\n" +
			"(state-machine One (\n" +
			"\t(One ERR)\n" +
			"\tTwo))"
) {
	enum class States {
		One, Two
	}
	class Init : StateMachineTest1(Phase.INIT)
	class Execute : StateMachineTest1(Phase.EXECUTE)
	class Finished : StateMachineTest1(Phase.FINISHED)
	class End : StateMachineTest1(Phase.END)
}

abstract class StateMachineTest2(phase: Phase) : GroupsOrUtil(phase, {
	StateMachine(phase.commandName)
		.withState(phase.commandName) { state, name -> it }
		.withState("1") { state, name -> Lambda(name) }
},
	"caused by: ${phase.commandName}\n" +
			"cause is marked as 'ERR' in this command s-expr\n" +
			"(state-machine ${phase.commandName} (\n" +
			"\tERR\n" +
			"\t1))"
) {
	class Init : StateMachineTest2(Phase.INIT)
	class Execute : StateMachineTest2(Phase.EXECUTE)
	class Finished : StateMachineTest2(Phase.FINISHED)
	class End : StateMachineTest2(Phase.END)
}

abstract class ComplexTest(phase: Phase) : GroupsOrUtil(phase, {
	Sequential(
		Parallel(
			Lambda("1"),
			Lambda("2")
		),
		Sequential(
			Parallel(
				Lambda("3"),
				Lambda("4")
			),
			Race(null,
				Lambda("5"),
				Lambda("6")
			),
			it.timeout(0.002)
		)
	)
},
	"caused by: ${phase.commandName}\n" +
			"cause is marked as 'ERR' in this command s-expr\n" +
			"(sequential (\n" +
			"\t(parallel (\n" +
			"\t\t1\n" +
			"\t\t2))\n" +
			"\t(sequential (\n" +
			"\t\t(parallel (\n" +
			"\t\t\t3\n" +
			"\t\t\t4))\n" +
			"\t\t(race (\n" +
			"\t\t\t5\n" +
			"\t\t\t6))\n" +
			"\t\t(race (wait 0.002) (\n" +
			"\t\t\tERR))))))"
) {
	class Init : ComplexTest(Phase.INIT)
	class Execute : ComplexTest(Phase.EXECUTE)
	class Finished : ComplexTest(Phase.FINISHED)
	class End : ComplexTest(Phase.END)
}