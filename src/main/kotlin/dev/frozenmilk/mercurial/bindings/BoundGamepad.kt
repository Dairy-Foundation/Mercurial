package dev.frozenmilk.mercurial.bindings

import dev.frozenmilk.dairy.core.util.supplier.logical.EnhancedBooleanSupplier
import dev.frozenmilk.dairy.core.util.supplier.numeric.EnhancedDoubleSupplier
import dev.frozenmilk.dairy.pasteurized.PasteurizedGamepad

class BoundGamepad(gamepad: PasteurizedGamepad<EnhancedDoubleSupplier, EnhancedBooleanSupplier>) : PasteurizedGamepad<BoundDoubleSupplier, BoundBooleanSupplier> {
	/**
	 * left analog stick horizontal axis
	 */
	override var leftStickX = BoundDoubleSupplier(gamepad.leftStickX)

	/**
	 * left analog stick vertical axis
	 */
	override var leftStickY = BoundDoubleSupplier(gamepad.leftStickY)

	/**
	 * right analog stick horizontal axis
	 */
	override var rightStickX = BoundDoubleSupplier(gamepad.rightStickX)

	/**
	 * right analog stick vertical axis
	 */
	override var rightStickY = BoundDoubleSupplier(gamepad.rightStickY)

	/**
	 * dpad up
	 */
	override var dpadUp = BoundBooleanSupplier(gamepad.dpadUp)

	/**
	 * dpad down
	 */
	override var dpadDown = BoundBooleanSupplier(gamepad.dpadDown)

	/**
	 * dpad left
	 */
	override var dpadLeft = BoundBooleanSupplier(gamepad.dpadLeft)

	/**
	 * dpad right
	 */
	override var dpadRight = BoundBooleanSupplier(gamepad.dpadRight)

	/**
	 * button a
	 */
	override var a = BoundBooleanSupplier(gamepad.a)

	/**
	 * button b
	 */
	override var b = BoundBooleanSupplier(gamepad.b)

	/**
	 * button x
	 */
	override var x = BoundBooleanSupplier(gamepad.x)

	/**
	 * button y
	 */
	override var y = BoundBooleanSupplier(gamepad.y)

	/**
	 * button guide - often the large button in the middle of the controller. The OS may
	 * capture this button before it is sent to the app; in which case you'll never
	 * receive it.
	 */
	override var guide = BoundBooleanSupplier(gamepad.guide)

	/**
	 * button start
	 */
	override var start = BoundBooleanSupplier(gamepad.start)

	/**
	 * button back
	 */
	override var back = BoundBooleanSupplier(gamepad.back)

	/**
	 * button left bumper
	 */
	override var leftBumper = BoundBooleanSupplier(gamepad.leftBumper)

	/**
	 * button right bumper
	 */
	override var rightBumper = BoundBooleanSupplier(gamepad.rightBumper)

	/**
	 * left stick button
	 */
	override var leftStickButton = BoundBooleanSupplier(gamepad.leftStickButton)

	/**
	 * right stick button
	 */
	override var rightStickButton = BoundBooleanSupplier(gamepad.rightStickButton)

	/**
	 * left trigger
	 */
	override var leftTrigger = BoundDoubleSupplier(gamepad.leftTrigger)

	/**
	 * right trigger
	 */
	override var rightTrigger = BoundDoubleSupplier(gamepad.rightTrigger)

	/**
	 * PS4 Support - Circle
	 */
	override var circle
		get() = b
		set(value) {
			b = value
		}

	/**
	 * PS4 Support - cross
	 */
	override var cross
		get() = a
		set(value) {
			a = value
		}

	/**
	 * PS4 Support - triangle
	 */
	override var triangle
		get() = y
		set(value) {
			y = value
		}

	/**
	 * PS4 Support - square
	 */
	override var square
		get() = x
		set(value) {
			x = value
		}

	/**
	 * PS4 Support - share
	 */
	override var share
		get() = back
		set(value) {
			back = value
		}

	/**
	 * PS4 Support - options
	 */
	override var options
		get() = start
		set(value) {
			start = value
		}

	/**
	 * PS4 Support - touchpad
	 */
	override var touchpad = BoundBooleanSupplier(gamepad.touchpad)

	override var touchpadFinger1 = BoundBooleanSupplier(gamepad.touchpadFinger1)

	override var touchpadFinger2 = BoundBooleanSupplier(gamepad.touchpadFinger2)

	override var touchpadFinger1X = BoundDoubleSupplier(gamepad.touchpadFinger1X)

	override var touchpadFinger1Y = BoundDoubleSupplier(gamepad.touchpadFinger1Y)

	override var touchpadFinger2X = BoundDoubleSupplier(gamepad.touchpadFinger2X)

	override var touchpadFinger2Y = BoundDoubleSupplier(gamepad.touchpadFinger2Y)

	/**
	 * PS4 Support - PS Button
	 */
	override var ps
		get() = guide
		set(value) {
			guide = value
		}
}