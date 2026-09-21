package dev.frozenmilk.dairy.mercurial.environments

class GeneralPurposeRegister {
    private enum class State {
        O, D, I, B,
    }

    private var state = State.O

    var o: Any? = Unit
        @JvmName("o") get() = when (state) {
            State.O -> field
            State.D if field is Double -> field
            State.D -> {
                field = d
                field
            }

            State.I if field is Int -> field
            State.I -> {
                field = i
                field
            }

            State.B if field is Boolean -> field
            State.B -> {
                field = b
                field
            }
        }
        @JvmName("o") set(value) {
            when (value) {
                is Double -> d = value
                is Int -> i = value
                is Boolean -> b = value
                else -> state = State.O
            }
            field = value
        }

    var d: Double = 0.0
        @JvmName("d") get() = when (state) {
            State.D -> field
            else -> throw IllegalStateException("attempted to access Double register when unset")
        }
        @JvmName("d") set(value) {
            o = Unit
            // correct state
            state = State.D
            field = value
        }

    var i: Int = 0
        @JvmName("i") get() = when (state) {
            State.I -> field
            else -> throw IllegalStateException("attempted to access Int register when unset")
        }
        @JvmName("i") set(value) {
            o = Unit
            // correct state
            state = State.I
            field = value
        }

    var b: Boolean = false
        @JvmName("b") get() = when (state) {
            State.B -> field
            else -> throw IllegalStateException("attempted to access Boolean register when unset")
        }
        @JvmName("b") set(value) {
            o = Unit
            // correct state
            state = State.B
            field = value
        }

    fun copy(other: GeneralPurposeRegister) {
        when (other.state) {
            State.O -> o = other.o
            State.D -> d = other.d
            State.I -> i = other.i
            State.B -> b = other.b
        }
    }
}
