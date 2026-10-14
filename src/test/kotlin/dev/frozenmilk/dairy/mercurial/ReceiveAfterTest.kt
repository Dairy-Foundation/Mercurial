package dev.frozenmilk.dairy.mercurial

import dev.frozenmilk.dairy.mercurial.continuations.Continuations.exec
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.expression
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.receive
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.seconds
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.value
import dev.frozenmilk.dairy.mercurial.processes.Channel
import org.junit.Before
import org.junit.Test

class ReceiveAfterTest {
    @Before
    fun initialise() = Mercurial.initialiseThread()

    @Test
    fun receiveAfter() {
        val f = expression { _ ->
            val chan = o { Channel.single<String>() }

            val msg = o(
                receive<String>() //
                    .receive(chan) //
                    .after(seconds(1.0), value.o { "After" }) //
            )

            exec { println(msg()) }
        }.spawnable().spawnLink()

        while (f.status.alive) Mercurial.scheduler.poll()
    }
}
