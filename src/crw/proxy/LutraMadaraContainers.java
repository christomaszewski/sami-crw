package crw.proxy;

import com.gams.variables.Self;

import com.madara.KnowledgeBase;
import com.madara.UpdateSettings;
import com.madara.containers.Double;
import com.madara.containers.DoubleVector;
import com.madara.containers.Integer;
import com.madara.containers.NativeDoubleVector;
import com.madara.containers.String;
import java.util.ArrayList;
import java.util.List;

enum TELEOPERATION_TYPES {
    NONE(0), GUI_WP(1), GUI_MS(2), RC(3);
    // NONE --> control loops are always active (always try to arrive at and stay at current destination unless algorithm overrides)
    // GUI_WP --> user selects waypoint(s) in a GUI. Boat controls arrival, but the boat does nothing after it arrives
    // GUI_MS --> user is sending motor signals to the boats via a GUI (w/ joystick). Boat control loops completely disabled
    // RC --> user is sending motor signals to the boats via a radio controller. Boat control loops completely disabled
    private final long value;
    TELEOPERATION_TYPES(long value) { this.value = value; }
    public final long getLongValue() { return value; }
}

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
    Integer teleopStatus; // see TELEOPERATION_TYPES enum
    DoubleVector motorCommands;
    NativeDoubleVector bearingPIDGains;
    NativeDoubleVector thrustPIDGains;
    NativeDoubleVector thrustPPIGains;
    DoubleVector eastingNorthingBearing; // UTM x,y,th
    NativeDoubleVector location; // stand in for device.{id}.location
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
        motorCommands = new DoubleVector();
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
        waypointsFinishedStatus.free();
        bearingPIDGains.free();
        thrustPIDGains.free();
        thrustPPIGains.free();        
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

}
