package crw.proxy;


import com.gams.controllers.BaseController;
import com.gams.platforms.BasePlatform;
import com.gams.utility.Axes;
import com.gams.utility.Position;
import com.gams.platforms.PlatformStatusEnum;

import com.madara.KnowledgeBase;
import gov.nasa.worldwind.geom.coords.UTMCoord;

import javax.measure.unit.NonSI;
import javax.measure.unit.SI;

import org.jscience.geography.coordinates.LatLong;
import org.jscience.geography.coordinates.UTM;
import org.jscience.geography.coordinates.crs.ReferenceEllipsoid;

/**
 *
 * @author jjb
 */
class SimulatedGAMSBoatPlatform extends BasePlatform {
    
    KnowledgeBase knowledge;
    int id;
    java.lang.String name;
    UTMCoord initialUTMCoord;
    LutraMadaraContainers containers; // important difference from actual GAMS on a phone: here the BoatProxy owns the containers
    Long t;
    
    final double ROTVEL = Math.PI/5.0; // rad/s (rotate 180 degrees in 5 seconds)
    final double VEL = 10; // m/s
    
    public SimulatedGAMSBoatPlatform(KnowledgeBase knowledge, int id, UTMCoord initialUTMCoord, LutraMadaraContainers containers) {
        this.knowledge = knowledge;        
        this.id = id;
        this.containers = containers;
        name = java.lang.String.format("SimBoat#%d",id);
        this.initialUTMCoord = initialUTMCoord;
        t = System.currentTimeMillis();
    }
    
    @Override
    public void init(BaseController controller) {
        super.init(controller);
        containers.setSelf(self); // need to do this here (but not on a real phone) b/c BoatProxy owns the containers
        this.self.device.dest.resize(3);
        this.self.device.home.resize(3);
        this.self.device.location.resize(3);
        this.self.device.source.resize(3);
        
        double lat = initialUTMCoord.getLatitude().degrees;
        double lon = initialUTMCoord.getLongitude().degrees;
        containers.latLong.set(0, lat);
        containers.latLong.set(1, lon);
        UTM utmLoc = UTM.latLongToUtm(LatLong.valueOf(lat,lon, NonSI.DEGREE_ANGLE),ReferenceEllipsoid.WGS84);
        containers.longitudeZone.set((long)utmLoc.longitudeZone());
        containers.latitudeZone.set(java.lang.String.format("%c",utmLoc.latitudeZone()));        
        double easting = utmLoc.eastingValue(SI.METER);
        double northing = utmLoc.northingValue(SI.METER);        
        containers.self.device.home.set(0, easting);
        containers.self.device.home.set(1, northing);
        containers.self.device.home.set(2,0.0);
        containers.self.device.location.set(0, easting);
        containers.self.device.location.set(1, northing);
        containers.self.device.location.set(2,0.0);        
    }
    
    double maxAbs(double a, double b) {
        if (Math.abs(a) > Math.abs(b)) { return a; }
        else {return b;}
    }
    
    double minAbs(double a, double b) {
        if (Math.abs(a) < Math.abs(b)) { return a; }
        else {return b;}
    }

    @Override
    public int analyze() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    @Override
    public double getAccuracy() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    @Override
    public double getPositionAccuracy() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    @Override
    public Position getPosition() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    @Override
    public int home() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    @Override
    public int land() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    @Override
    public int move(Position target, double proximity) {        
        
        UTM utmLoc = UTM.latLongToUtm(LatLong.valueOf(target.getX(),target.getY(), NonSI.DEGREE_ANGLE),ReferenceEllipsoid.WGS84);
        self.device.dest.set(0,utmLoc.eastingValue(SI.METER));
        self.device.dest.set(1,utmLoc.eastingValue(SI.METER));        
        self.device.source.set(0,self.device.location.get(0));
        self.device.source.set(1,self.device.location.get(1));
        self.device.source.set(2,self.device.location.get(2));
        
        // spawn a thread that just shifts the boat straight toward the goal
        new Thread(new Runnable() {
            @Override
            public void run() {
                updateDistToDest();
                while (containers.distToDest.get() > proximity) {                        
                    
                    Long old_t = t;
                    t = System.currentTimeMillis();
                    double dt = (t.doubleValue() - old_t.doubleValue())/1000.0; // time difference in seconds
                    
                    double[] x = new double[3];
                    double[] xd = new double[2];
                    double[] xError = new double[3];
                    x[0] = self.device.location.get(0);
                    x[1] = self.device.location.get(1);
                    x[2] = self.device.location.get(2);
                    xd[0] = self.device.dest.get(0);
                    xd[1] = self.device.dest.get(1);
                    xError[0] = xd[0] - x[0];
                    xError[1] = xd[1] - x[1];                    
                    double angleToGoal = Math.atan2(xError[1],xError[0]);
                    xError[2] = angleToGoal - x[2];
                    if (xError[2] > 0) { // point toward goal first, then move
                        self.device.location.set(2, x[2] - minAbs(ROTVEL*dt,xError[2]));
                    }
                    else { // now that you point directly to the goal, move toward it
                        // find max velocity in x and y
                        double VELX = VEL*Math.cos(angleToGoal);
                        double VELY = VEL*Math.sin(angleToGoal);                        
                        self.device.location.set(0, x[0] - minAbs(VELX*dt,xError[0]));
                        self.device.location.set(1, x[1] - minAbs(VELY*dt,xError[1]));
                    }
                    updateDistToDest();
                    try {
                        Thread.sleep(100);
                    } 
                    catch (InterruptedException ex) {                                                    
                    }
                }
            }            
        }).start();
        
        return PlatformStatusEnum.OK.value();
    }
    
    void updateDistToDest() {
        double[] x = new double[2];
        double[] xd = new double[2];
        x[0] = self.device.location.get(0);
        x[1] = self.device.location.get(1);
        xd[0] = self.device.dest.get(0);
        xd[1] = self.device.dest.get(1);
        containers.distToDest.set(Math.pow((Math.pow(xd[0]-x[0],2.0) + Math.pow(xd[1]-x[1],2.0)),0.5));         
    }
    
    
    @Override
    public int rotate(Axes axes) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    @Override
    public double getMinSensorRange() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    @Override
    public double getMoveSpeed() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    @Override
    public String getId() {
        return name;
    }
    @Override
    public String getName() {
        return name;
    }
    @Override
    public int sense() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    @Override
    public void setMoveSpeed(double speed) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    @Override
    public int takeoff() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    @Override
    public void stopMove() {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
}
