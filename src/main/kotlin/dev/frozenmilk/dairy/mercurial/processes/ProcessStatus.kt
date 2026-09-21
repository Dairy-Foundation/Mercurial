package dev.frozenmilk.dairy.mercurial.processes

/**
 * the state of a process
 */
sealed class ProcessStatus(
    @get:JvmName("alive")
    val alive: Boolean,
) : Fiber.Result<Nothing>() {
    companion object {
        @JvmField
        val alive = Alive
    }
    object Alive : ProcessStatus(true)
}
