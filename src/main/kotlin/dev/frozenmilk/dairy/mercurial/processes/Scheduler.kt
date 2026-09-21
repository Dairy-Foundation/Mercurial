package dev.frozenmilk.dairy.mercurial.processes

import dev.frozenmilk.dairy.mercurial.Mercurial
import dev.frozenmilk.util.collections.Q
import kotlin.time.Duration
import kotlin.time.DurationUnit
import kotlin.time.measureTime

interface Scheduler {
    fun schedule(fiber: Fiber<*>)
    fun poll()
    fun shutdown()

    val pollDuration: Duration
    val pollDurationSeconds: Double
        get() = pollDuration.toDouble(DurationUnit.SECONDS)
    val averageFiberDuration: Duration
    val averageFiberDurationSeconds: Double
        get() = averageFiberDuration.toDouble(DurationUnit.SECONDS)

    class Standard : Scheduler {
        private val q = Q<Fiber<*>>()

        override fun schedule(fiber: Fiber<*>) {
            q.append(fiber)
        }

        override var pollDuration = Duration.ZERO
            private set
        override var averageFiberDuration = Duration.ZERO
            private set

        override fun poll() {
            val root = Fiber.root
            when (val status = root.status) {
                is ExitReason.Normally -> shutdown()
                is ExitReason.Exceptionally -> {
                    shutdown()
                    throw IllegalStateException("root fiber exited exceptionally", status.e)
                }

                is ExitReason -> {
                    shutdown()
                    throw IllegalStateException("root fiber exited abnormally, $status")
                }

                ProcessStatus.Alive -> pollDuration = Mercurial.timeSource.measureTime {
                    q.append(root)
                    while (true) {
                        val fiber = q.pop()
                        if (fiber === root) break
                        if (fiber.poll().alive) q.append(fiber)
                        averageFiberDuration *= 0.99
                        averageFiberDuration += fiber.pollDuration * 0.01
                    }
                }
            }
        }

        override fun shutdown() {
            while (!q.empty) {
                val fiber = q.pop()
                if (fiber.status.alive) fiber.exit(ExitReason.Kill)
            }
        }

        override fun toString() = q.toString()
    }
}
