package dev.frozenmilk.dairy.mercurial.continuations.channels

data class Channel<T>(
    @get:JvmName("tx")
    val tx: Sender<T>,
    @get:JvmName("rx")
    val rx: Receiver<T>
)
