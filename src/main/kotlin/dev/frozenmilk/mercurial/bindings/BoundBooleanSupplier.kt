package dev.frozenmilk.mercurial.bindings

import dev.frozenmilk.dairy.core.util.supplier.logical.EnhancedBooleanSupplier
import dev.frozenmilk.dairy.core.util.supplier.logical.IEnhancedBooleanSupplier
import dev.frozenmilk.mercurial.commands.Command
import java.util.function.Supplier

@JvmInline
value class BoundBooleanSupplier(val supplier: IEnhancedBooleanSupplier) : IEnhancedBooleanSupplier by supplier {
	constructor(supplier: Supplier<Boolean>) : this(EnhancedBooleanSupplier(supplier))
	/**
	 * registers [toRun] to be triggered when this condition becomes true
	 */
	fun onTrue(toRun: Command): BoundBooleanSupplier {
		Binding.runCommand(this::onTrue, toRun)
		return this
	}

	/**
	 * registers [toRun] to be triggered when this condition becomes false
	 */
	fun onFalse(toRun: Command): BoundBooleanSupplier {
		Binding.runCommand(this::onFalse, toRun)
		return this
	}

	/**
	 * registers [toCancel] to be cancelled when this condition becomes true
	 */
	fun cancelOnTrue(toCancel: Command): BoundBooleanSupplier {
		Binding.cancelCommand(this::onTrue, toCancel)
		return this
	}

	/**
	 * registers [toCancel] to be cancelled when this condition becomes false
	 */
	fun cancelOnFalse(toCancel: Command): BoundBooleanSupplier {
		Binding.cancelCommand(this::onFalse, toCancel)
		return this
	}

	/**
	 * registers [toRun] to be triggered when this condition is true, and ends it early if it becomes false
	 */
	fun whileTrue(toRun: Command): BoundBooleanSupplier {
		Binding.runCommand(this::onTrue, toRun.intoLambdaCommand().addFinish { !state })
		return this
	}

	/**
	 * registers [toRun] to be triggered when this condition is false, and ends it early if it becomes true
	 */
	fun whileFalse(toRun: Command): BoundBooleanSupplier {
		Binding.runCommand(this::onFalse, toRun.intoLambdaCommand().addFinish(this::state))
		return this
	}

	/**
	 * registers [toRun] to be triggered when this condition is true, and re-schedule [toRun] until it becomes false, and then will end it early
	 */
	fun untilFalse(toRun: Command): BoundBooleanSupplier {
		Binding.runCommand(this::onTrue, toRun.intoLambdaCommand().addEnd { _ -> toRun.schedule() }.addFinish { !state })
		return this
	}

	/**
	 * registers [toRun] to be triggered when this condition is false, and re-schedule [toRun] until it becomes true, and then will end it early
	 */
	fun untilTrue(toRun: Command): BoundBooleanSupplier {
		Binding.runCommand(this::onFalse, toRun.intoLambdaCommand().addEnd { _ -> toRun.schedule() }.addFinish(this::state))
		return this
	}
	/**
	 * registers [toRun] to be triggered when this condition is true, and ends it early if it becomes false
	 *
	 * @see EnhancedBooleanSupplier.toggleTrue
	 */
	fun toggleTrue(toRun: Command): BoundBooleanSupplier {
		Binding.runCommand(this::toggleTrue, toRun.intoLambdaCommand().addFinish(this::toggleTrue))
		return this
	}

	/**
	 * registers [toRun] to be triggered when this condition is false, and ends it early if it becomes true
	 *
	 * @see EnhancedBooleanSupplier.toggleFalse
	 */
	fun toggleFalse(toRun: Command): BoundBooleanSupplier {
		Binding.runCommand(this::toggleFalse, toRun.intoLambdaCommand().addFinish(this::toggleFalse))
		return this
	}

	//
	// Debounce Op Overrides
	//

	override fun debounce(rising: Double, falling: Double) = BoundBooleanSupplier(supplier.debounce(rising, falling))
	override fun debounce(debounce: Double) = BoundBooleanSupplier(supplier.debounce(debounce))
	override fun debounceRisingEdge(debounce: Double) = BoundBooleanSupplier(supplier.debounceRisingEdge(debounce))
	override fun debounceFallingEdge(debounce: Double) = BoundBooleanSupplier(supplier.debounceFallingEdge(debounce))

	//
	// Logical op overrides
	//

	override fun and(booleanSupplier: Supplier<Boolean>) = BoundBooleanSupplier(supplier and  booleanSupplier)
	override fun and(booleanSupplier: IEnhancedBooleanSupplier) = BoundBooleanSupplier(supplier and  booleanSupplier)

	override fun or(booleanSupplier: Supplier<Boolean>) = BoundBooleanSupplier(supplier or  booleanSupplier)
	override fun or(booleanSupplier: IEnhancedBooleanSupplier) = BoundBooleanSupplier(supplier or  booleanSupplier)

	override fun xor(booleanSupplier: Supplier<Boolean>) = BoundBooleanSupplier(supplier xor booleanSupplier)
	override fun xor(booleanSupplier: IEnhancedBooleanSupplier) = BoundBooleanSupplier(supplier xor booleanSupplier)

	override fun not() = BoundBooleanSupplier(!supplier)
}