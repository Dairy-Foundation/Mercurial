package dev.frozenmilk.dairy.mercurial

fun interface MapTo<in T, out U> {
    fun mapTo(value: T): U
}
