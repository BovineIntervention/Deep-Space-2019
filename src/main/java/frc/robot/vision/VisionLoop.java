package frc.robot.vision;

import java.util.ArrayList;

import edu.wpi.first.wpilibj.Timer;
import frc.robot.lib.joystick.SelectedJoystick;
import frc.robot.lib.sensors.Limelight;
import frc.robot.lib.sensors.Limelight.BoundingAngles;
import frc.robot.lib.sensors.Limelight.BoundingRectangle;
import frc.robot.lib.util.DataLogger;
import frc.robot.loops.Loop;

/**
 * VisionLoop contains the various attributes calculated by the vision system,
 * namely a list of targets and the timestamp at which it was captured.
 */
public class VisionLoop implements Loop {
	private static VisionLoop instance = new VisionLoop();

	public static VisionLoop getInstance() {
		return instance;
	}

	SelectedJoystick selectedJoystick = SelectedJoystick.getInstance();

	// camera selection
	public Limelight cargoCamera = Limelight.getCargoInstance();
	public Limelight hatchCamera = Limelight.getHatchInstance();
	Limelight cameraSelection = hatchCamera;

	public VisionTargetList visionTargetList = VisionTargetList.getInstance();

	BoundingRectangle boundingRectangle = cameraSelection.new BoundingRectangle();

	@Override
	public void onStart() {
		// nothing
	}

	@Override
	public void onLoop() {
		double currentTime = Timer.getFPGATimestamp();

		// get target info from Limelight
		getTargets(currentTime);
	}

	@Override
	public void onStop() {
		// nothing
	}

	public void getTargets(double currentTime) {
		cameraSelection = selectedJoystick.getDrivingCargo() ? cargoCamera : hatchCamera;

		double cameraLatency = cameraSelection.getTotalLatencyMs() / 1000.0;
		double imageCaptureTimestamp = currentTime - cameraLatency; // assumes transport time from phone to this code is
																	// instantaneous

		ArrayList<VisionTargetList.Target> targets = new ArrayList<>();	// initially empty

		if (cameraSelection.getIsTargetFound()) 
		{
			boundingRectangle = cameraSelection.getBoundingRectangle();

			if (boundingRectangle.xMin > 0 && boundingRectangle.xMax < (Limelight.kImageWidthPixels-1) && 
			    boundingRectangle.yMin > 0 && boundingRectangle.yMax < (Limelight.kImageHeightPixels-1))
			{
				// no corners at limits (indicating we are too close, and should just use a previous value)					
				BoundingAngles boundingAngles = cameraSelection.getBoundingAnglesRad(boundingRectangle);

				double hAngle = cameraSelection.getTargetHorizontalAngleRad();
				double vAngle = cameraSelection.getTargetVerticalAngleRad();
				double hWidth = boundingAngles.hWidthRad;
				double vWidth = boundingAngles.vWidthRad;

				VisionTargetList.Target target = new VisionTargetList.Target(hAngle, vAngle, hWidth, vWidth);
				targets.add(target);
			}
		}

		visionTargetList.set(imageCaptureTimestamp, targets);
	}


	private final DataLogger logger = new DataLogger()
    {
        @Override
        public void log()
        {
			put("VisionLoop/selectedCamera", cameraSelection.getTableName());
			put("VisionLoop/isTargetFound", cameraSelection.getIsTargetFound());
			put("VisionLoop/Corners.xMin", boundingRectangle.xMin);
			put("VisionLoop/Corners.xMax", boundingRectangle.xMax);
			put("VisionLoop/Corners.yMin", boundingRectangle.yMin);
			put("VisionLoop/Corners.yMax", boundingRectangle.yMax);
        }
    };
    
    public DataLogger getLogger() { return logger; }

	/**
	 * @return the selectedJoystick
	 */
	public SelectedJoystick getSelectedJoystick() {
		return selectedJoystick;
	}

	/**
	 * @param selectedJoystick the selectedJoystick to set
	 */
	public void setSelectedJoystick(SelectedJoystick selectedJoystick) {
		this.selectedJoystick = selectedJoystick;
	}

	/**
	 * @return the cargoCamera
	 */
	public Limelight getCargoCamera() {
		return cargoCamera;
	}

	/**
	 * @param cargoCamera the cargoCamera to set
	 */
	public void setCargoCamera(Limelight cargoCamera) {
		this.cargoCamera = cargoCamera;
	}

	/**
	 * @return the hatchCamera
	 */
	public Limelight getHatchCamera() {
		return hatchCamera;
	}

	/**
	 * @param hatchCamera the hatchCamera to set
	 */
	public void setHatchCamera(Limelight hatchCamera) {
		this.hatchCamera = hatchCamera;
	}

	/**
	 * @return the cameraSelection
	 */
	public Limelight getCameraSelection() {
		return cameraSelection;
	}

	/**
	 * @param cameraSelection the cameraSelection to set
	 */
	public void setCameraSelection(Limelight cameraSelection) {
		this.cameraSelection = cameraSelection;
	}

	/**
	 * @return the visionTargetList
	 */
	public VisionTargetList getVisionTargetList() {
		return visionTargetList;
	}

	/**
	 * @param visionTargetList the visionTargetList to set
	 */
	public void setVisionTargetList(VisionTargetList visionTargetList) {
		this.visionTargetList = visionTargetList;
	}

	/**
	 * @return the boundingRectangle
	 */
	public BoundingRectangle getBoundingRectangle() {
		return boundingRectangle;
	}

	/**
	 * @param boundingRectangle the boundingRectangle to set
	 */
	public void setBoundingRectangle(BoundingRectangle boundingRectangle) {
		this.boundingRectangle = boundingRectangle;
	}


}
