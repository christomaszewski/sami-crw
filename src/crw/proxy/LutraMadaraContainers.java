package crw.proxy;

import com.gams.variables.Self;
import com.madara.EvalSettings;

import com.madara.KnowledgeBase;
import com.madara.UpdateSettings;
import com.madara.containers.Double;
import com.madara.containers.DoubleVector;
import com.madara.containers.Integer;
import com.madara.containers.NativeDoubleVector;
import com.madara.containers.String;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


/**
 * @author jjb
 */
public class LutraMadaraContainers {

    // Pass in knowledge base
    // Takes care of all the SetName's for all the code at once - no more hard coding strings in multiple locations!
    // Then just pass this object around in the constructors for things like the EFK and controller

    KnowledgeBase knowledge;
    java.lang.String prefix;
    UpdateSettings settings; // used to force a global variable to not broadcast as if it were local

    Double distToDest;
    Double sufficientProximity;
    Double peakVelocity;
    Double accel;
    Double decel;
    public Integer teleopStatus; // see TELEOPERATION_TYPES enum
    NativeDoubleVector motorCommands;
    Double thrustFraction;
    Double bearingFraction;
    NativeDoubleVector bearingPIDGains;
    NativeDoubleVector thrustPIDGains;
    NativeDoubleVector thrustPPIGains;
    DoubleVector eastingNorthingBearing; // UTM x,y,th
    NativeDoubleVector location; // stand in for device.{id}.location
    NativeDoubleVector dest; // stand in for device.{.id}.location
    Integer longitudeZone;
    String latitudeZone; // a single character (see UTM) http://jscience.org/api/org/jscience/geography/coordinates/UTM.html
    Integer waypointsFinishedStatus; // used as a stand in for device.{id}.algorithm.waypoints.finished
    final double defaultSufficientProximity = 3.0;
    final double defaultPeakVelocity = 2.0;
    final double defaultAccelTime = 5.0;
    final double defaultDecelTime = 5.0;
    final long defaultTeleopStatus = 0L;
    final double[] bearingPIDGainsDefaults = new double[]{0.5,0.5,0.5}; // cols: P,I,D
    final double[] thrustPIDGainsDefaults = new double[]{0.2,0,0.3}; // cols: P,I,D
    final double[] thrustPPIGainsDefaults = new double[]{1.0,1.0,1.0}; // cols: Pos-P, Vel-P, Vel-I    

    Self self;
    
    public LutraMadaraContainers(KnowledgeBase knowledge, int id) {
        this.knowledge = knowledge;
        this.prefix = java.lang.String.format("device.%d.",id);
        this.settings = new UpdateSettings();
        settings.setTreatGlobalsAsLocals(true);
        
        distToDest = new Double();
        distToDest.setName(knowledge, prefix + "distToDest");
        sufficientProximity = new Double();
        sufficientProximity.setName(knowledge,prefix + "sufficientProximity");
        peakVelocity = new Double();
        peakVelocity.setName(knowledge, prefix + "peakVelocity");
        accel = new Double();
        accel.setName(knowledge, prefix + "accelTime");
        decel = new Double();
        decel.setName(knowledge, prefix + "decelTime");
        motorCommands = new NativeDoubleVector();
        motorCommands.setName(knowledge, prefix + "motorCommands");
        motorCommands.resize(2);
        teleopStatus = new Integer();
        teleopStatus.setName(knowledge, prefix + "teleopStatus");
        longitudeZone = new Integer();
        longitudeZone.setName(knowledge, prefix + "longitudeZone");
        longitudeZone.setSettings(settings);
        latitudeZone = new String();
        latitudeZone.setName(knowledge, prefix + "latitudeZone");
        latitudeZone.setSettings(settings);                                   
        eastingNorthingBearing = new DoubleVector();
        eastingNorthingBearing.setName(knowledge, prefix + "eastingNorthingBearing");
        eastingNorthingBearing.resize(3);
        location = new NativeDoubleVector();
        location.setName(knowledge, prefix + "location");
        location.resize(3);
        location.setSettings(settings);   
        dest = new NativeDoubleVector();
        dest.setName(knowledge, prefix + "dest");
        dest.resize(3);              
        
        waypointsFinishedStatus = new Integer();
        waypointsFinishedStatus.setName(knowledge, prefix + "algorithm.waypoints.finished");
        
        bearingPIDGains= new NativeDoubleVector();
        bearingPIDGains.setName(knowledge, prefix + "bearingPIDGains");
        bearingPIDGains.resize(3);

        thrustPIDGains= new NativeDoubleVector();
        thrustPIDGains.setName(knowledge, prefix + "thrustPIDGains");
        thrustPIDGains.resize(3);

        thrustPPIGains= new NativeDoubleVector();
        thrustPPIGains.setName(knowledge, prefix + "thrustPPIGains");
        thrustPPIGains.resize(3);
        
        thrustFraction = new Double();
        bearingFraction = new Double();
        thrustFraction.setName(knowledge, prefix + "thrustFraction");
        bearingFraction.setName(knowledge, prefix + "bearingFraction");        
        
        restoreDefaults();
        
        settings.free();
    }

    public void freeAll() {
        distToDest.free();
        sufficientProximity.free();
        peakVelocity.free();
        accel.free();
        decel.free();
        teleopStatus.free();
        motorCommands.free();
        longitudeZone.free();
        latitudeZone.free();
        eastingNorthingBearing.free();
        location.free();
        dest.free();
        waypointsFinishedStatus.free();
        bearingPIDGains.free();
        thrustPIDGains.free();
        thrustPPIGains.free();    
        thrustFraction.free();
        bearingFraction.free();
    }

    public void restoreDefaults() {
        sufficientProximity.set(defaultSufficientProximity);
        peakVelocity.set(defaultPeakVelocity);
        accel.set(defaultAccelTime);
        decel.set(defaultDecelTime);
        teleopStatus.set(defaultTeleopStatus);
        for (int i = 0; i < 3; i++) {
            bearingPIDGains.set(i,bearingPIDGainsDefaults[i]);
            thrustPIDGains.set(i,thrustPIDGainsDefaults[i]);
            thrustPPIGains.set(i,thrustPPIGainsDefaults[i]);
        }
    }
    
    public void setSelf(Self self) {
        this.self = self;
    }
    
    public void setTeleopStatus(TELEOPERATION_TYPES type) {
        teleopStatus.set(type.getLongValue());
    }
    
    public void keepCurrentLocation() {
        dest.set(0, eastingNorthingBearing.get(0));
        dest.set(1, eastingNorthingBearing.get(1));
        dest.set(2, eastingNorthingBearing.get(2));            
    }
    
    public void stopMotors() {
        motorCommands.set(0, 0.0);
        motorCommands.set(1, 0.0);
    }
    
    public double[] getMotorCommands() {
        return motorCommands.toRecord().toDoubleArray();
    }
    
    public double[] getThrustAndBearingFraction() {
        double[] thrustAndBearingFraction = new double[2];
        thrustAndBearingFraction[0] = thrustFraction.get();
        thrustAndBearingFraction[1] = bearingFraction.get();
        return thrustAndBearingFraction;
    }
    
    public void setThrustAndRudderFraction(double thrustFraction, double bearingFraction) {
        this.thrustFraction.set(thrustFraction);
        this.bearingFraction.set(bearingFraction);
    }
    
    public void setBearingPIDGains(double P, double I, double D) {
        bearingPIDGains.set(0, P);
        bearingPIDGains.set(1, I);
        bearingPIDGains.set(2, D);
    }
    
    public void setThrustPIDGains(double P, double I, double D) {
        thrustPIDGains.set(0, P);
        thrustPIDGains.set(1, I);
        thrustPIDGains.set(2, D);        
    }    
    
    public void setThrustPPIGains(double PosP, double VelP, double VelI) {
        thrustPPIGains.set(0,PosP);
        thrustPPIGains.set(1,VelP);
        thrustPPIGains.set(2,VelI);
    }
    
    

}
