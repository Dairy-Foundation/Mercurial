package dev.frozenmilk.dairy.mercurial.continuations.registers

import dev.frozenmilk.dairy.mercurial.continuations.Fiber
import java.util.function.Supplier
import kotlin.reflect.KProperty

open class ValRegister<T> : Register<T>, Supplier<T> {
    override fun get(): T = Fiber.Registers.GET(this)
    operator fun getValue(thisRef: Any?, property: KProperty<*>): T = get()
}