// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot;

import edu.wpi.first.wpilibj.AnalogEncoder;
import edu.wpi.first.wpilibj.I2C;
import edu.wpi.first.wpilibj.Joystick;
import edu.wpi.first.wpilibj.SpeedControllerGroup;
import edu.wpi.first.wpilibj.motorcontrol.*;
import edu.wpi.first.wpilibj.I2C.Port;
import edu.wpi.first.wpilibj.command.WaitCommand;
import edu.wpi.first.wpilibj.command.WaitForChildren;

import com.revrobotics.CANPIDController;
import com.revrobotics.CANSparkMax;
import com.revrobotics.SparkMaxRelativeEncoder;
import com.revrobotics.CANAnalog.AnalogMode;
import com.revrobotics.CANSparkMax.ControlType;

import java.util.concurrent.TimeUnit;

import javax.lang.model.util.ElementScanner6;
import javax.swing.GroupLayout.Group;

import com.ctre.phoenix.motorcontrol.ControlMode;
import com.ctre.phoenix.motorcontrol.VictorSPXControlMode;
import com.ctre.phoenix.motorcontrol.can.VictorSPX;
import com.kauailabs.navx.frc.AHRS;
import com.revrobotics.SparkMaxRelativeEncoder.Type;
import com.revrobotics.RelativeEncoder;
import com.revrobotics.SparkMaxAnalogSensor;
import com.revrobotics.SparkMaxPIDController;
import com.revrobotics.CANSparkMaxLowLevel.MotorType;
import org.ejml.equation.Variable;
import edu.wpi.first.wpilibj.TimedRobot;
import edu.wpi.first.wpilibj.Timer;
import edu.wpi.first.wpilibj.drive.DifferentialDrive;
import edu.wpi.first.wpilibj.interfaces.Gyro;
import edu.wpi.first.cameraserver.CameraServer;
import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.math.kinematics.SwerveDriveKinematics;
import edu.wpi.first.math.kinematics.SwerveDriveOdometry;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.networktables.NetworkTable;
import edu.wpi.first.networktables.NetworkTableEntry;
import edu.wpi.first.networktables.NetworkTableInstance;
import edu.wpi.first.wpilibj.Compressor;
import edu.wpi.first.wpilibj.DoubleSolenoid;
import edu.wpi.first.wpilibj.PneumaticsModuleType;
import edu.wpi.first.wpilibj.DoubleSolenoid.Value;

/**
 * This is a demo program showing the use of the RobotDrive class, specifically
 * it contains the code
 * necessary to operate a robot with tank drive.
 */
public class Robot extends TimedRobot {
  private Joystick LeftStick;
  private Joystick RightStick;
  private AHRS ahrs;
  private double RightStickX;
  private double RightStickY;
  private double RightStickZ;
  private double RightStickTwist;
  private double LeftStickY;
  private double LeftStickZ;
  private boolean HasBeenRun;
  private CANSparkMax ShooterTop;
  private CANSparkMax ShooterBottom;
  private CANSparkMax ArmExtend;
  private double P;
  private double I;
  private double D;
  private Translation2d FrontRightLocation = new Translation2d(0.381, -0.381);
  private Translation2d FrontLeftLocation = new Translation2d(0.381, 0.381);
  private Translation2d BackLeftLocation = new Translation2d(-0.381, -0.381);
  private Translation2d BackRightLocation = new Translation2d(-0.381, 0.381);
  private Timer timer;
  private final Compressor Compressor = new Compressor(0, PneumaticsModuleType.CTREPCM);
  private final DoubleSolenoid Solenoid = new DoubleSolenoid(PneumaticsModuleType.CTREPCM, 0, 1);

  SwerveDriveKinematics Kinematics = new SwerveDriveKinematics(FrontRightLocation, FrontLeftLocation, BackLeftLocation, BackRightLocation);
  SwerveDriveOdometry Odometry;

  private class Wheel {
    private CANSparkMax Drive;
    private RelativeEncoder DriveEncoder;
    private SparkMaxPIDController DrivePIDController;
    private CANSparkMax Steer;
    private RelativeEncoder SteerEncoder;
    private SparkMaxPIDController SteerPIDController;
    private double DistToPos;
    private double DistSpdMod;

    private Wheel(CANSparkMax Drive, RelativeEncoder DriveEncoder, SparkMaxPIDController DrivePIDController, CANSparkMax Steer, RelativeEncoder SteerEncoder, SparkMaxPIDController PIDController, double DistToPos, double DistSpdMod) {
      this.Drive = Drive;
      this.DriveEncoder = DriveEncoder;
      this.DrivePIDController = DrivePIDController;
      this.Steer = Steer;
      this.SteerEncoder = SteerEncoder;
      this.SteerPIDController = SteerPIDController;
      this.DistToPos = DistToPos;
      this.DistSpdMod = DistSpdMod;
    }
  }

  private Wheel FrontRight = new Wheel(null, null, null, null, null, null, D, D);
  private Wheel FrontLeft = new Wheel(null, null, null, null, null, null, D, D);
  private Wheel BackLeft = new Wheel(null, null, null, null, null, null, D, D);
  private Wheel BackRight = new Wheel(null, null, null, null, null, null, D, D);

  @Override
  public void robotInit() {
    CameraServer.startAutomaticCapture();
    timer = new Timer();
    LeftStick = new Joystick(1);
    RightStick = new Joystick(2);
    ahrs = new AHRS(I2C.Port.kMXP);
    Odometry = new SwerveDriveOdometry(Kinematics, ahrs.getRotation2d(), new Pose2d(5.0, 13.5, new Rotation2d()));

    FrontRight.Drive = new CANSparkMax(1, MotorType.kBrushless);
    FrontLeft.Drive = new CANSparkMax(2, MotorType.kBrushless);
    BackLeft.Drive = new CANSparkMax(3, MotorType.kBrushless);
    BackRight.Drive = new CANSparkMax(4, MotorType.kBrushless);
    FrontRight.Steer = new CANSparkMax(5, MotorType.kBrushed);
    FrontLeft.Steer = new CANSparkMax(6, MotorType.kBrushed);
    BackLeft.Steer = new CANSparkMax(7, MotorType.kBrushed);
    BackRight.Steer = new CANSparkMax(8, MotorType.kBrushed);
    ShooterBottom = new CANSparkMax(9, MotorType.kBrushless);
    ShooterTop = new CANSparkMax(10, MotorType.kBrushless);
    ArmExtend = new CANSparkMax(11, MotorType.kBrushed);

    
    FrontRight.SteerEncoder = FrontRight.Steer.getEncoder(SparkMaxRelativeEncoder.Type.kQuadrature, (28));
    FrontLeft.SteerEncoder = FrontLeft.Steer.getEncoder(SparkMaxRelativeEncoder.Type.kQuadrature, (28));
    BackLeft.SteerEncoder = BackLeft.Steer.getEncoder(SparkMaxRelativeEncoder.Type.kQuadrature, (28));
    BackRight.SteerEncoder = BackRight.Steer.getEncoder(SparkMaxRelativeEncoder.Type.kQuadrature, (28));

    /**
    FrontRight.SteerEncoder = FrontRight.Steer.getAnalog(SparkMaxAnalogSensor.AnalogMode.kAbsolute);
    FrontLeft.SteerEncoder = FrontLeft.Steer.getAnalog(SparkMaxAnalogSensor.AnalogMode.kAbsolute);
    BackLeft.SteerEncoder = BackLeft.Steer.getAnalog(SparkMaxAnalogSensor.AnalogMode.kAbsolute);
    BackRight.SteerEncoder = BackRight.Steer.getAnalog(SparkMaxAnalogSensor.AnalogMode.kAbsolute);
    */
    FrontRight.DriveEncoder = FrontRight.Drive.getEncoder(SparkMaxRelativeEncoder.Type.kHallSensor, (42));
    FrontLeft.DriveEncoder = FrontLeft.Drive.getEncoder(SparkMaxRelativeEncoder.Type.kHallSensor, (42));
    BackRight.DriveEncoder = BackRight.Drive.getEncoder(SparkMaxRelativeEncoder.Type.kHallSensor, (42));
    BackLeft.DriveEncoder = BackLeft.Drive.getEncoder(SparkMaxRelativeEncoder.Type.kHallSensor, (42));

    FrontRight.SteerEncoder.setPosition(0);
    FrontLeft.SteerEncoder.setPosition(0);
    BackLeft.SteerEncoder.setPosition(0);
    BackRight.SteerEncoder.setPosition(0);
    
    /**
    FrontRight.SteerEncoder.setPositionConversionFactor(1);
    FrontLeft.SteerEncoder.setPositionConversionFactor(1);
    BackLeft.SteerEncoder.setPositionConversionFactor(1);
    BackRight.SteerEncoder.setPositionConversionFactor(1);
    */

    FrontRight.DriveEncoder.setPosition(0);
    FrontLeft.DriveEncoder.setPosition(0);
    BackRight.DriveEncoder.setPosition(0);
    BackLeft.DriveEncoder.setPosition(0);

    ahrs.calibrate();
    ahrs.reset();

    FrontRight.SteerPIDController = FrontRight.Steer.getPIDController();
    FrontLeft.SteerPIDController = FrontLeft.Steer.getPIDController();
    BackLeft.SteerPIDController = BackLeft.Steer.getPIDController();
    BackRight.SteerPIDController = BackRight.Steer.getPIDController();

    FrontRight.DrivePIDController = FrontRight.Drive.getPIDController();
    FrontLeft.DrivePIDController = FrontLeft.Drive.getPIDController();
    BackRight.DrivePIDController = BackLeft.Drive.getPIDController();
    BackLeft.DrivePIDController = BackRight.Drive.getPIDController();
  
    P = 1;
    I = 0;
    D = 0;

    FrontRight.SteerPIDController.setOutputRange(-1, 1);
    FrontLeft.SteerPIDController.setOutputRange(-1, 1);
    BackLeft.SteerPIDController.setOutputRange(-1, 1);
    BackRight.SteerPIDController.setOutputRange(-1, 1);

    FrontRight.DrivePIDController.setOutputRange(-1, 1);
    FrontLeft.DrivePIDController.setOutputRange(-1, 1);
    BackLeft.DrivePIDController.setOutputRange(-1, 1);
    BackRight.DrivePIDController.setOutputRange(-1, 1);

    FrontRight.SteerPIDController.setP(P);
    FrontLeft.SteerPIDController.setP(P);
    BackLeft.SteerPIDController.setP(P);
    BackRight.SteerPIDController.setP(P);

    FrontRight.DrivePIDController.setP(P);
    FrontLeft.DrivePIDController.setP(P);
    BackLeft.DrivePIDController.setP(P);
    BackRight.DrivePIDController.setP(P);

    FrontRight.SteerPIDController.setI(I);
    FrontLeft.SteerPIDController.setI(I);
    BackLeft.SteerPIDController.setI(I);
    BackRight.SteerPIDController.setI(I);

    FrontRight.DrivePIDController.setI(I);
    FrontLeft.DrivePIDController.setI(I);
    BackLeft.DrivePIDController.setI(I);
    BackRight.DrivePIDController.setI(I);

    FrontRight.SteerPIDController.setD(D);
    FrontLeft.SteerPIDController.setD(D);
    BackLeft.SteerPIDController.setD(D);
    BackRight.SteerPIDController.setD(D);

    FrontRight.DrivePIDController.setD(D);
    FrontLeft.DrivePIDController.setD(D);
    BackLeft.DrivePIDController.setD(D);
    BackRight.DrivePIDController.setD(D);

    Solenoid.set(Value.kOff);
    Compressor.enableDigital();
  }

  @Override
  public void teleopPeriodic() {

    //Find right joystick positions once, to prevent discrepancies
    RightStickX = RightStick.getX();
    RightStickY = RightStick.getY();
    RightStickZ = (1 - ((RightStick.getZ() + 1) / 2));
    RightStickTwist = RightStick.getRawAxis(3);
    LeftStickY = LeftStick.getY();
    LeftStickZ = (1 - ((LeftStick.getZ() + 1) / 2));

    if (Math.abs(RightStickX) < 0.1) {
      RightStickX = 0;
    }
    if (Math.abs(RightStickY) < 0.1) {
      RightStickY = 0;
    }
    if (Math.abs(RightStickTwist) < 0.15) {
      RightStickTwist = 0;
    }
    if (Math.abs(LeftStickY) < 0.1) {
      LeftStickY = 0;
    }
    
    //Set ChassisSpeeds for actual movement
    ChassisSpeeds speeds = new ChassisSpeeds(((RightStickY * -1) * RightStickZ), (RightStickX * RightStickZ), (RightStickTwist * LeftStickZ));

    //Convert to module states
    SwerveModuleState[] ModuleStates = Kinematics.toSwerveModuleStates(speeds);

    //Front left module state
    SwerveModuleState frontLeft = ModuleStates[0];

    //Front right module state
    SwerveModuleState frontRight = ModuleStates[1];

    //Back left module state
    SwerveModuleState backLeft = ModuleStates[2];

    //Back right module state
    SwerveModuleState backRight = ModuleStates[3];

    //Update Odometry
    Odometry.update(ahrs.getRotation2d(), new SwerveModuleState(FrontLeft.DriveEncoder.getVelocity(), new Rotation2d(FrontLeft.SteerEncoder.getPosition() / (59.0 + (1.0/6.0)))), new SwerveModuleState(FrontRight.DriveEncoder.getVelocity(), new Rotation2d(FrontRight.SteerEncoder.getPosition() / (59.0 + (1.0/6.0)))),
    new SwerveModuleState(BackLeft.DriveEncoder.getVelocity(), new Rotation2d(BackLeft.SteerEncoder.getPosition() / (59.0 + (1.0/6.0)))), new SwerveModuleState(BackRight.DriveEncoder.getVelocity(), new Rotation2d(BackRight.SteerEncoder.getPosition() / (59.0 + (1.0/6.0)))));

    //ModuleStates = Kinematics.toSwerveModuleStates(ChassisSpeeds.fromFieldRelativeSpeeds(((RightStickY * -1) * RightStickZ), (RightStickX * RightStickZ), (RightStickTwist * LeftStickZ), ahrs.getRotation2d()));
    
    //Optimize rotation positions
    var frontLeftOptimized = SwerveModuleState.optimize(frontLeft,
    new Rotation2d((FrontLeft.SteerEncoder.getPosition() / (59.0 + (1.0/6.0)))));
    var frontRightOptimized = SwerveModuleState.optimize(frontRight,
    new Rotation2d((FrontRight.SteerEncoder.getPosition() / (59.0 + (1.0/6.0)))));
    var backLeftOptimized = SwerveModuleState.optimize(backLeft,
    new Rotation2d((BackLeft.SteerEncoder.getPosition() / (59.0 + (1.0/6.0)))));
    var backRightOptimized = SwerveModuleState.optimize(backRight,
    new Rotation2d((BackRight.SteerEncoder.getPosition() / (59.0 + (1.0/6.0)))));

    FrontRight.DistToPos = ((Math.abs((FrontRight.SteerEncoder.getPosition() / (59.0 + (1.0/6.0))) - ((frontRightOptimized.angle.getDegrees() / 360.0)))));
    FrontLeft.DistToPos = ((Math.abs((FrontLeft.SteerEncoder.getPosition() / (59.0 + (1.0/6.0))) - ((frontLeftOptimized.angle.getDegrees() / 360.0)))));
    BackLeft.DistToPos = ((Math.abs((BackLeft.SteerEncoder.getPosition() / (59.0 + (1.0/6.0))) - ((backLeftOptimized.angle.getDegrees() / 360.0)))));
    BackRight.DistToPos = ((Math.abs((BackRight.SteerEncoder.getPosition() / (59.0 + (1.0/6.0))) - ((backRightOptimized.angle.getDegrees() / 360.0)))));

    FrontRight.DistToPos = (1 - (2 * FrontRight.DistToPos));
    FrontLeft.DistToPos = (1 - (2 * FrontLeft.DistToPos));
    BackLeft.DistToPos = (1 - (2 * BackLeft.DistToPos));
    BackRight.DistToPos = (1 - (2 * BackRight.DistToPos));

    FrontRight.DistSpdMod = Math.pow(FrontRight.DistToPos, 5);
    FrontLeft.DistSpdMod = Math.pow(FrontLeft.DistToPos, 5);
    BackLeft.DistSpdMod = Math.pow(BackLeft.DistToPos, 5);
    BackRight.DistSpdMod = Math.pow(BackRight.DistToPos, 5);

    FrontRight.SteerPIDController.setReference(((frontRightOptimized.angle.getDegrees() / 360.0) * (59.0 + (1.0/6.0))), ControlType.kPosition);
    FrontLeft.SteerPIDController.setReference(((frontLeftOptimized.angle.getDegrees() / 360.0) * (59.0 + (1.0/6.0))), ControlType.kPosition);
    BackLeft.SteerPIDController.setReference(((backLeftOptimized.angle.getDegrees() / 360.0) * (59.0 + (1.0/6.0))), ControlType.kPosition);
    BackRight.SteerPIDController.setReference(((backRightOptimized.angle.getDegrees() / 360.0) * (59.0 + (1.0/6.0))), ControlType.kPosition);

    FrontRight.Drive.set((frontRightOptimized.speedMetersPerSecond / 2) * FrontRight.DistSpdMod);
    FrontLeft.Drive.set((frontLeftOptimized.speedMetersPerSecond / 2) * FrontLeft.DistSpdMod);
    BackLeft.Drive.set((backLeftOptimized.speedMetersPerSecond / 2) * BackLeft.DistSpdMod);
    BackRight.Drive.set((backRightOptimized.speedMetersPerSecond / 2) * BackRight.DistSpdMod);

    if (LeftStick.getRawButton(1)){
      ShooterTop.set(-.6);
      ShooterBottom.set(-.5);
    }
    else{
      if(LeftStick.getRawButton(4)){
        ShooterTop.set(-.6);
      } 
      else if(LeftStick.getRawButton(6)){
        ShooterTop.set(.6);
      }
      else{
        ShooterTop.set(0);
      }
      if(RightStick.getRawButton(3)){
        ShooterBottom.set(-.5);
      }
      else if(RightStick.getRawButton(5)){
        ShooterBottom.set(.5);
      }
      else{
        ShooterBottom.set(0);
      }
    }

    if(LeftStick.getRawButton(5) && !LeftStick.getRawButton(3)) {
      Solenoid.set(Value.kForward);
    } else if(LeftStick.getRawButton(3) && !LeftStick.getRawButton(5)) {
      Solenoid.set(Value.kReverse);
    } else {
      Solenoid.set(Value.kOff);
    }

    if(LeftStick.getRawButtonPressed(2))
      Compressor.disable();
    if(RightStick.getRawButtonPressed(2))
      Compressor.enableDigital();

    if(RightStick.getRawButton(4)){
      Solenoid.set(DoubleSolenoid.Value.kReverse);
    }
    else if(RightStick.getRawButton(6)){
      Solenoid.set(DoubleSolenoid.Value.kForward);
    }
  }

  //Autonomous right away
  @Override
  public void autonomousInit(){
    HasBeenRun = false;
  }

  //Autonomous repeat
  @Override
  public void autonomousPeriodic(){ 
    FrontRight.SteerPIDController.setReference(0, ControlType.kPosition);
    FrontLeft.SteerPIDController.setReference(0, ControlType.kPosition);
    BackLeft.SteerPIDController.setReference(0, ControlType.kPosition);
    BackRight.SteerPIDController.setReference(0, ControlType.kPosition);
    
    if (HasBeenRun != true){
      timer.reset();
      timer.start();
      HasBeenRun = true;
    }
    while (timer.get() < .5){
      FrontRight.Drive.set(0);
      FrontLeft.Drive.set(0);
      BackLeft.Drive.set(0);
      BackRight.Drive.set(0);
      ShooterTop.set(-.6);
      ShooterBottom.set(-.5);
    }
    while (.5 < timer.get() & timer.get() < 3){
      FrontRight.Drive.set(.25);
      FrontLeft.Drive.set(.25);
      BackLeft.Drive.set(.25);
      BackRight.Drive.set(.25);
      ShooterTop.set(0);
      ShooterBottom.set(-.5);
    }
    while (3 < timer.get() & timer.get() < 4){
      FrontRight.Drive.set(0);
      FrontLeft.Drive.set(0);
      BackLeft.Drive.set(0);
      BackRight.Drive.set(0);
      ShooterBottom.set(-.5);
    }
    while (4 < timer.get() & timer.get() < 6.5){
      FrontRight.Drive.set(-.25);
      FrontLeft.Drive.set(-.25);
      BackLeft.Drive.set(-.25);
      BackRight.Drive.set(-.25);
      ShooterBottom.set(0);
    }
    while (6.5 < timer.get() & timer.get() < 10){
      FrontRight.Drive.set(0);
      FrontLeft.Drive.set(0);
      BackLeft.Drive.set(0);
      BackRight.Drive.set(0);
      ShooterTop.set(-.6);
      ShooterBottom.set(-.5);
    }
    while (10 < timer.get() & timer.get() < 11){
      FrontRight.Drive.set(0);
      FrontLeft.Drive.set(0);
      BackLeft.Drive.set(0);
      BackRight.Drive.set(0);
      ShooterTop.set(0);
      ShooterBottom.set(0);
    }
  }
}