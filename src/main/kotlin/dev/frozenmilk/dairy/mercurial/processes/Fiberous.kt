package dev.frozenmilk.dairy.mercurial.processes

import dev.frozenmilk.util.collections.Ord
import dev.frozenmilk.util.collections.WBT
import dev.frozenmilk.util.collections.Ord.HashCode as OrdHashCode

@Suppress("INAPPLICABLE_JVM_NAME")
interface Fiberous<out T> {
    companion object {
        @JvmField
        val Ord = object : Ord<Fiberous<*>> {
            override fun compare(
                l: Fiberous<*>,
                r: Fiberous<*>,
            ) = OrdHashCode.compare(l.fiber, r.fiber)
        }

        @JvmField
        val Set = WBT.MakeSet(Ord)
    }

    @get:JvmName("fiber")
    val fiber: Fiber<T>
}
