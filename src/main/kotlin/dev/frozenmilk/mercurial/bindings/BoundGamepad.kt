package dev.frozenmilk.mercurial.bindings

import dev.frozenmilk.dairy.core.util.supplier.logical.EnhancedBooleanSupplier
import dev.frozenmilk.dairy.core.util.supplier.numeric.EnhancedDoubleSupplier
import dev.frozenmilk.dairy.pasteurized.PasteurizedGamepad
import dev.frozenmilk.util.modifier.Modifier

@Suppress("INAPPLICABLE_JVM_NAME")
class BoundGamepad(gamepad: PasteurizedGamepad<EnhancedDoubleSupplier, EnhancedBooleanSupplier>) : PasteurizedGamepad<BoundDoubleSupplier, BoundBooleanSupplier> {
	override fun convert(n: BoundDoubleSupplier, modifier: Modifier<Double>) = BoundDoubleSupplier { modifier.modify(n.state) }
	/**
	 * left analog stick horizontal axis
	 */
	@get:JvmName("leftStickX")
	@set:JvmName("leftStickX")
	override var leftStickX = BoundDoubleSupplier(gamepad.leftStickX)

	/**
	 * left analog stick vertical axis
	 */
	@get:JvmName("leftStickY")
	@set:JvmName("leftStickY")
	override var leftStickY = BoundDoubleSupplier(gamepad.leftStickY)

	/**
	 * right analog stick horizontal axis
	 */
	@get:JvmName("rightStickX")
	@set:JvmName("rightStickX")
	override var rightStickX = BoundDoubleSupplier(gamepad.rightStickX)

	/**
	 * right analog stick vertical axis
	 */
	@get:JvmName("rightStickY")
	@set:JvmName("rightStickY")
	override var rightStickY = BoundDoubleSupplier(gamepad.rightStickY)

	/**
	 * left analog stick horizontal axis
	 */
	@get:JvmName("absoluteLeftStickX")
	@set:JvmName("absoluteLeftStickX")
	override var absoluteLeftStickX = BoundDoubleSupplier(Math.abs(gamepad.leftStickX))

	/**
	 * left analog stick vertical axis
	 */
	@get:JvmName("absoluteLeftStickY")
	@set:JvmName("absoluteLeftStickY")
	override var absoluteLeftStickY = BoundDoubleSupplier(Math.abs(gamepad.leftStickY))

	/**
	 * right analog stick horizontal axis
	 */
	@get:JvmName("absoluteRightStickX")
	@set:JvmName("absoluteRightStickX")
	override var absoluteRightStickX = BoundDoubleSupplier(Math.abs(gamepad.rightStickX))

	/**
	 * right analog stick vertical axis
	 */
	@get:JvmName("absoluteRightStickY")
	@set:JvmName("absoluteRightStickY")
	override var absoluteRightStickY = BoundDoubleSupplier(Math.abs(gamepad.rightStickY))

	/**
	 * dpad up
	 */
	@get:JvmName("dpadUp")
	@set:JvmName("dpadUp")
	override var dpadUp = BoundBooleanSupplier(gamepad.dpadUp)

	/**
	 * dpad down
	 */
	@get:JvmName("dpadDown")
	@set:JvmName("dpadDown")
	override var dpadDown = BoundBooleanSupplier(gamepad.dpadDown)

	/**
	 * dpad left
	 */
	@get:JvmName("dpadLeft")
	@set:JvmName("dpadLeft")
	override var dpadLeft = BoundBooleanSupplier(gamepad.dpadLeft)

	/**
	 * dpad right
	 */
	@get:JvmName("dpadRight")
	@set:JvmName("dpadRight")
	override var dpadRight = BoundBooleanSupplier(gamepad.dpadRight)

	/**
	 * button a
	 */
	@get:JvmName("a")
	@set:JvmName("a")
	override var a = BoundBooleanSupplier(gamepad.a)

	/**
	 * button b
	 */
	@get:JvmName("b")
	@set:JvmName("b")
	override var b = BoundBooleanSupplier(gamepad.b)

	/**
	 * button x
	 */
	@get:JvmName("x")
	@set:JvmName("x")
	override var x = BoundBooleanSupplier(gamepad.x)

	/**
	 * button y
	 */
	@get:JvmName("y")
	@set:JvmName("y")
	override var y = BoundBooleanSupplier(gamepad.y)

	/**
	 * button guide - often the large button in the middle of the controller. The OS may
	 * capture this button before it is sent to the app; in which case you'll never
	 * receive it.
	 */
	@get:JvmName("guide")
	@set:JvmName("guide")
	override var guide = BoundBooleanSupplier(gamepad.guide)

	/**
	 * button start
	 */
	@get:JvmName("start")
	@set:JvmName("start")
	override var start = BoundBooleanSupplier(gamepad.start)

	/**
	 * button back
	 */
	@get:JvmName("back")
	@set:JvmName("back")
	override var back = BoundBooleanSupplier(gamepad.back)

	/**
	 * button left bumper
	 */
	@get:JvmName("leftBumper")
	@set:JvmName("leftBumper")
	override var leftBumper = BoundBooleanSupplier(gamepad.leftBumper)

	/**
	 * button right bumper
	 */
	@get:JvmName("rightBumper")
	@set:JvmName("rightBumper")
	override var rightBumper = BoundBooleanSupplier(gamepad.rightBumper)

	/**
	 * left stick button
	 */
	@get:JvmName("leftStickButton")
	@set:JvmName("leftStickButton")
	override var leftStickButton = BoundBooleanSupplier(gamepad.leftStickButton)

	/**
	 * right stick button
	 */
	@get:JvmName("rightStickButton")
	@set:JvmName("rightStickButton")
	override var rightStickButton = BoundBooleanSupplier(gamepad.rightStickButton)

	/**
	 * left trigger
	 */
	@get:JvmName("leftTrigger")
	@set:JvmName("leftTrigger")
	override var leftTrigger = BoundDoubleSupplier(gamepad.leftTrigger)

	/**
	 * right trigger
	 */
	@get:JvmName("rightTrigger")
	@set:JvmName("rightTrigger")
	override var rightTrigger = BoundDoubleSupplier(gamepad.rightTrigger)

	/**
	 * PS4 Support - Circle
	 */
	@get:JvmName("circle")
	@set:JvmName("circle")
	override var circle
		get() = b
		set(value) {
			b = value
		}

	/**
	 * PS4 Support - cross
	 */
	@get:JvmName("cross")
	@set:JvmName("cross")
	override var cross
		get() = a
		set(value) {
			a = value
		}

	/**
	 * PS4 Support - triangle
	 */
	@get:JvmName("triangle")
	@set:JvmName("triangle")
	override var triangle
		get() = y
		set(value) {
			y = value
		}

	/**
	 * PS4 Support - square
	 */
	@get:JvmName("square")
	@set:JvmName("square")
	override var square
		get() = x
		set(value) {
			x = value
		}

	/**
	 * PS4 Support - share
	 */
	@get:JvmName("share")
	@set:JvmName("share")
	override var share
		get() = back
		set(value) {
			back = value
		}

	/**
	 * PS4 Support - options
	 */
	@get:JvmName("options")
	@set:JvmName("options")
	override var options
		get() = start
		set(value) {
			start = value
		}

	/**
	 * PS4 Support - touchpad
	 */
	@get:JvmName("touchpad")
	@set:JvmName("touchpad")
	override var touchpad = BoundBooleanSupplier(gamepad.touchpad)

	@get:JvmName("touchpadFinger1")
	@set:JvmName("touchpadFinger1")
	override var touchpadFinger1 = BoundBooleanSupplier(gamepad.touchpadFinger1)

	@get:JvmName("touchpadFinger2")
	@set:JvmName("touchpadFinger2")
	override var touchpadFinger2 = BoundBooleanSupplier(gamepad.touchpadFinger2)

	@get:JvmName("touchpadFinger1X")
	@set:JvmName("touchpadFinger1X")
	override var touchpadFinger1X = BoundDoubleSupplier(gamepad.touchpadFinger1X)

	@get:JvmName("touchpadFinger1Y")
	@set:JvmName("touchpadFinger1Y")
	override var touchpadFinger1Y = BoundDoubleSupplier(gamepad.touchpadFinger1Y)

	@get:JvmName("touchpadFinger2X")
	@set:JvmName("touchpadFinger2X")
	override var touchpadFinger2X = BoundDoubleSupplier(gamepad.touchpadFinger2X)

	@get:JvmName("touchpadFinger2Y")
	@set:JvmName("touchpadFinger2Y")
	override var touchpadFinger2Y = BoundDoubleSupplier(gamepad.touchpadFinger2Y)

	/**
	 * PS4 Support - PS Button
	 */
	@get:JvmName("ps")
	@set:JvmName("ps")
	override var ps
		get() = guide
		set(value) {
			guide = value
		}
}