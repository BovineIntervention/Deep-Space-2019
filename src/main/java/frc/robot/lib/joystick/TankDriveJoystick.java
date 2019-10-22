package frc.robot.lib.joystick;

import frc.robot.lib.joystick.JoystickControlsBase;
import frc.robot.Constants;
import frc.robot.command_status.DriveCommand;

/**
 * For use with Xbox steering wheel
 */
public class TankDriveJoystick extends JoystickControlsBase 
{
  	// singleton class
      private static JoystickControlsBase instance = null;
      public static JoystickControlsBase getInstance() 
      { 
              if (instance == null) {
                      instance = new TankDriveJoystick();
              }
              return instance;
      }
 
    
    public DriveCommand getDriveCommand()
    {
    	double lMotorSpeed = -mStick[0].getRawAxis(XboxConstants.kLStickYAxis);
        double rMotorSpeed = -mStick[0].getRawAxis(XboxConstants.kRStickYAxis);
	    
	    DriveCommand signal = new DriveCommand(lMotorSpeed, rMotorSpeed);
	   	    
	    return signal;        
    }
}
