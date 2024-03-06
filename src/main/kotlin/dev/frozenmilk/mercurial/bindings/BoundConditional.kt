package dev.frozenmilk.mercurial.bindings

import dev.frozenmilk.dairy.core.util.supplier.logical.Conditional
import dev.frozenmilk.dairy.core.util.supplier.logical.IConditional
import java.util.function.Supplier

class BoundConditional<T: Comparable<T>>(private var conditional: IConditional<T>) : IConditional<T> {
	constructor(supplier: Supplier<T>) : this(Conditional(supplier))
	override fun lessThan(value: T) = BoundConditional(conditional.lessThan(value))
	override fun lessThanEqualTo(value: T) = BoundConditional(conditional.lessThanEqualTo(value))
	override fun greaterThan(value: T) = BoundConditional(conditional.greaterThan(value))
	override fun greaterThanEqualTo(value: T) = BoundConditional(conditional.greaterThanEqualTo(value))
	override fun bind() = BoundBooleanSupplier(conditional.bind())
}