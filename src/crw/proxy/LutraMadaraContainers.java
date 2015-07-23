package crw.proxy;

import com.gams.variables.Self;

import com.madara.KnowledgeBase;
import com.madara.containers.Double;
import com.madara.containers.DoubleVector;
import com.madara.containers.Integer;
import com.madara.containers.String;

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

    Double distToDest;
    Double sufficientProximity;
    Double peakVelocity;
    Double accel;
    Double decel;
    Integer teleopStatus; // see TELEOPERATION_TYPES enum
    Integer localized; // == 1 if both GPS and compass are initialized
    DoubleVector motorCommands;
    DoubleVector latLong;
    Integer longitudeZone;
    String latitudeZone; // a single character (see UTM) http://jscience.org/api/org/jscience/geography/coordinates/UTM.html
    final double defaultSufficientProximity = 3.0;
    final double defaultPeakVelocity = 2.0;
    final double defaultAccelTime = 5.0;
    final double defaultDecelTime = 5.0;
    final long defaultTeleopStatus = 0L;

    Self self;
    
    public LutraMadaraContainers(KnowledgeBase knowledge, int id) {
        this.knowledge = knowledge;
        this.prefix = java.lang.String.format("device.%d.",id);
        distToDest = new Double();
        distToDest.setName(knowledge, prefix + "distToDest");
        sufficientProximity = new Double();
        sufficientProximity.setName(knowledge,prefix + "sufficientProximity");
        sufficientProximity.set(defaultSufficientProximity);
        peakVelocity = new Double();
        peakVelocity.setName(knowledge, prefix + "peakVelocity");
        peakVelocity.set(defaultPeakVelocity);
        accel = new Double();
        accel.setName(knowledge, prefix + "accelTime");
        accel.set(defaultAccelTime);
        decel = new Double();
        decel.setName(knowledge, prefix + "decelTime");
        decel.set(defaultDecelTime);
        motorCommands = new DoubleVector();
        motorCommands.setName(knowledge, prefix + "motorCommands");
        motorCommands.resize(2);
        teleopStatus = new Integer();
        teleopStatus.setName(knowledge, prefix + "teleopStatus");
        teleopStatus.set(defaultTeleopStatus);
        localized = new Integer();
        localized.setName(knowledge,".localized");
        localized.set(0);
        latLong = new DoubleVector();
        latLong.setName(knowledge,prefix + "latLong");
        latLong.resize(2);
        longitudeZone = new Integer();
        longitudeZone.setName(knowledge, prefix + "longitudeZone");
        latitudeZone = new String();
        latitudeZone.setName(knowledge, prefix + "latitudeZone");
    }

    public void freeAll() {
        distToDest.free();
        sufficientProximity.free();
        peakVelocity.free();
        accel.free();
        decel.free();
        teleopStatus.free();
        localized.free();
        motorCommands.free();
        latLong.free();
        longitudeZone.free();
        latitudeZone.free();
    }

    public void restoreDefaults() {
        sufficientProximity.set(defaultSufficientProximity);
        peakVelocity.set(defaultPeakVelocity);
        accel.set(defaultAccelTime);
        decel.set(defaultDecelTime);
        teleopStatus.set(defaultTeleopStatus);
    }
    
    public void setSelf(Self self) {
        this.self = self;
    }

}
