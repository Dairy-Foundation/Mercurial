package dev.frozenmilk.dairy.mercurial.continuations.channels

interface Receiver<T> {
    val empty: Boolean

    /**
     * panics if [empty]
     */
    fun take(): T
}