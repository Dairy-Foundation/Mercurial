package dev.frozenmilk.dairy.mercurial.continuations

import dev.frozenmilk.dairy.mercurial.continuations.Continuation.Builder
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.exec
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.expression
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.loop
import dev.frozenmilk.dairy.mercurial.environments.Reference
import dev.frozenmilk.dairy.mercurial.processes.Fiber
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.intrinsics.createCoroutineUnintercepted
import kotlin.coroutines.intrinsics.suspendCoroutineUninterceptedOrReturn

object Coroutines {
    class Scope(private val ref: Reference.O<Continuation<Unit>?>) {
        suspend fun yield() {
            val _ = suspendCoroutineUninterceptedOrReturn { k ->
                ref(k)
                COROUTINE_SUSPENDED
            }
        }
    }

    private class Return<T>(val ref: Reference.O<Continuation<Unit>?>) : Continuation<T> {
        override fun resumeWith(result: Result<T>) {
            ref(null)
            Fiber.current.returnRegister.o = result.getOrThrow()
        }
        override val context = EmptyCoroutineContext
    }

    private val resume = Result.success(Unit)

    @JvmSynthetic
    @Suppress("UNCHECKED_CAST")
    fun <T> builder(f: suspend Scope.() -> T) = expression {
        val ctx = object {
            val ref: Reference.O<Continuation<Unit>?> = o {
                f.createCoroutineUnintercepted(scope, Return(ref))
            }
            val scope = Scope(ref)
        }

        loop({ ctx.ref() !== null }, exec { ctx.ref()!!.resumeWith(resume) }) as Builder<T>
    }
}
