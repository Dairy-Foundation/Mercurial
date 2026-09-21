package dev.frozenmilk.dairy.mercurial

import dev.frozenmilk.dairy.mercurial.continuations.Continuations.duration
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.exec
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.expression
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.function
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.ifThen
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.noop
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.receive
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.repeat
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.unreachable
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.value
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.waitFor
import dev.frozenmilk.dairy.mercurial.processes.Channel
import dev.frozenmilk.dairy.mercurial.processes.Fiber
import dev.frozenmilk.dairy.mercurial.processes.Scheduler
import org.junit.Before
import org.junit.Test
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource
import kotlin.time.measureTime

class SimpleTests {
    @Before
    fun initialise() = Mercurial.initialiseThread()

    @Test
    fun a() {
        val wait1 = waitFor * duration { 1.seconds }
        val prog1 = wait1
            .thenExec { println("a") }
            .then(wait1)
            .thenExec { println("b") }
            .then(wait1)
            .thenExec { println("c") }
            .then(wait1)
            .thenExec { println("d") }
            .spawnable()

        val fibn = function.I { self, n ->
            ifThen(
                { n() == 1 || n() == 2 },
                value.i { 1 },
                expression {
                    val prev1 = i(self * { n() - 1 })
                    val prev2 = i(self * { n() - 2 })
                    value.i { prev1() + prev2() }
                }
            )
        }

        val prog2 = expression {
            val n = i(fibn * { 10 })
            exec { println("fibn of 10 => ${n()}") }
        }.spawnable()

        val f1 = prog1.spawnLink()
        val f2 = prog2.spawnLink()

        val duration = TimeSource.Monotonic.measureTime {
            while (f2.status.alive) {
                Mercurial.scheduler.poll()
            }
        }
        println("time: $duration")
    }

    @Test
    fun b() {
        var mark: TimeMark = TimeSource.Monotonic.markNow()
        val pingChannel = Channel.queue<Unit>()
        val pongChannel = Channel.queue<Unit>()

        val ping: Fiber<Any?> = expression {
            // receive is a lot like match with cases
            // we will look more at its details shortly

            // this will receive three pings
            // respond to pong each time
            repeat(
                3,
                receive<Any?>()
                    .receive(pingChannel, exec {
                        // print ping
                        println("ping!")
                        // send back to pong
                        pongChannel.send(Unit)
                    })
            )
        }.spawnable().spawnLink()

        val pong: Fiber<Any?> = expression {
            // this will receive three pongs
            // respond to ping each time
            repeat(
                3,
                receive<Any?>()
                    .receive(pongChannel, exec {
                        // print pong
                        println("pong!")
                        // send back to ping
                        pingChannel.send(Unit)
                    })
            )
        }.spawnable().spawnLink()

        // poll 5 times to warm up
        repeat(5) {
            Mercurial.scheduler.poll()
            println("average fiber loop time: ${Mercurial.scheduler.averageFiberDuration}")
            println("poll duration: ${Mercurial.scheduler.pollDuration}")
            println()
        }

        println("warmed up")
        println()

        pingChannel.send(Unit)

        var count = 0
        val duration = TimeSource.Monotonic.measureTime {
            while (ping.status.alive || pong.status.alive) {
                Mercurial.scheduler.poll()
                println("average fiber loop time: ${Mercurial.scheduler.averageFiberDuration}")
                println("poll duration: ${Mercurial.scheduler.pollDuration}")
                println()
                count++
            }
        }
        println("time: $duration")
        println("average fiber loop time: ${Mercurial.scheduler.averageFiberDuration}")
        println("poll duration: ${Mercurial.scheduler.pollDuration}")
        println("count: $count")
    }

    @Test
    fun c() {
        Mercurial.initialiseThread(
            Mercurial.Settings(
                timeSource = TimeSource.Monotonic,
                stackTraces = true,
                scheduler = Scheduler.Standard(),
            )
        )
        val _ = expression {
            val f = expression {
                unreachable
            }
            !noop
            !noop
            !f
            !f
            noop
        }.spawnable().spawnLink()

        while (Fiber.root.status.alive) {
            Mercurial.scheduler.poll()
        }
    }
}
