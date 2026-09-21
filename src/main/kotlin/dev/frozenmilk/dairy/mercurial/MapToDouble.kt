package dev.frozenmilk.dairy.mercurial

fun interface MapToDouble<in T> {
    fun mapToDouble(value: T): Double
}
