package dev.frozenmilk.dairy.mercurial.continuations

import java.util.function.Predicate

sealed class PredicateChain<C, T> {
    data class Unterminated<C, T>(
        val prior: Unterminated<C, T>?,
        val guard: Predicate<C>,
        val then: T,
    ) : PredicateChain<C, T>()

    data class Terminated<C, T>(
        val prior: Unterminated<C, T>?,
        val then: T,
    ) : PredicateChain<C, T>()

    companion object {
        fun <C, T> PredicateChain<C, T>?.link(
            guard: Predicate<C>,
            then: T,
        ): Unterminated<C, T> = when (this) {
            is Terminated -> error("Branch is already matched exhaustively")
            is Unterminated -> Unterminated(
                this,
                guard,
                then,
            )

            null -> Unterminated(
                this,
                guard,
                then,
            )
        }

        fun <C, T> PredicateChain<C, T>?.tryLink(
            guard: Predicate<C>,
            then: T,
        ) = this as? Terminated ?: link(guard, then)

        fun <C, T> PredicateChain<C, T>?.link(then: T): Terminated<C, T> = when (this) {
            is Terminated -> error("Branch is already matched exhaustively")
            is Unterminated -> Terminated(
                this,
                then,
            )

            null -> Terminated(
                this,
                then,
            )
        }

        fun <C, T> PredicateChain<C, T>?.tryLink(then: T) =
            this as? Terminated ?: link(then)

        fun <C, T, U> PredicateChain<C, T>?.compile(
            f: (T) -> U,
            acc: (C) -> U,
        ): (C) -> U = when (this) {
            is Terminated -> {
                val then = f(then)
                prior.compile(f) { then }
            }

            is Unterminated -> {
                val then = f(then).let { then ->
                    { value: C ->
                        if (guard.test(value)) then
                        else acc(value)
                    }
                }
                prior.compile(f, then)
            }

            null -> acc
        }
    }
}

