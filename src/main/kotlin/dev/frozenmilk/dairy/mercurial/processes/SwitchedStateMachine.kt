package dev.frozenmilk.dairy.mercurial.processes

import dev.frozenmilk.dairy.mercurial.continuations.Continuation
import dev.frozenmilk.dairy.mercurial.processes.Channel.Companion.tryPoll
import org.jetbrains.annotations.Contract
import java.util.function.Consumer
import java.util.function.UnaryOperator

abstract class SwitchedStateMachine : Fiberous<Nothing> {
    interface Mode {
        fun enter(previousMode: Mode) {}
        fun eval(): Mode
        fun exit(nextMode: Mode) {}
    }

    companion object {
        @JvmStatic
        @Contract(pure = true)
        fun mode(
            enter: Consumer<Mode>,
            eval: UnaryOperator<Mode>,
            exit: Consumer<Mode>,
        ) = object : Mode {
            override fun enter(previousMode: Mode) = enter.accept(previousMode)
            override fun eval() = eval.apply(this)
            override fun exit(nextMode: Mode) = exit.accept(nextMode)
        }

        @JvmStatic
        @Contract(pure = true)
        fun mode(eval: UnaryOperator<Mode>) = object : Mode {
            override fun eval() = eval.apply(this)
        }

        @JvmStatic
        @Contract(pure = true)
        fun entranceMode(
            enter: Consumer<Mode>,
        ) = object : Mode {
            override fun enter(previousMode: Mode) = enter.accept(previousMode)
            override fun eval() = this
        }

        @JvmStatic
        @Contract(pure = true)
        fun entranceMode(
            enter: Consumer<Mode>,
            eval: UnaryOperator<Mode>,
        ) = object : Mode {
            override fun enter(previousMode: Mode) = enter.accept(previousMode)
            override fun eval() = eval.apply(this)
        }

        @JvmStatic
        @Contract(pure = true)
        fun egressMode(
            eval: UnaryOperator<Mode>,
            exit: Consumer<Mode>,
        ) = object : Mode {
            override fun eval() = eval.apply(this)
            override fun exit(nextMode: Mode) = exit.accept(nextMode)
        }

        @JvmStatic
        @Contract(pure = true)
        fun egressMode(
            exit: Consumer<Mode>,
        ) = object : Mode {
            override fun eval() = this
            override fun exit(nextMode: Mode) = exit.accept(nextMode)
        }
    }

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

            switches.tryPoll { switchTo ->
                _mode?.exit(switchTo)
                switchTo.enter(nextMode)
                _mode = switchTo
            }

            this
        }
    }

    private val spawnable = Spawnable.Builder<Nothing>(k)

    @get:JvmName("fiber")
    final override val fiber = spawnable.spawnLink()

    protected fun <MSG : Any> linkedSingleChannel(): Channel.Single<MSG> = Channel.single<MSG>().also { channel ->
        channel.link(fiber)
    }

    protected fun <MSG : Any> linkedQueueChannel(): Channel.Queue<MSG> = Channel.queue<MSG>().also { channel ->
        channel.link(fiber)
    }

    private val switches = linkedQueueChannel<Mode>()

    protected abstract fun init(): Mode

    protected fun switchTo(mode: Mode) {
        switches.send(mode)
    }

    protected fun switchToNext(mode: Mode) {
        switches.prepend(mode)
    }
}
