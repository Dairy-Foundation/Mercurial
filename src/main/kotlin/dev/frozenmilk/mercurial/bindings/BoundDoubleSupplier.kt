package dev.frozenmilk.mercurial.bindings

import dev.frozenmilk.dairy.core.util.supplier.numeric.EnhancedComparableSupplier
import dev.frozenmilk.dairy.core.util.supplier.numeric.EnhancedDoubleSupplier
import dev.frozenmilk.dairy.core.util.supplier.numeric.IEnhancedNumericSupplier
import dev.frozenmilk.dairy.core.util.supplier.numeric.MotionComponents
import dev.frozenmilk.util.modifier.Modifier
import java.util.function.Supplier

class BoundDoubleSupplier(private val numberSupplier: IEnhancedNumericSupplier<Double>) : IEnhancedNumericSupplier<Double>, EnhancedComparableSupplier<Double, BoundConditional<Double>> {
	constructor(supplier: Supplier<Double>) : this(EnhancedDoubleSupplier(supplier))
	override val supplier
		get() = numberSupplier.supplier
	override val modifier
		get() = numberSupplier.modifier
	override var position
		get() = numberSupplier.position
		set(value) {
			numberSupplier.position = value
		}
	override val velocity
		get() = numberSupplier.velocity
	override val rawVelocity
		get() = numberSupplier.rawVelocity
	override var measurementWindow
		get() = numberSupplier.measurementWindow
		set(value) {
			numberSupplier.measurementWindow = value
		}
	override val acceleration
		get() = numberSupplier.acceleration
	override val rawAcceleration
		get() = numberSupplier.rawAcceleration
	override var autoUpdates
		get() = numberSupplier.autoUpdates
		set(value) {
			numberSupplier.autoUpdates = value
		}

	override fun invalidate() = numberSupplier.invalidate()
	override fun component(motionComponent: MotionComponents) = numberSupplier.component(motionComponent)
	override fun setModifier(modifier: Modifier<Double>) = BoundDoubleSupplier(numberSupplier.setModifier(modifier))
	override fun applyModifier(modifier: Modifier<Double>) = BoundDoubleSupplier(numberSupplier.applyModifier(modifier))
	override fun <N2> merge(supplier: Supplier<out N2>, merge: (Double, N2) -> Double) = BoundDoubleSupplier(numberSupplier.merge(supplier, merge))
	override fun componentError(motionComponent: MotionComponents, target: Double) = numberSupplier.componentError(motionComponent, target)
	override fun findErrorRawAcceleration(target: Double) = numberSupplier.findErrorRawAcceleration(target)
	override fun findErrorAcceleration(target: Double) = numberSupplier.findErrorAcceleration(target)
	override fun findErrorRawVelocity(target: Double) = numberSupplier.findErrorRawVelocity(target)
	override fun findErrorVelocity(target: Double) = numberSupplier.findErrorVelocity(target)
	override fun findErrorPosition(target: Double) = numberSupplier.findErrorPosition(target)
	override val dependencies
		get() = numberSupplier.dependencies

	override fun conditionalBindPosition() = BoundConditional(this::position)
	override fun conditionalBindVelocity() = BoundConditional(this::velocity)
	override fun conditionalBindVelocityRaw() = BoundConditional(this::rawVelocity)
	override fun conditionalBindAcceleration() = BoundConditional(this::acceleration)
	override fun conditionalBindAccelerationRaw() = BoundConditional(this::rawAcceleration)
}