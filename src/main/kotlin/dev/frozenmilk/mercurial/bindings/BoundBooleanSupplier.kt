package dev.frozenmilk.mercurial.bindings

import dev.frozenmilk.dairy.core.dependency.Dependency
import dev.frozenmilk.dairy.core.dependency.lazy.Yielding
import dev.frozenmilk.dairy.core.util.supplier.logical.EnhancedBooleanSupplier
import dev.frozenmilk.dairy.core.util.supplier.logical.IEnhancedBooleanSupplier
import dev.frozenmilk.dairy.core.wrapper.Wrapper
import dev.frozenmilk.mercurial.commands.Command
import dev.frozenmilk.mercurial.commands.Lambda
import java.util.function.BooleanSupplier

@Suppress("INAPPLICABLE_JVM_NAME")
class BoundBooleanSupplier private constructor(private val booleanSupplier: BooleanSupplier, private val risingDebounce: Long, private val fallingDebounce: Long) : IEnhancedBooleanSupplier<BoundBooleanSupplier> {
	constructor(booleanSupplier: BooleanSupplier, risingDebounce: Double, fallingDebounce: Double) : this(booleanSupplier, (risingDebounce * 1E9).toLong(), (fallingDebounce * 1E9).toLong())
	constructor(booleanSupplier: BooleanSupplier) : this(booleanSupplier, 0, 0)
	constructor(booleanSupplier: IEnhancedBooleanSupplier<*>, risingDebounce: Double, fallingDebounce: Double) : this(booleanSupplier::state, (risingDebounce * 1E9).toLong(), (fallingDebounce * 1E9).toLong())
	constructor(booleanSupplier: IEnhancedBooleanSupplier<*>) : this(booleanSupplier::state, 0, 0)
	private var previous = booleanSupplier.asBoolean
	private var current = previous
	private var _toggleTrue = current
	@get:JvmName("toggleTrue")
	override val toggleTrue
		get() = _toggleTrue
	private var _toggleFalse = current
	@get:JvmName("toggleFalse")
	override val toggleFalse
		get() = _toggleFalse
	private var timeMarker = 0L
	private fun update() {
		previous = current
		val time = System.nanoTime()
		if(!current && booleanSupplier.asBoolean){
			if(time - timeMarker >= risingDebounce) {
				current = true
				_toggleTrue = !_toggleTrue
				timeMarker = time
			}
		}
		else if (current && !booleanSupplier.asBoolean) {
			if (time - timeMarker >= fallingDebounce) {
				current = false
				_toggleFalse = !_toggleFalse
				timeMarker = time
			}
		}
		else {
			timeMarker = time
		}
	}

	private var valid = false

	/**
	 * causes the next call to [get] to update this supplier
	 */
	override fun invalidate() {
		valid = false
	}

	/**
	 * returns the current boolean state of this
	 */
	@get:JvmName("state")
	override val state: Boolean get() {
		if (!valid) {
			update()
			valid = true
		}
		return current
	}

	/**
	 * a rising edge detector for this
	 */
	@get:JvmName("onTrue")
	override val onTrue: Boolean get() { return state && !previous }

	/**
	 * a falling edge detector for this
	 */
	@get:JvmName("onFalse")
	override val onFalse: Boolean get() { return !state && previous }

	/**
	 * non-mutating
	 *
	 * @param debounce is applied to both the rising and falling edges
	 */
	override fun debounce(debounce: Double) = BoundBooleanSupplier(this.booleanSupplier, (debounce * 1E9).toLong(), (debounce * 1E9).toLong())

	/**
	 * non-mutating
	 *
	 * @param rising is applied to the rising edge
	 * @param falling is applied to the falling edge
	 */
	override fun debounce(rising: Double, falling: Double) = BoundBooleanSupplier(this.booleanSupplier, (rising * 1E9).toLong(), (falling * 1E9).toLong())

	/**
	 * non-mutating
	 *
	 * @param debounce is applied to the rising edge
	 */
	override fun debounceRisingEdge(debounce: Double) = BoundBooleanSupplier(this.booleanSupplier, (debounce * 1E9).toLong(), this.fallingDebounce)

	/**
	 * non-mutating
	 *
	 * @param debounce is applied to the falling edge
	 */
	override fun debounceFallingEdge(debounce: Double) = BoundBooleanSupplier(this.booleanSupplier, this.risingDebounce, (debounce * 1E9).toLong())

	/**
	 * non-mutating
	 *
	 * @return a new BoundBooleanSupplier that combines the two conditions
	 */
	override infix fun and(booleanSupplier: BooleanSupplier) = BoundBooleanSupplier(BooleanSupplier { this.state && booleanSupplier.asBoolean })

	/**
	 * non-mutating
	 *
	 * @return a new BoundBooleanSupplier that combines the two conditions
	 */
	override infix fun and(booleanSupplier: IEnhancedBooleanSupplier<*>) = BoundBooleanSupplier(BooleanSupplier { this.state && booleanSupplier.state })

	/**
	 * non-mutating
	 *
	 * @return a new BoundBooleanSupplier that combines the two conditions
	 */
	override fun or(booleanSupplier: BooleanSupplier) = BoundBooleanSupplier(BooleanSupplier { this.state || booleanSupplier.asBoolean })

	/**
	 * non-mutating
	 *
	 * @return a new BoundBooleanSupplier that combines the two conditions
	 */
	override infix fun or(booleanSupplier: IEnhancedBooleanSupplier<*>) = BoundBooleanSupplier(BooleanSupplier { this.state || booleanSupplier.state })

	/**
	 * non-mutating
	 *
	 * @return a new BoundBooleanSupplier that combines the two conditions
	 */
	override infix fun xor(booleanSupplier: BooleanSupplier) = BoundBooleanSupplier(BooleanSupplier { this.state xor booleanSupplier.asBoolean })

	/**
	 * non-mutating
	 *
	 * @return a new BoundBooleanSupplier that combines the two conditions
	 */
	override infix fun xor(booleanSupplier: IEnhancedBooleanSupplier<*>) = BoundBooleanSupplier(BooleanSupplier { this.state xor booleanSupplier.state })

	/**
	 * non-mutating
	 *
	 * @return a new BoundBooleanSupplier that has the inverse of this, and keeps the debounce information
	 */
	override operator fun not() = BoundBooleanSupplier({ !this.state }, risingDebounce, fallingDebounce)

	//
	// Impl Feature:
	//
	override var dependency: Dependency<*> = Yielding

	init {
		register()
	}

	/**
	 * if this automatically updates, by calling [invalidate] and [state]
	 */
	override var autoUpdates = true
	private fun autoUpdatePost() {
		if (autoUpdates) {
			invalidate()
			state
		}
	}

	override fun postUserInitHook(opMode: Wrapper) = autoUpdatePost()
	override fun postUserInitLoopHook(opMode: Wrapper) = autoUpdatePost()
	override fun postUserStartHook(opMode: Wrapper) = autoUpdatePost()
	override fun postUserLoopHook(opMode: Wrapper) = autoUpdatePost()
	override fun cleanup(opMode: Wrapper) {
		deregister()
	}

	//
	// Commands:
	//

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
		Binding.runCommand(this::onTrue, Lambda.from(toRun).addFinish { !state })
		return this
	}

	/**
	 * registers [toRun] to be triggered when this condition is false, and ends it early if it becomes true
	 */
	fun whileFalse(toRun: Command): BoundBooleanSupplier {
		Binding.runCommand(this::onFalse, Lambda.from(toRun).addFinish { it || state })
		return this
	}

	/**
	 * registers [toRun] to be triggered when this condition is true, and re-schedule [toRun] until it becomes false, and then will end it early
	 */
	fun untilFalse(toRun: Command): BoundBooleanSupplier {
		Binding.runCommand(this::onTrue, Lambda.from(toRun).addEnd { _ -> toRun.schedule() }.addFinish { !state })
		return this
	}

	/**
	 * registers [toRun] to be triggered when this condition is false, and re-schedule [toRun] until it becomes true, and then will end it early
	 */
	fun untilTrue(toRun: Command): BoundBooleanSupplier {
		Binding.runCommand(this::onFalse, Lambda.from(toRun).addEnd { _ -> toRun.schedule() }.addFinish { it || state })
		return this
	}
	/**
	 * registers [toRun] to be triggered when [EnhancedBooleanSupplier.toggleTrue] becomes true, and ends it early if it becomes false
	 */
	fun toggleTrue(toRun: Command): BoundBooleanSupplier {
		Binding.runCommand(this::toggleTrue, Lambda.from(toRun).addFinish { it || !toggleTrue })
		return this
	}

	/**
	 * registers [toRun] to be triggered when [EnhancedBooleanSupplier.toggleFalse] becomes true, and ends it early if it becomes false
	 */
	fun toggleFalse(toRun: Command): BoundBooleanSupplier {
		Binding.runCommand(this::toggleFalse, Lambda.from(toRun).addFinish { it || !toggleFalse })
		return this
	}
}
