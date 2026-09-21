package dev.frozenmilk.dairy.mercurial.processes

import dev.frozenmilk.dairy.mercurial.Mercurial
import dev.frozenmilk.dairy.mercurial.continuations.Continuation
import java.util.function.Supplier

interface Spawnable<out T> {
    fun spawn(flag: Fiber.SpawnFlag): Fiber<T>
    fun spawn() = spawn(Fiber.SpawnFlag.None)
    fun spawnLink() = spawn(Fiber.SpawnFlag.Link)
    fun spawnMonitor(channel: Supplier<out Channel<in Messages.Down>>) = spawn(Fiber.SpawnFlag.Monitor(channel))
    fun spawnMonitor(channel: Channel<in Messages.Down>) = spawn(Fiber.SpawnFlag.Monitor { channel })


    data class Builder<T>(val k: Continuation) : Spawnable<T> {
        constructor(builder: Continuation.Builder<T>) : this(builder.compile())
        override fun spawn(flag: Fiber.SpawnFlag) = Fiber<T>(k).also { fiber ->
            flag.applyTo(fiber)
            Mercurial.scheduler.schedule(fiber)
        }
    }
}
