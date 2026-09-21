package dev.frozenmilk.dairy.mercurial.processes

import dev.frozenmilk.dairy.mercurial.continuations.Continuation

abstract class StateMachine {
    interface Mode {
        fun enter(previousMode: Mode) {}
        fun eval(): Mode
    }

    protected abstract fun init(): Mode

    private var _mode: Mode? = null

    @get:JvmName("mode")
    val mode: Mode
        get() = checkNotNull(_mode)

    private val k = object : Continuation {
        override fun eval() = run {
            if (_mode === null) {
                val nextMode = init()
                _mode = nextMode
                nextMode.enter(nextMode)
            }
            val mode = mode
            val nextMode = mode.eval()
            if (nextMode != mode) nextMode.enter(mode)
            _mode = nextMode
            this
        }
    }

    private val spawnable = Spawnable.Builder<Nothing>(k)

    @get:JvmName("fiber")
    val fiber = spawnable.spawnLink()
}
