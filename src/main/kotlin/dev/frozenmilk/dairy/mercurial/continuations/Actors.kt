package dev.frozenmilk.dairy.mercurial.continuations

import dev.frozenmilk.dairy.mercurial.continuations.Continuations.exec
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.loop
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.scope
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.sequence
import dev.frozenmilk.dairy.mercurial.continuations.channels.Channels
import dev.frozenmilk.dairy.mercurial.continuations.channels.Receiver
import dev.frozenmilk.dairy.mercurial.continuations.channels.Sender
import dev.frozenmilk.dairy.mercurial.continuations.registers.VarRegister
import java.util.function.Supplier

@Suppress("UNUSED")
object Actors {
    fun interface AutomataScopeClosure<STATE> {
        fun bind(stateRegister: VarRegister<STATE>): Closure
    }

    fun interface MessageHandler<STATE, MESSAGE> {
        fun handle(
            state: STATE,
            message: MESSAGE,
        ): STATE
    }

    class Actor<STATE, MESSAGE>(
        initializer: Supplier<STATE>,
        messageHandler: MessageHandler<STATE, MESSAGE>,
        automata: AutomataScopeClosure<STATE>,
    ) : IntoContinuation {
        @get:JvmName("tx")
        val tx: Sender<MESSAGE>
        private val rx: Receiver<MESSAGE>

        init {
            val (tx, rx) = Channels.single<MESSAGE>()
            this.tx = tx
            this.rx = rx
        }

        private val k = scope {
            val stateRegister = variable(initializer)
            var state by stateRegister

            val automataLocalStateRegister: VarRegister<STATE>
            val automata = loop(
                scope {
                    automataLocalStateRegister = variable(stateRegister)
                    sequence(
                        automata.bind(automataLocalStateRegister),
                        exec { state = automataLocalStateRegister.get() },
                    )
                }
            ).close()

            var automataFiber by variable { Fiber(automata) }

            object : FactoryClosure() {
                override fun close(
                    name: String?,
                    k: Continuation,
                ) = Continuation(name ?: "actor") { self ->
                    if (!rx.empty) {
                        val oldState = state
                        val newState = messageHandler.handle(oldState, rx.take())
                        if (oldState != newState) {
                            state = newState
                            Fiber.CANCEL(automataFiber)
                            automataFiber = Fiber(automata)
                        }
                    }

                    Fiber.SUBSCHEDULE(automataFiber)

                    self
                }
            }
        }.close()

        override fun intoContinuation() = k
    }

    @JvmStatic
    fun <STATE, MESSAGE> actor(
        initializer: Supplier<STATE>,
        messageHandler: MessageHandler<STATE, MESSAGE>,
        automata: AutomataScopeClosure<STATE>,
    ) = Actor(
        initializer,
        messageHandler,
        automata,
    )
}