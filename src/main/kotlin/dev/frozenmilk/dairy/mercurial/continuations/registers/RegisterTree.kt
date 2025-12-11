package dev.frozenmilk.dairy.mercurial.continuations.registers

import dev.frozenmilk.util.collections.Cons
import dev.frozenmilk.util.collections.Ord

class RegisterTree<T> private constructor(
    k: Register<T>,
    var v: T,
    size: Int,
    l: RegisterTree<*>?,
    r: RegisterTree<*>?,
) {
    var k = k
        private set
    var size = size
        private set
    var l = l
        private set
    var r = r
        private set

    operator fun component1() = k
    operator fun component2() = v
    operator fun component3() = l
    operator fun component4() = r

    @Suppress("UNCHECKED_CAST")
    private fun <T> write(
        k: Register<T>,
        v: T,
        l: RegisterTree<*>?,
        r: RegisterTree<*>?,
    ) = run {
        val self = this as RegisterTree<T>
        self.k = k
        self.v = v
        self.size = size(l) + size(r) + 1
        self.l = l
        self.r = r
        self
    }

    private object FreeList {
        private class ThreadState(var head: Cons<RegisterTree<*>>? = null, var len: Int = 0)

        private val state = ThreadLocal.withInitial { ThreadState() }
        private val EMPTY_REG = object : Register<Nothing?> {}
        fun pop() = state.get().run {
            head?.let {
                val (car, cdr) = it
                Cons.drop(it)
                head = cdr
                len--
                car
            }
        }

        fun drop(t: RegisterTree<*>) = state.get().run {
            t.write(
                EMPTY_REG,
                null,
                null,
                null,
            )
            head = Cons.cons(t, head)
            len++
        }
    }

    companion object {
        fun <T> cons(
            k: Register<T>,
            v: T,
        ) = FreeList.pop()?.write(
            k,
            v,
            null,
            null,
        ) ?: RegisterTree(
            k,
            v,
            1,
            null,
            null,
        )

        fun <T> cons(
            k: Register<T>,
            v: T,
            l: RegisterTree<*>?,
            r: RegisterTree<*>?,
        ) = FreeList.pop()?.write(
            k,
            v,
            l,
            r,
        ) ?: RegisterTree(
            k,
            v,
            size(l) + size(r) + 1,
            l,
            r,
        )

        fun size(tree: RegisterTree<*>?) = tree?.size ?: 0

        private const val DELTA = 3
        private fun balanced(l: Int, r: Int): Boolean = DELTA * (l + 1) >= r + 1
        private fun balanced(
            l: RegisterTree<*>?,
            r: RegisterTree<*>?,
        ): Boolean = balanced(
            size(l), size(r)
        )

        @Suppress("NON_TAIL_RECURSIVE_CALL")
        private tailrec fun balanced(tree: RegisterTree<*>?): Boolean = if (tree == null) true
        else balanced(
            tree.l, tree.r
        ) && balanced(
            tree.r, tree.l
        ) && balanced(tree.l) && balanced(
            tree.r
        )

        private const val GAMMA = 2
        private fun single(
            l: RegisterTree<*>?,
            r: RegisterTree<*>?,
        ): Boolean = size(l) + 1 < GAMMA * (size(r) + 1)

        private fun <T> balance(
            k: Register<T>,
            v: T,
            l: RegisterTree<*>?,
            r: RegisterTree<*>?,
        ) = run {
            val ln = size(l)
            val rn = size(r)
            if (ln + rn < 2) cons(k, v, l, r)
            else if (!balanced(ln, rn)) rotateL(k, v, l, r!!)
            else if (!balanced(rn, ln)) rotateR(k, v, l!!, r)
            else cons(k, v, l, r)
        }

        private fun <T> rotateL(
            k: Register<T>,
            v: T,
            l: RegisterTree<*>?,
            r: RegisterTree<*>,
        ) = if (single(r.l, r.r)) singleL(k, v, l, r)
        else doubleL(k, v, l, r)


        @Suppress("UNCHECKED_CAST")
        private fun <T> singleL(
            k: Register<T>,
            v: T,
            l: RegisterTree<*>?,
            r: RegisterTree<*>,
        ) = run {
            val (rk, rv, rl, rr) = r
            r.write(
                rk as Register<Any?>,
                rv,
                cons(k, v, l, rl),
                rr,
            )
        }

        @Suppress("UNCHECKED_CAST")
        private fun <T> doubleL(
            k: Register<T>,
            v: T,
            l: RegisterTree<*>?,
            r: RegisterTree<*>,
        ) = run {
            val (rk, rv, rl, rr) = r
            val (rlk, rlv, rll, rlr) = rl!!
            r.write(
                rlk as Register<Any?>,
                rlv,
                rl.write(
                    k,
                    v,
                    l,
                    rll,
                ),
                cons(
                    rk as Register<Any?>,
                    rv,
                    rlr,
                    rr,
                ),
            )
        }

        private fun <T> rotateR(
            k: Register<T>,
            v: T,
            l: RegisterTree<*>,
            r: RegisterTree<*>?,
        ) = if (single(l.r, l.l)) singleR(k, v, l, r)
        else doubleR(k, v, l, r)

        @Suppress("UNCHECKED_CAST")
        private fun <T> singleR(
            k: Register<T>,
            v: T,
            l: RegisterTree<*>,
            r: RegisterTree<*>?,
        ) = run {
            val (lk, lv, ll, lr) = l
            l.write(
                lk as Register<Any?>,
                lv,
                ll,
                cons(
                    k,
                    v,
                    lr,
                    r,
                ),
            )
        }

        @Suppress("UNCHECKED_CAST")
        private fun <T> doubleR(
            k: Register<T>,
            v: T,
            l: RegisterTree<*>,
            r: RegisterTree<*>?,
        ) = run {
            val (lk, lv, ll, lr) = l
            val (lrk, lrv, lrl, lrr) = lr!!
            l.write(
                lrk as Register<Any?>,
                lrv,
                lr.write(
                    lk as Register<Any?>,
                    lv,
                    ll,
                    lrl,
                ),
                cons(
                    k,
                    v,
                    lrr,
                    r,
                ),
            )
        }

        @Suppress("NON_TAIL_RECURSIVE_CALL")
        @JvmStatic
        tailrec fun dropAll(tree: RegisterTree<*>) {
            val (_, _, l, r) = tree
            FreeList.drop(tree)
            if (l != null) {
                if (r != null) dropAll(r)
                dropAll(l)
            } else if (r != null) dropAll(r)
        }


        @JvmStatic
        tailrec fun min(tree: RegisterTree<*>): RegisterTree<*> = run {
            return min(tree.l ?: return@run tree)
        }

        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        fun <T> get(
            tree: RegisterTree<*>?,
            register: Register<T>,
        ): RegisterTree<T>? = if (tree == null) null
        else run {
            when (Ord.IdentityHashCode.compare(register, tree.k)) {
                Ord.Result.LT -> get(tree.l, register)
                Ord.Result.GT -> get(tree.r, register)
                Ord.Result.EQ -> tree as RegisterTree<T>
            }
        }

        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        fun <T> add(
            tree: RegisterTree<*>?,
            register: Register<T>,
            v: T,
        ): RegisterTree<*> = if (tree == null) cons(register, v)
        else run {
            val (tk, tv, tl, tr) = tree as RegisterTree<Any>
            when (Ord.IdentityHashCode.compare(register, tk)) {
                Ord.Result.LT -> balance(tk, tv, add(tl, register, v), tr)
                Ord.Result.GT -> balance(tk, tv, tl, add(tr, register, v))
                // replace
                Ord.Result.EQ -> tree.write(register, v, tl, tr)
            }
        }

        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        fun delete(
            tree: RegisterTree<*>?,
            register: Register<*>,
        ): RegisterTree<*>? = if (tree == null) null
        else run {
            val (tk, tv, tl, tr) = tree as RegisterTree<Any?>
            when (Ord.IdentityHashCode.compare(register, tk)) {
                Ord.Result.LT -> balance(tk, tv, delete(tl, register), tr)
                Ord.Result.GT -> balance(tk, tv, tl, delete(tr, register))
                // remove
                Ord.Result.EQ -> deleteJoin(tl, tr)
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun deleteJoin(
            l: RegisterTree<*>?, r: RegisterTree<*>?
        ) = if (l == null) r
        else if (r == null) l
        else {
            val (mink, minv, _, _) = min(r) as RegisterTree<Any?>
            balance(mink, minv, l, deleteMin(r))
        }

        @Suppress("UNCHECKED_CAST")
        @JvmStatic
        fun deleteMin(tree: RegisterTree<*>): RegisterTree<*>? = run {
            val (tk, tv, tl, tr) = tree as RegisterTree<Any?>
            if (tl == null) tr
            else balance(tk, tv, deleteMin(tl), tr)
        }
    }
}