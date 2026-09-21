package dev.frozenmilk.dairy.mercurial.continuations

import dev.frozenmilk.dairy.mercurial.Tracing.traced
import dev.frozenmilk.dairy.mercurial.continuations.Continuation.Builder
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.function
import dev.frozenmilk.dairy.mercurial.continuations.Continuations.unreachable
import dev.frozenmilk.dairy.mercurial.continuations.PredicateChain.Companion.link
import dev.frozenmilk.dairy.mercurial.environments.Reference
import dev.frozenmilk.dairy.mercurial.processes.Fiber
import dev.frozenmilk.util.collections.Ord
import dev.frozenmilk.util.collections.WBT
import java.util.function.Predicate
import java.util.function.Supplier
import kotlin.reflect.KClass

sealed class Cases<M, T> {
    companion object {
        val ConstMap = WBT.Make(Const<*, *>::const, Ord.HashCode)
        val TypeMap = WBT.Make(Type<*, *>::type, Ord.HashCode)
    }

    data class Const<C, T>(val const: C, val chain: PredicateChain<C, T>)
    typealias Consts<C, T> = WBT.Tree<Const<C, T>>

    data class Type<C : Any, T>(val type: Class<C>, val chain: PredicateChain<C, T>)
    typealias Types<C, T> = WBT.Tree<Type<C, T>>

    class Exhaustive<M, T>(val inner: Continuation.Match2.Builder<M, T>) : Cases<M, T>()

    class Inexhaustive<M, T> private constructor(
        val consts: Consts<out M, Builder<T>>?,
        val types: Types<out M & Any, Builder<T>>?,
        val default: PredicateChain.Unterminated<M, Builder<T>>?,
    ) : Cases<M, T>() {
        // internals
        companion object {
            @Suppress("UNCHECKED_CAST")
            private fun <C> bindExpr() = Supplier { Fiber.current.returnRegister.o as C }
        }

        constructor() : this(
            ConstMap.empty(),
            TypeMap.empty(),
            null,
        )

        // consts

        @Suppress("UNCHECKED_CAST")
        fun <C : M> constCase(const: C, then: Builder<T>) = Inexhaustive(
            ConstMap.mapEntry(consts, const) { entry ->
                entry as Const<C, Builder<T>>?
                Const(
                    const,
                    entry?.chain.link(then),
                )
            },
            types,
            default,
        )

        @Suppress("UNCHECKED_CAST")
        fun <C : M> constCase(const: C, guard: Predicate<C>, then: Builder<T>) = Inexhaustive(
            ConstMap.mapEntry(consts, const) { entry ->
                entry as Const<C, Builder<T>>?
                Const(
                    const,
                    entry?.chain.link(guard, then),
                )
            },
            types,
            default,
        )

        // types

        @Suppress("UNCHECKED_CAST")
        fun <C : M & Any> typeCase(cls: Class<C>, then: Builder<T>) = Inexhaustive(
            consts,
            TypeMap.mapEntry(types, cls) { entry ->
                entry as Type<C, Builder<T>>?
                Type(
                    cls,
                    entry?.chain.link(then),
                )
            },
            default,
        )

        fun <C : M & Any> typeCase(cls: Class<C>, then: Parameter.O<C, Return<T>>) =
            typeCase(cls, then * bindExpr())

        fun <C : M & Any> typeCase(cls: Class<C>, then: Expression<*>.(Reference.O<C>) -> Builder<T>) =
            typeCase(cls, function { _, ref -> then(ref) })

        @Suppress("UNCHECKED_CAST")
        fun <C : M & Any> typeCase(cls: Class<C>, guard: Predicate<C>, then: Builder<T>) = Inexhaustive(
            consts,
            TypeMap.mapEntry(types, cls) { entry ->
                entry as Type<C, Builder<T>>?
                Type(
                    cls,
                    entry?.chain.link(guard, then),
                )
            },
            default,
        )

        fun <C : M & Any> typeCase(
            cls: Class<C>,
            guard: Predicate<C>,
            then: Parameter.O<C, Return<T>>,
        ) = typeCase(cls, guard, then * bindExpr())

        fun <C : M & Any> typeCase(
            cls: Class<C>,
            guard: Predicate<C>,
            then: Expression<*>.(Reference.O<C>) -> Builder<T>,
        ) = typeCase(cls, guard, function { _, ref -> then(ref) })

        // ktypes

        fun <C : M & Any> typeCase(cls: KClass<C>, then: Builder<T>) = typeCase(cls.javaObjectType, then)

        fun <C : M & Any> typeCase(cls: KClass<C>, then: Parameter.O<C, Return<T>>) =
            typeCase(cls.javaObjectType, then)

        fun <C : M & Any> typeCase(cls: KClass<C>, then: Expression<*>.(Reference.O<C>) -> Builder<T>) =
            typeCase(cls.javaObjectType, then)

        fun <C : M & Any> typeCase(cls: KClass<C>, guard: Predicate<C>, then: Builder<T>) =
            typeCase(cls.javaObjectType, guard, then)

        fun <C : M & Any> typeCase(
            cls: KClass<C>,
            guard: Predicate<C>,
            then: Parameter.O<C, Return<T>>
        ) = typeCase(cls.javaObjectType, guard, then)

        fun <C : M & Any> typeCase(
            cls: KClass<C>,
            guard: Predicate<C>,
            then: Expression<*>.(Reference.O<C>) -> Builder<T>,
        ) = typeCase(cls.javaObjectType, guard, then)

        // inlines

        inline fun <reified C : M & Any> typeCase(then: Builder<T>) = typeCase(C::class, then)

        inline fun <reified C : M & Any> typeCase(then: Parameter.O<C, Return<T>>) =
            typeCase(C::class, then)

        inline fun <reified C : M & Any> typeCase(noinline then: Expression<*>.(Reference.O<C>) -> Builder<T>) =
            typeCase(C::class, then)

        inline fun <reified C : M & Any> typeCase(guard: Predicate<C>, then: Builder<T>) =
            typeCase(C::class, guard, then)

        inline fun <reified C : M & Any> typeCase(
            guard: Predicate<C>,
            then: Parameter.O<C, Return<T>>
        ) = typeCase(C::class, guard, then)

        inline fun <reified C : M & Any> typeCase(
            guard: Predicate<C>, noinline then: Expression<*>.(Reference.O<C>) -> Builder<T>
        ) = typeCase(C::class, guard, then)

        // defaults

        fun defaultCase(then: Builder<T>) = traced { trace ->
            Exhaustive(
                Continuation.Match2.Builder(
                    trace,
                    consts,
                    types,
                    default.link(then),
                )
            )
        }

        fun defaultCase(then: Parameter.O<M, Return<T>>) = defaultCase(then * bindExpr())

        fun defaultCase(then: Expression<*>.(Reference.O<M>) -> Builder<T>) =
            defaultCase(function { _, ref -> then(ref) })

        fun defaultCase(guard: Predicate<M>, then: Builder<T>) = Inexhaustive(
            consts,
            types,
            default.link(guard, then),
        )

        fun defaultCase(guard: Predicate<M>, then: Parameter.O<M, Return<T>>) =
            defaultCase(guard, then * bindExpr())

        fun defaultCase(guard: Predicate<M>, then: Expression<*>.(Reference.O<M>) -> Builder<T>) =
            defaultCase(guard, function { _, ref -> then(ref) })

        fun assertExhaustive() = defaultCase(unreachable)
    }
}
