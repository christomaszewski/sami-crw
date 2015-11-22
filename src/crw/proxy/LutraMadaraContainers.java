package crw.proxy;

import com.gams.variables.Self;
import com.madara.EvalSettings;

import com.madara.KnowledgeBase;
import com.madara.UpdateSettings;
import com.madara.containers.Double;
import com.madara.containers.FlexMap;
import com.madara.containers.Integer;
import com.madara.containers.NativeDoubleVector;
import com.madara.containers.String;


/**
 * @author jjb
 */
public class LutraMadaraContainers {

    // Pass in knowledge base
    // Takes care of all the SetName's for all the code at once - no more hard coding strings in multiple locations!
    // Then just pass this object around in the constructors for things like the EFK and controller

    KnowledgeBase knowledge;
    public java.lang.String prefix;
    UpdateSettings settings; // used to force a global variable to not broadcast as if it were local

    FlexMap environmentalData;
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
    NativeDoubleVector eastingNorthingBearing; // UTM x,y,th
    NativeDoubleVector errorEllipse; // width, height, angle
    NativeDoubleVector location; // stand in for agent.{id}.location
    NativeDoubleVector dest; // stand in for agent.{.id}.dest
    NativeDoubleVector home; // stand in for agent.{.id}.ome
    Integer longitudeZone;
    String latitudeZone; // a single character (see UTM) http://jscience.org/api/org/jscience/geography/coordinates/UTM.html
    Integer waypointsFinishedStatus; // used as a stand in for agent.{id}.algorithm.waypoints.finished
    Integer resetLocalization;
    Integer connectivityWatchdog;
    Integer wifiStrength;
    Integer gpsWatchdog;
    final double defaultSufficientProximity = 2.0;
    final double defaultPeakVelocity = 2.0;
    final double defaultAccelTime = 5.0;
    final double defaultDecelTime = 5.0;
    final long defaultTeleopStatus = TELEOPERATION_TYPES.GUI_MS.getLongValue();
    final double[] bearingPIDGainsDefaults = new double[]{0.3,0.01,0.5}; // cols: P,I,D
    final double[] thrustPIDGainsDefaults = new double[]{0.1,0,0.2}; // cols: P,I,D
    final double[] thrustPPIGainsDefaults = new double[]{0.2,0.2,0.05}; // cols: Pos-P, Vel-P, Vel-I

    Self self;
    
    public LutraMadaraContainers(KnowledgeBase knowledge, int id) {        
        this.knowledge = knowledge;
        this.prefix = java.lang.String.format("agent.%d.",id);
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
        eastingNorthingBearing = new NativeDoubleVector();
        eastingNorthingBearing.setName(knowledge, prefix + "eastingNorthingBearing");
        eastingNorthingBearing.resize(3);
        
        errorEllipse = new NativeDoubleVector();
        errorEllipse.setSettings(settings);   
        errorEllipse.setName(knowledge, prefix + "errorEllipse");
        errorEllipse.resize(3);
        
        location = new NativeDoubleVector();
        location.setName(knowledge, prefix + "location");
        location.setSettings(settings);   
        location.resize(3);
        
        dest = new NativeDoubleVector();
        dest.setSettings(settings);
        dest.setName(knowledge, prefix + "dest");
        dest.resize(3);        
        
        home = new NativeDoubleVector();
        home.setSettings(settings);
        home.setName(knowledge, prefix + "home");
        home.resize(3);
        
        
        waypointsFinishedStatus = new Integer();
        waypointsFinishedStatus.setName(knowledge, prefix + "algorithm.waypoints.finished");     
        
        resetLocalization = new Integer();
        resetLocalization.setName(knowledge, prefix + "resetLocalization");
        resetLocalization.set(0);        
        
        bearingPIDGains= new NativeDoubleVector();
        bearingPIDGains.setName(knowledge, prefix + "bearingPIDGains");
        bearingPIDGains.setSettings(settings);
        bearingPIDGains.resize(3);

        thrustPIDGains= new NativeDoubleVector();
        thrustPIDGains.setName(knowledge, prefix + "thrustPIDGains");
        thrustPIDGains.setSettings(settings);
        thrustPIDGains.resize(3);

        thrustPPIGains= new NativeDoubleVector();
        thrustPPIGains.setName(knowledge, prefix + "thrustPPIGains");
        thrustPPIGains.setSettings(settings);
        thrustPPIGains.resize(3);
        
        thrustFraction = new Double();
        bearingFraction = new Double();
        thrustFraction.setName(knowledge, prefix + "thrustFraction");
        bearingFraction.setName(knowledge, prefix + "bearingFraction");        
               
        connectivityWatchdog = new Integer();
        connectivityWatchdog.setName(knowledge, prefix + "connectivityWatchdog");
        connectivityWatchdog.set(0L); // boat sets to 1, GUI sets to 0, if the GUI doesn't see a 1, there is an issue with the connection
        
        gpsWatchdog = new Integer();
        gpsWatchdog.setName(knowledge, prefix + "gpsWatchdog");
        gpsWatchdog.set(0L); // boat sets to 1, GUI sets to 0, if the GUI doesn't see a 1, there is an issue with the gps

        wifiStrength = new Integer();
        wifiStrength.setName(knowledge, prefix + "wifiStrength");        
        
        environmentalData = new FlexMap();
        environmentalData.setName(knowledge, prefix + "environmentalData");
        
                
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
        resetLocalization.free();
        errorEllipse.free();
        connectivityWatchdog.free();
        wifiStrength.free();
        gpsWatchdog.free();
        environmentalData.free();
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
    
    public double[] getLocation() {
        double[] result = new double[3];
        result[0] = location.get(0);
        result[1] = location.get(1);
        result[2] = 0.0;
        return result;
    }
    
    public void resetLocalization() {
        resetLocalization.set(1);
    }
    
    public void reHome() {
        UpdateSettings makeItGlobal = new UpdateSettings();
        home.setSettings(makeItGlobal);
        home.set(0, eastingNorthingBearing.get(0));
        home.set(1, eastingNorthingBearing.get(1));
        home.set(2, eastingNorthingBearing.get(2));            
        makeItGlobal.free();
    }
    
    public void keepCurrentLocation() {
        UpdateSettings makeItGlobal = new UpdateSettings();
        dest.setSettings(makeItGlobal);
        dest.set(0, eastingNorthingBearing.get(0));
        dest.set(1, eastingNorthingBearing.get(1));
        dest.set(2, eastingNorthingBearing.get(2));            
        makeItGlobal.free();
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
        UpdateSettings makeItGlobal = new UpdateSettings();
        bearingPIDGains.setSettings(makeItGlobal);
        bearingPIDGains.set(0, P);
        bearingPIDGains.set(1, I);
        bearingPIDGains.set(2, D);
        makeItGlobal.free();
    }
    public double[] getBearingPIDGains() {
        double[] result = new double[3];
        result[0] = bearingPIDGains.get(0);
        result[1] = bearingPIDGains.get(1);
        result[2] = bearingPIDGains.get(2);
        return result;
    }
    
    public void setThrustPIDGains(double P, double I, double D) {
        UpdateSettings makeItGlobal = new UpdateSettings();
        thrustPIDGains.setSettings(makeItGlobal);
        thrustPIDGains.set(0, P);
        thrustPIDGains.set(1, I);
        thrustPIDGains.set(2, D);  
        makeItGlobal.free();
    }    
    public double[] getThrustPIDGains() {
        double[] result = new double[3];
        result[0] = thrustPIDGains.get(0);
        result[1] = thrustPIDGains.get(1);
        result[2] = thrustPIDGains.get(2);
        return result;
    }    
    
    public void setThrustPPIGains(double PosP, double VelP, double VelI) {
        UpdateSettings makeItGlobal = new UpdateSettings();
        thrustPPIGains.setSettings(makeItGlobal);        
        thrustPPIGains.set(0,PosP);
        thrustPPIGains.set(1,VelP);
        thrustPPIGains.set(2,VelI);
        makeItGlobal.free();
    }
    public double[] getThrustPPIGains() {
        double[] result = new double[3];
        result[0] = thrustPPIGains.get(0);
        result[1] = thrustPPIGains.get(1);
        result[2] = thrustPPIGains.get(2);
        return result;
    }      
    
    public double[] getErrorEllipse() {
        double[] result = new double[3];
        result[0] = errorEllipse.get(0);
        result[1] = errorEllipse.get(1);
        result[2] = errorEllipse.get(2);
        return result;        
    }
    
    

}
