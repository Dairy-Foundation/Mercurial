package dev.frozenmilk.dairy.mercurial.continuations

import dev.frozenmilk.dairy.mercurial.Tracing.plus
import dev.frozenmilk.dairy.mercurial.continuations.Cases.Companion.ConstMap
import dev.frozenmilk.dairy.mercurial.continuations.Cases.Companion.TypeMap
import dev.frozenmilk.dairy.mercurial.continuations.Continuation.Builder
import dev.frozenmilk.dairy.mercurial.continuations.PredicateChain.Companion.compile
import dev.frozenmilk.util.collections.Cons

sealed class Match<M, T> {
    class Exhaustive<M, T>(
        val trace: StackTraceElement?,
        val value: Builder<M>,
        val cases: Cases.Exhaustive<M, T>,
    ) : Match<M, T>(), Builder<T> {
        override fun compile(
            trace: Cons<StackTraceElement>?,
            k: Continuation,
        ) = run {
            val trace = this.trace + trace
            value.compile(trace, cases.inner.compile(trace, k))
        }
    }

    class Inexhaustive<M, T>(
        val trace: StackTraceElement?,
        val value: Builder<M>,
        val cases: Cases.Inexhaustive<M, T>,
    ) : Match<M, T>(), Builder<Any?> {
        @Suppress("UNCHECKED_CAST")
        override fun compile(
            trace: Cons<StackTraceElement>?,
            k: Continuation,
        ) = run {
            val trace = this.trace + trace
            val chainCompiler = { builder: Builder<T> -> builder.compile(trace, k) }

            val default = cases.default.compile(chainCompiler) { k }
            val consts = ConstMap.map(cases.consts) { (const, chain) ->
                Continuation.Match2.Const(
                    const,
                    chain.compile(chainCompiler, default) as (Any?) -> Continuation,
                )
            }
            val types = TypeMap.map(cases.types) { (cls, chain) ->
                Continuation.Match2.Type(
                    cls,
                    chain.compile(chainCompiler, default) as (Any?) -> Continuation,
                )
            }

            value.compile(
                trace,
                Continuation.Match2(
                    consts,
                    types,
                    default as (Any?) -> Continuation,
                ),
            )
        }
    }
}
