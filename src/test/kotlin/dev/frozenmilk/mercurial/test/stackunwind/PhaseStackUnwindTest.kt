package dev.frozenmilk.mercurial.test.stackunwind

import dev.frozenmilk.dairy.testrt.TestOpMode
import dev.frozenmilk.mercurial.Mercurial
import dev.frozenmilk.mercurial.commands.Command
import dev.frozenmilk.mercurial.commands.Lambda
import dev.frozenmilk.mercurial.commands.MercurialException
import dev.frozenmilk.mercurial.commands.stateful.StatefulLambda
import dev.frozenmilk.util.modifier.Modifier
import org.hamcrest.CoreMatchers
import org.junit.Rule
import org.junit.rules.ExpectedException
import org.junit.rules.Timeout

@Mercurial.Attach
abstract class PhaseStackUnwindTest(phase: Phase, mod: Modifier<Command>) : TestOpMode() {
	enum class Phase(val commandName: String) {
		INIT("initialise"),
		EXECUTE("execute"),
		FINISHED("finished?"),
		END("end");

		//fun <T> mod(statefulLambda: StatefulLambda<T>) = statefulLambda.run {
		//	when (this@Phase) {
		//		INIT -> setInit { _ -> throw RuntimeException() }
		//		EXECUTE -> setExecute { _ -> throw RuntimeException() }
		//		FINISHED -> setFinish { _ -> throw RuntimeException() }
		//		END -> setEnd { _ -> throw RuntimeException() }
		//	}
		//}
		fun lambda() = Lambda(commandName).run {
			when (this@Phase) {
				INIT -> setInit { throw RuntimeException() }
				EXECUTE -> setExecute { throw RuntimeException() }
				FINISHED -> setFinish { throw RuntimeException() }
				END -> setEnd { throw RuntimeException() }
			}
		}

		val errorPrefix = "exception thrown in $commandName:\n"
	}
	@JvmField
	@Rule
	var exceptionRule: ExpectedException = ExpectedException.none().apply {
		expect(MercurialException::class.java)
		expectCause(CoreMatchers.isA(RuntimeException::class.java))
		reportMissingExceptionWithMessage("Exception was not thrown")
		expectMessage(CoreMatchers.startsWith(phase.errorPrefix))
	}

	@Rule
	@JvmField
	val globalTimeout: Timeout = Timeout.seconds(10)

	var command = mod.modify(phase.lambda())

	override fun start() {
		command.schedule()
	}

	override fun loop() {
		advanceToStop = false
	}
}