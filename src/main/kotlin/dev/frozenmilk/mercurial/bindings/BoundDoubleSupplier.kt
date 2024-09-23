package dev.frozenmilk.mercurial.bindings

import dev.frozenmilk.dairy.core.util.supplier.numeric.EnhancedComparableNumericSupplier
import dev.frozenmilk.dairy.core.util.supplier.numeric.EnhancedDoubleSupplier
import dev.frozenmilk.dairy.core.util.supplier.numeric.IEnhancedNumericSupplier
import dev.frozenmilk.dairy.core.util.supplier.numeric.MotionComponents
import java.util.function.Supplier

@Suppress("INAPPLICABLE_JVM_NAME")
class BoundDoubleSupplier(private val numberSupplier: IEnhancedNumericSupplier<Double>) : EnhancedComparableNumericSupplier<Double, BoundConditional<Double>> {
	constructor(supplier: Supplier<Double>) : this(EnhancedDoubleSupplier(supplier))
	@get:JvmName("state")
	@set:JvmName("state")
	override var state
		get() = numberSupplier.state
		set(value) {
			numberSupplier.state = value
		}
	@get:JvmName("velocity")
	override val velocity
		get() = numberSupplier.velocity
	@get:JvmName("rawVelocity")
	override val rawVelocity
		get() = numberSupplier.rawVelocity
	@get:JvmName("acceleration")
	override val acceleration
		get() = numberSupplier.acceleration
	@get:JvmName("rawAcceleration")
	override val rawAcceleration
		get() = numberSupplier.rawAcceleration
	override var measurementWindow
		get() = numberSupplier.measurementWindow
		set(value) {
			numberSupplier.measurementWindow = value
		}
	override fun invalidate() = numberSupplier.invalidate()
	override fun get(motionComponent: MotionComponents) = numberSupplier.get(motionComponent)
	override fun conditionalBindState() = BoundConditional(this::state)
	override fun conditionalBindVelocity() = BoundConditional(this::velocity)
	override fun conditionalBindVelocityRaw() = BoundConditional(this::rawVelocity)
	override fun conditionalBindAcceleration() = BoundConditional(this::acceleration)
	override fun conditionalBindAccelerationRaw() = BoundConditional(this::rawAcceleration)
}