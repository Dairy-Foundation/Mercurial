package dev.frozenmilk.dairy.mercurial

import dev.frozenmilk.dairy.mercurial.continuations.Continuation
import dev.frozenmilk.dairy.mercurial.processes.Fiber
import dev.frozenmilk.dairy.mercurial.processes.Scheduler
import kotlin.time.TimeSource

object Mercurial {
    data class Settings(
        val timeSource: TimeSource = TimeSource.Monotonic,
        val stackTraces: Boolean = true,
        val scheduler: Scheduler = Scheduler.Standard(),
    )

    private val settingsThreadLocal = ThreadLocal<Settings>()

    @get:JvmName("initialised")
    val initialised
        get() = settingsThreadLocal.get() !== null

    @JvmStatic
    @get:JvmName("settings")
    val settings: Settings
        get() = checkNotNull(settingsThreadLocal.get()) { "Mercurial not initialised" }

    @JvmStatic
    @get:JvmName("timeSource")
    val timeSource: TimeSource
        get() = settings.timeSource

    @JvmStatic
    @get:JvmName("stackTraces")
    val stackTraces: Boolean
        get() = settings.stackTraces

    @JvmStatic
    @get:JvmName("scheduler")
    val scheduler: Scheduler
        get() = settings.scheduler

    @JvmStatic
    fun initialiseThread(settings: Settings) {
        run {
            val settings: Settings? = settingsThreadLocal.get()
            settings?.scheduler?.shutdown()
        }
        settingsThreadLocal.set(settings)
        val root = Fiber<Nothing>(Continuation.Root)
        Fiber.rootThreadLocal.set(root)
        Fiber.current = root
    }

    @JvmStatic
    fun initialiseThread() = initialiseThread(Settings())
}
