package dev.frozenmilk.dairy.mercurial.continuations.registers

import dev.frozenmilk.dairy.mercurial.continuations.Fiber
import dev.frozenmilk.util.modifier.Modifier
import java.util.function.Consumer
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.KProperty

class VarRegister<T> : ValRegister<T>(), Consumer<T> {
    fun set(value: T) = Fiber.Registers.SET(this, value)
    fun map(f: Modifier<T>) = Fiber.Registers.MAP(this, f)
    @OptIn(ExperimentalContracts::class)
    @JvmSynthetic
    inline fun map(f: (T) -> T): T {
        contract {
            callsInPlace(f, InvocationKind.EXACTLY_ONCE)
        }
        return Fiber.Registers.MAP(this, f)
    }
    override fun accept(t: T) = set(t)
    operator fun setValue(thisRef: Any?, property: KProperty<*>, value: T) = set(value)
}