package dev.frozenmilk.dairy.mercurial

fun interface MapToInt<in T> {
    fun mapToInt(value: T): Int
}
