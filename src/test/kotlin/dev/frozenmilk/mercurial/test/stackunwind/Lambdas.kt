package dev.frozenmilk.mercurial.test.stackunwind

import dev.frozenmilk.dairy.testrt.OpModeTestRunner
import org.hamcrest.CoreMatchers
import org.junit.runner.RunWith

@RunWith(OpModeTestRunner::class)
abstract class Lambdas(phase: Phase) : PhaseStackUnwindTest(phase, { it }) {
	init {
		exceptionRule.expectMessage(CoreMatchers.`is`(phase.errorPrefix + phase.commandName))
	}
}

class Init : Lambdas(Phase.INIT)
class Execute : Lambdas(Phase.EXECUTE)
class Finished : Lambdas(Phase.FINISHED)
class End : Lambdas(Phase.END)
