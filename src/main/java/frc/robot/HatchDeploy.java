package frc.robot;

import com.ctre.phoenix.ParamEnum;
import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.Faults;
import com.ctre.phoenix.motorcontrol.FeedbackDevice;
import com.ctre.phoenix.motorcontrol.NeutralMode;
import com.ctre.phoenix.motorcontrol.StatusFrameEnhanced;
import com.ctre.phoenix.motorcontrol.can.TalonSRX;

import edu.wpi.first.wpilibj.Solenoid;
import edu.wpi.first.wpilibj.Timer;
import frc.robot.lib.joystick.ArcadeDriveJoystick;
import frc.robot.lib.joystick.ButtonBoard;
import frc.robot.lib.joystick.JoystickControlsBase;
import frc.robot.lib.joystick.SelectedJoystick;
import frc.robot.lib.util.DataLogger;
import frc.robot.lib.util.FallingEdgeDetector;
import frc.robot.lib.util.RisingEdgeDetector;
import frc.robot.loops.Loop;

public class HatchDeploy implements Loop
{
    public static HatchDeploy mInstance = new HatchDeploy();

    public static HatchDeploy getInstance() {
        return mInstance;
    }

    public TalonSRX dropMotor;
    public Solenoid hatchSolenoid;
    public final double zeroingSpeed = -0.15;
    public static final int bumperAngle = 275;
    public static final int collisionAngle = 400;
    public static final int HumanStationAngle = 400;
    public static final int groundAngle = 1249;
    public static final int defenseAngle = 0;
    public RisingEdgeDetector hatchButtonRisingEdgeDetector = new RisingEdgeDetector();
    public RisingEdgeDetector humanButtonRisingEdgeDetector = new RisingEdgeDetector();
    public FallingEdgeDetector hatchButtonFallingEdgeDetector = new FallingEdgeDetector();
    public RisingEdgeDetector ejectButtonRisingEdgeDetector = new RisingEdgeDetector();
    public FallingEdgeDetector ejectButtonFallingEdgeDetector = new FallingEdgeDetector();
    public boolean zeroed  = false; 
    private double mTimeToWait = 1;
    private double hTimeToWait = 0.5;
    private double mStartTime;
    public double targetPosition;
    public ButtonBoard buttonBoard = ButtonBoard.getInstance();

    // math for limit switch
    public static double kEncoderUnitsPerRev = 4096;
    public static double kEncoderDegPerRev = 360;
    public static double kEncoderUnitsPerDeg = kEncoderUnitsPerRev/kEncoderDegPerRev;


    //====================================================
    // Constants
    //====================================================
    
    public final double zeroingPercentOutput = -0.1;

    public final double kMinFwdOutput = +0;
    public final double kMinRevOutput = -0;
    public final double kMaxFwdOutput = +0.5;   // start with low voltage!!!  TODO: increase to 0.5 max (6V)
    public final double kMaxRevOutput = -0.5;

    public final int kSlotIdx = 0;

    public final double kCalMaxEncoderPulsePer100ms = 500;	// velocity at a max throttle (measured using Phoenix Tuner)
    public final double kCalMaxPercentOutput 		= 0.5;	// percent output of motor at above throttle (using Phoenix Tuner)

    public final double kCruiseVelocity = 0.50 * kCalMaxEncoderPulsePer100ms;		// cruise below top speed
    public final double kTimeToCruiseVelocity = 0.25;				// seconds to reach cruise velocity
    public final double kAccel = kCruiseVelocity / kTimeToCruiseVelocity; 
    
	public final double kKf = kCalMaxPercentOutput * 1023.0 / kCalMaxEncoderPulsePer100ms;
	public final double kKp = 1.0;	   
	public final double kKd = 0.0;	// to resolve any overshoot, start at 10*Kp 
	public final double kKi = 0.0;    

    public final int    kAllowableError = (int)(0.25 * kEncoderUnitsPerDeg);
    

    public final int kPeakCurrentLimit = 30;
    public final int kPeakCurrentDuration = 200;
    public final int kContinuousCurrentLimit = 20;
    
    
    public enum HatchDeployStateEnum {
        INIT, DEFENSE, AUTO_COLLISION, TO_BUMPER, HUMAN_STATION, GROUND;
    }

    public HatchDeployStateEnum state = HatchDeployStateEnum.INIT;

    public HatchDeploy() {
        
        hatchSolenoid = new Solenoid(0, Constants.kHatchEjectChannel);
        dropMotor = new TalonSRX(Constants.kHatchDeployTalonId);
        state = HatchDeployStateEnum.INIT;

             // Factory default hardware to prevent unexpected behavior
       dropMotor.configFactoryDefault();

       // configure encoder
       dropMotor.configSelectedFeedbackSensor(FeedbackDevice.CTRE_MagEncoder_Relative, Constants.kTalonPidIdx, Constants.kTalonTimeoutMs);
       dropMotor.setInverted(true);   // set to have green LEDs when driving forward
       dropMotor.setSensorPhase(false); // set so that positive motor input results in positive change in sensor value
       
       // set relevant frame periods to be at least as fast as periodic rate
       dropMotor.setStatusFramePeriod(StatusFrameEnhanced.Status_1_General,      (int)(1000 * Constants.kLoopDt), Constants.kTalonTimeoutMs);
       dropMotor.setStatusFramePeriod(StatusFrameEnhanced.Status_2_Feedback0,    (int)(1000 * Constants.kLoopDt), Constants.kTalonTimeoutMs);
       dropMotor.setStatusFramePeriod(StatusFrameEnhanced.Status_10_MotionMagic, (int)(1000 * Constants.kLoopDt), Constants.kTalonTimeoutMs);
       dropMotor.setStatusFramePeriod(StatusFrameEnhanced.Status_13_Base_PIDF0,  (int)(1000 * Constants.kLoopDt), Constants.kTalonTimeoutMs);
           
           // set min and max outputs
           dropMotor.configNominalOutputForward(kMinFwdOutput, Constants.kTalonTimeoutMs);
           dropMotor.configNominalOutputReverse(kMinRevOutput, Constants.kTalonTimeoutMs);
           dropMotor.configPeakOutputForward(kMaxFwdOutput, Constants.kTalonTimeoutMs);
           dropMotor.configPeakOutputReverse(kMaxRevOutput, Constants.kTalonTimeoutMs);
       
       // configure position loop PID 
           dropMotor.selectProfileSlot(kSlotIdx, Constants.kTalonPidIdx); 
           dropMotor.config_kF(kSlotIdx, kKf, Constants.kTalonTimeoutMs); 
           dropMotor.config_kP(kSlotIdx, kKp, Constants.kTalonTimeoutMs); 
           dropMotor.config_kI(kSlotIdx, kKi, Constants.kTalonTimeoutMs); 
           dropMotor.config_kD(kSlotIdx, kKd, Constants.kTalonTimeoutMs);
           dropMotor.configAllowableClosedloopError(kSlotIdx, kAllowableError, Constants.kTalonTimeoutMs);
           
       // set acceleration and cruise velocity
       dropMotor.configMotionCruiseVelocity((int)kCruiseVelocity, Constants.kTalonTimeoutMs);
       dropMotor.configMotionAcceleration((int)kAccel, Constants.kTalonTimeoutMs);	
           
           // configure talon to automatically reset its position when the reverse limit switch is hit
           dropMotor.configSetParameter(ParamEnum.eClearPositionOnLimitR, 1, 0x00, 0x00, Constants.kTalonTimeoutMs);
          
           // current limits
           dropMotor.configPeakCurrentLimit(kPeakCurrentLimit, Constants.kTalonTimeoutMs);
           dropMotor.configPeakCurrentDuration(kPeakCurrentDuration, Constants.kTalonTimeoutMs);
           dropMotor.configContinuousCurrentLimit(kContinuousCurrentLimit, Constants.kTalonTimeoutMs);
           dropMotor.enableCurrentLimit(true);
           
        
           // begin with motors stopped
           dropMotor.set(ControlMode.PercentOutput, 0.0);
           dropMotor.setNeutralMode(NeutralMode.Brake);

           zeroed = false;

    }

	@Override
	public void onStart() 
	{
        // if we haven't calibrated yet, do so now
        if (!zeroed)
        {
            state = HatchDeployStateEnum.INIT;
        }
    }

    
	@Override
	public void onStop() 
	{
        // stop all motors
        dropMotor.set(ControlMode.PercentOutput, 0.0);
    }

    boolean drivingHatch;
    @Override
    public void onLoop() {
        drivingHatch = !SelectedJoystick.getInstance().getDrivingCargo();

        JoystickControlsBase controls = ArcadeDriveJoystick.getInstance();
        boolean dBtnIsPushed = buttonBoard.getButton(Constants.kDefenseButton);
        
        boolean hBtnIsPushed = controls.getButton(Constants.kHatchDeployButton) && drivingHatch;
        boolean hButtonPush = hatchButtonRisingEdgeDetector.update(hBtnIsPushed);

        boolean ejectButton = controls.getAxisAsButton(Constants.kHatchShootAxis) && drivingHatch;
        boolean ejectButtonPush = ejectButtonRisingEdgeDetector.update(ejectButton);
        boolean ejectButtonUnpush = ejectButtonFallingEdgeDetector.update(ejectButton);

        boolean humanBtnIsPushed = controls.getButton(Constants.kHumanStationBttn) && drivingHatch;
        boolean humanButtonPush = humanButtonRisingEdgeDetector.update(humanBtnIsPushed);

        getLimitSwitches();


        
        switch (state) {
        case INIT:
            dropMotor.set(ControlMode.PercentOutput, zeroingSpeed);
            if (zeroed || getReverseLimitSwitch()) {
                zeroed = true;
                state = HatchDeployStateEnum.TO_BUMPER;
            }
            break;


        case TO_BUMPER:
            setTarget(bumperAngle);
            if (hButtonPush)  
            {    
                mStartTime = Timer.getFPGATimestamp();
                 
                state = HatchDeployStateEnum.GROUND;
            }
            if (dBtnIsPushed)
            {
                state = HatchDeployStateEnum.DEFENSE;
            }
            if (humanButtonPush)  
            {    
                mStartTime = Timer.getFPGATimestamp();
                 
                state = HatchDeployStateEnum.HUMAN_STATION;
            }
            break;

        case AUTO_COLLISION:
            setTarget(collisionAngle);

            // allow us to get out of this mode with button presses
            if (hButtonPush)  
            {    
                mStartTime = Timer.getFPGATimestamp();
                 
                state = HatchDeployStateEnum.GROUND;
            }
            if (dBtnIsPushed)
            {
                state = HatchDeployStateEnum.DEFENSE;
            }
            break;

        case DEFENSE:
           setTarget(defenseAngle);
            if (hButtonPush)
            {
                state = HatchDeployStateEnum.TO_BUMPER;
            }
            break;

            
        case GROUND:
            setTarget(groundAngle);
            if ( Timer.getFPGATimestamp() - mStartTime >= mTimeToWait)    
            {
                state = HatchDeployStateEnum.TO_BUMPER;
            }
            break;

            case HUMAN_STATION:
            setTarget(HumanStationAngle);
            if ( Timer.getFPGATimestamp() - mStartTime >= hTimeToWait)    
            {
                state = HatchDeployStateEnum.TO_BUMPER;
            }
            break;


        }

        //shoots both pistons from the solenoid
        if (ejectButtonPush) {
            hatchSolenoid.set(true); }
        else if (ejectButtonUnpush) {
            hatchSolenoid.set(false); }
            

    }

    public static double degsToEncoderUnits(int _encoderUnits)
	{
		return _encoderUnits / kEncoderUnitsPerDeg;
	}
	
	public static int degsToEncoderUnits(double _inches)
	{
		return (int)(_inches * kEncoderUnitsPerDeg);
	}
	
	public double encoderVelocityToDegsPerSec(int _encoderVelocity)
	{
		// extra factor of 10 because velocity is reported over 100ms periods 
		return _encoderVelocity * 10.0 / kEncoderUnitsPerDeg;
	}
    
    public void setState(HatchDeployStateEnum _newState)
    {
        state = _newState;
    }

    public double getHatchAngle()
    {
        return dropMotor.getSelectedSensorPosition(Constants.kTalonPidIdx);
    }

public void drop() {
    dropMotor.set(ControlMode.MotionMagic, degsToEncoderUnits(groundAngle));
}
public void eject(){
    hatchSolenoid.set(true);
}
public void retract() {
    hatchSolenoid.set(false);
}

public void setTarget(double _targetPosition){
    dropMotor.set(ControlMode.MotionMagic, (_targetPosition));
    targetPosition = _targetPosition;
}
Faults faults = new Faults();
public void getLimitSwitches()
{
    // called once per loop iteration
    dropMotor.getFaults(faults);
}

public boolean getForwardLimitSwitch()
{
    return faults.ForwardLimitSwitch;
}

public boolean getReverseLimitSwitch()
{
    return faults.ReverseLimitSwitch;
}    

public boolean getForwardSoftLimit()
{
    return faults.ForwardSoftLimit;  
}    

public boolean getReverseSoftLimit()
{
    return faults.ReverseSoftLimit;  
}    

private final DataLogger logger = new DataLogger()
	{
		@Override
		public void log()
		{
            put("HatchDeploy/targetPosition", targetPosition);
            put("HatchDeploy/state", state.toString());
            put("HatchDeploy/zeroed", zeroed);
            put("HatchDeploy/fwdLimitSwitch", getForwardLimitSwitch());
            put("HatchDeploy/revLimitSwitch", getReverseLimitSwitch());
            put("HatchDeploy/fwdSoftLimit", getForwardSoftLimit());
            put("HatchDeploy/revSoftLimit", getReverseSoftLimit());
            put("HatchDeploy/motorCurrent", dropMotor.getOutputCurrent());
            put("HatchDeploy/pidError", dropMotor.getClosedLoopError(kSlotIdx));
            put("HatchDeploy/hatchAngle", getHatchAngle());
            put("HatchDeploy/drivingHatch", drivingHatch);
		}
	};
    
	public DataLogger getLogger()
	{
		return logger;
	}    

}
