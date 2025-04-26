package dev.frozenmilk.dairy.mercurial.continuations.channels

interface Sender<T> {
    val full: Boolean

    /**
     * panics if [full]
     */
    fun send(value: T)
}