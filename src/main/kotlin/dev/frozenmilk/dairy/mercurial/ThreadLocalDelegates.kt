package dev.frozenmilk.dairy.mercurial

import kotlin.reflect.KProperty

operator fun <T> ThreadLocal<T>.getValue(
    thisRef: Any?,
    property: KProperty<*>,
): T = get()

operator fun <T> ThreadLocal<T>.setValue(
    thisRef: Any?,
    property: KProperty<*>,
    value: T,
) = set(value)
