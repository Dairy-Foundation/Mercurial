package dev.frozenmilk.mercurial.subsystems

import com.qualcomm.robotcore.hardware.HardwareMap
import dev.frozenmilk.dairy.core.FeatureRegistrar
import org.firstinspires.ftc.robotcore.external.Telemetry

@Suppress("unused")
abstract class SDKSubsystem : Subsystem {
	private val hardwareMapCell: SubsystemObjectCell<HardwareMap> by lazy {
		subsystemCell {
			FeatureRegistrar.activeOpMode.hardwareMap
		}
	}
	protected val hardwareMap by hardwareMapCell

	private val telemetryCell: SubsystemObjectCell<Telemetry> by lazy {
		subsystemCell {
			FeatureRegistrar.activeOpMode.telemetry
		}
	}
	protected val telemetry by telemetryCell
}