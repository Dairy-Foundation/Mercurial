package dev.frozenmilk.dairy.mercurial.continuations

abstract class FactoryClosure : Closure {
    protected val stackTrace = Throwable().stackTrace.let { stackTrace ->
        stackTrace.copyOfRange(1, stackTrace.size)
    }

    protected fun Continuation(
        name: String,
        application: Application,
    ) = object : Continuation {
        override val stackTrace = this@FactoryClosure.stackTrace
        override fun apply() = application.apply(this)
        override fun toString() = name
    }
}