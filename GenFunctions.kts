#!/usr/bin/env kotlin

import GenComponents.Joinable.Companion.plus

class GenComponents(
    val generics: Joinable,
    val name: Joinable?,
    val params: Joinable?,
    val ret: Joinable,
    val body: Body,
) {
    fun render() = """
    @JvmStatic
    @Contract(pure = true)
    fun <$generics> ${name!!}(
        f: Expression<*>.(
            ${"self: $ret,\n" + " ".repeat(12)}$params,
        ) -> Builder<T>,
    ): $ret =
${body()}"""

    class Joinable(val s: String, val sep: String, val k: Joinable?) {
        override fun toString() = if (k != null) "$s$sep$k"
        else s

        companion object {
            operator fun Joinable?.plus(rhs: Joinable?): Joinable? =
                if (this == null) rhs
                else if (rhs == null) this
                else Joinable(
                    s,
                    sep,
                    k + rhs,
                )
        }
    }

    class Body(val s: String, val params: Joinable?, val k: Body?) {
        operator fun invoke() = Body(s, Joinable("self", ", ", params), k)(8, null)

        operator fun invoke(ident: Int, params: Joinable?): String = run {
            val params = params + this.params

            " ".repeat(ident) + s + " {" + (if (this.params != null) " ${this.params} ->\n" else "\n") +
                    (if (k != null) k(ident + 4, params)
                    else " ".repeat(ident + 4) + "f($params)\n") +
                    " ".repeat(ident) + "}\n"
        }
    }
}

class Depth private constructor(
    val lower: Char
) {
    val upper = lower.uppercase()

    val next
        get() = Depth(lower.plus(1))

    companion object {
        val A = Depth('a')
    }
}

sealed class Generator {
    protected abstract fun components(depth: Depth): GenComponents
    fun components() = components(Depth.A)

    object Return : Generator() {
        override fun components(depth: Depth) = GenComponents(
            generics = GenComponents.Joinable("T", ", ", null),
            name = null,
            params = null,
            ret = GenComponents.Joinable("Return<T>", "", null),
            body = GenComponents.Body("body", null, null)
        )
    }

    class O(
        val k: Generator,
    ) : Generator() {
        override fun components(depth: Depth) = run {
            val k = k.components(depth.next)
            GenComponents(
                generics = GenComponents.Joinable(depth.upper, ", ", k.generics),
                name = GenComponents.Joinable("O", "", k.name),
                params = GenComponents.Joinable(
                    "Reference.O<${depth.upper}>",
                    ",\n" + " ".repeat(12),
                    k.params
                ),
                ret = GenComponents.Joinable(
                    "Parameter.O<${depth.upper}",
                    ", ",
                    k.ret + GenComponents.Joinable(">", "", null)
                ),
                body = GenComponents.Body(
                    if (depth === Depth.A) "param.o" else "o",
                    GenComponents.Joinable(depth.lower.toString(), ", ", null),
                    k.body,
                ),
            )
        }
    }

    class D(
        val k: Generator,
    ) : Generator() {
        override fun components(depth: Depth) = run {
            val k = k.components(depth.next)
            GenComponents(
                generics = k.generics,
                name = GenComponents.Joinable("D", "", k.name),
                params = GenComponents.Joinable("Reference.D", ",\n" + " ".repeat(12), k.params),
                ret = GenComponents.Joinable(
                    "Parameter.D<",
                    "",
                    k.ret + GenComponents.Joinable(">", "", null)
                ),
                body = GenComponents.Body(
                    if (depth === Depth.A) "param.d" else "d",
                    GenComponents.Joinable(depth.lower.toString(), ", ", null),
                    k.body,
                ),
            )
        }
    }

    class I(
        val k: Generator,
    ) : Generator() {
        override fun components(depth: Depth) = run {
            val k = k.components(depth.next)
            GenComponents(
                generics = k.generics,
                name = GenComponents.Joinable("I", "", k.name),
                params = GenComponents.Joinable("Reference.I", ",\n" + " ".repeat(12), k.params),
                ret = GenComponents.Joinable(
                    "Parameter.I<",
                    "",
                    k.ret + GenComponents.Joinable(">", "", null)
                ),
                body = GenComponents.Body(
                    if (depth === Depth.A) "param.i" else "i",
                    GenComponents.Joinable(depth.lower.toString(), ", ", null),
                    k.body,
                ),
            )
        }
    }

    class B(
        val k: Generator,
    ) : Generator() {
        override fun components(depth: Depth) = run {
            val k = k.components(depth.next)
            GenComponents(
                generics = k.generics,
                name = GenComponents.Joinable("B", "", k.name),
                params = GenComponents.Joinable("Reference.B", ",\n" + " ".repeat(12), k.params),
                ret = GenComponents.Joinable(
                    "Parameter.B<",
                    "",
                    k.ret + GenComponents.Joinable(">", "", null)
                ),
                body = GenComponents.Body(
                    if (depth === Depth.A) "param.b" else "b",
                    GenComponents.Joinable(depth.lower.toString(), ", ", null),
                    k.body,
                ),
            )
        }
    }
}

fun generateAll(count: Int) {
    fun generate(depth: Int, acc: Generator) {
        val o = Generator.O(acc)
        print(o.components().render())

        val d = Generator.D(acc)
        print(d.components().render())

        val i = Generator.I(acc)
        print(i.components().render())

        val b = Generator.B(acc)
        print(b.components().render())

        if (depth < count) {
            generate(depth + 1, o)
            generate(depth + 1, d)
            generate(depth + 1, i)
            generate(depth + 1, b)
        }
    }

    generate(1, Generator.Return)
}

generateAll(3)
