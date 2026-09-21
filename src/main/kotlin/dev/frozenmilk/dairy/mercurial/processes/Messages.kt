package dev.frozenmilk.dairy.mercurial.processes

object Messages {
    data class Exit(val from: Fiber<*>, val reason: ExitReason)
    data class Down(val from: Fiber<*>, val reason: ExitReason)
}
