package dev.frozenmilk.dairy.mercurial.continuations

fun interface Application {
    fun apply(self: Continuation): Continuation
}