package dev.frozenmilk.dairy.mercurial

fun interface MapToBoolean<in T> {
    fun mapToBoolean(value: T): Boolean
}
