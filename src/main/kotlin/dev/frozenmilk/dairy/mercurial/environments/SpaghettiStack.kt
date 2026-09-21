package dev.frozenmilk.dairy.mercurial.environments

import dev.frozenmilk.dairy.mercurial.continuations.Continuation
import dev.frozenmilk.util.collections.Cons

class SpaghettiStack(
    var trace: Cons<StackTraceElement>?,
    val captured: SpaghettiStack?,
    val actual: Continuation.Function.Actual?,
    var k: Continuation,
) {
    constructor(k: Continuation) : this(
        null,
        null,
        null,
        k,
    )
    constructor(trace: Cons<StackTraceElement>?, fn: Continuation.Function) : this(
        trace,
        fn.captured,
        fn.actual,
        fn.body,
    ) {
        oReserve(fn.oSize)
        dReserve(fn.dSize)
        iReserve(fn.iSize)
        bReserve(fn.bSize)
    }

    companion object {
        @JvmField
        val oEmpty = emptyArray<Any?>()

        @JvmField
        val dEmpty = doubleArrayOf()

        @JvmField
        val iEmpty = intArrayOf()

        @JvmField
        val bEmpty = booleanArrayOf()


        // we try to let vms that require an array header keep some extra space at the end,
        // but we will ignore this if we're completely out of space
        private const val MAX_ARRAY_SIZE = Int.MAX_VALUE - 8
        private fun newCapacity(
            oldCapacity: Int,
            minCapacity: Int,
        ): Int {
            var newCapacity = oldCapacity + (oldCapacity shl 1)
            if (newCapacity < 10) newCapacity = 10
            if (newCapacity - minCapacity < 0) newCapacity = minCapacity
            if (newCapacity - MAX_ARRAY_SIZE > 0) newCapacity = hugeCapacity(minCapacity)
            return newCapacity
        }

        private fun hugeCapacity(minCapacity: Int) =
            // overflow
            if (minCapacity < 0) throw OutOfMemoryError()
            else if (minCapacity > MAX_ARRAY_SIZE) Int.MAX_VALUE
            else MAX_ARRAY_SIZE
    }

    var oData = oEmpty
    var oStack = 0

    fun oReserve(n: Int) {
        val newSize = oStack + n
        if (newSize > oData.size) oData = oData.copyOf(
            newCapacity(
                oData.size,
                newSize,
            ),
        )
    }

    fun oPush(o: Any?) {
        oReserve(1)
        oData[oStack++] = o
    }

    fun oPop() = oData[--oStack].also {
        oData[oStack] = null
    }

    var dData = dEmpty
    var dStack = 0

    fun dReserve(n: Int) {
        val newSize = dStack + n
        if (newSize > dData.size) dData = dData.copyOf(
            newCapacity(
                dData.size,
                newSize,
            ),
        )
    }

    fun dPush(d: Double) {
        dReserve(1)
        dData[dStack++] = d
    }

    fun dPop() = dData[--dStack]

    var iData = iEmpty
    var iStack = 0

    fun iReserve(n: Int) {
        val newSize = iStack + n
        if (newSize > iData.size) iData = iData.copyOf(
            newCapacity(
                iData.size,
                newSize,
            ),
        )
    }

    fun iPush(i: Int) {
        iReserve(1)
        iData[iStack++] = i
    }

    fun iPop() = iData[--iStack]

    var bData = bEmpty
    var bStack = 0

    fun bReserve(n: Int) {
        val newSize = bStack + n
        if (newSize > bData.size) bData = bData.copyOf(
            newCapacity(
                bData.size,
                newSize,
            ),
        )
    }

    fun bPush(b: Boolean) {
        bReserve(1)
        bData[bStack++] = b
    }

    fun bPop() = bData[--bStack]

    // procedure

    fun poll() {
        // step k
        k = k.eval()
    }
}
