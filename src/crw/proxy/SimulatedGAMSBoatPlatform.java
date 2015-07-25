package crw.proxy;


import com.gams.controllers.BaseController;
import com.gams.platforms.BasePlatform;
import com.gams.utility.Axes;
import com.gams.utility.Position;
import com.gams.platforms.PlatformStatusEnum;


import com.madara.KnowledgeBase;
import com.madara.threads.BaseThread;
import com.madara.threads.Threader;
import gov.nasa.worldwind.geom.coords.UTMCoord;
import java.util.Arrays;

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
    double[] currentDestination;
    Threader threader;
    
    final double ROTVEL = Math.PI/5.0; // rad/s
    final double VEL = 10.0; // m/s
    
    public SimulatedGAMSBoatPlatform(KnowledgeBase knowledge, int id, UTMCoord initialUTMCoord) {
        this.knowledge = knowledge;        
        this.id = id;
        name = java.lang.String.format("SimBoat#%d",id);
        this.initialUTMCoord = initialUTMCoord;
        
        containers = new LutraMadaraContainers(this.knowledge, id);
                
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
        containers.self.device.dest.set(0, easting);
        containers.self.device.dest.set(1, northing);
        containers.self.device.dest.set(2,0.0);
        
        currentDestination = new double[2];      
        
        t = System.currentTimeMillis();
        threader = new Threader(knowledge);
        threader.run(5,"movement",new MovementThread());
        
        knowledge.sendModifieds();
    }
    
    class MovementThread extends BaseThread {
        @Override
        public void run() {
            updateDistToDest();
            //while (containers.distToDest.get() > proximity) {                        
            Long old_t = t;
            t = System.currentTimeMillis();
            double dt = (t.doubleValue() - old_t.doubleValue())/1000.0; // time difference in seconds                
            if (containers.distToDest.get() > getPositionAccuracy()) {                                
                double[] x = self.device.location.toRecord().toDoubleArray();
                double[] xd = self.device.dest.toRecord().toDoubleArray();
                double[] xError = new double[3];
                xError[0] = xd[0] - x[0];
                xError[1] = xd[1] - x[1];                    
                double angleToGoal = Math.atan2(xError[1],xError[0]);
                xError[2] = angleToGoal - x[2];
                // Error magnitude must be <= 180 degrees. Wrap the error into [-180,180]
                while (Math.abs(xError[2]) > Math.PI) {
                    xError[2] = xError[2] - Math.signum(xError[2])*2*Math.PI;
                }                                        
                if (Math.abs(xError[2]) > 0) { // point toward goal first, then move
                    self.device.location.set(2, x[2] + minAbs(Math.signum(xError[2])*ROTVEL*dt,xError[2]));
                }
                else { // now that you point directly to the goal, move toward it
                    // find max velocity in x and y
                    double VELX = Math.abs(VEL*Math.cos(angleToGoal));
                    double VELY = Math.abs(VEL*Math.sin(angleToGoal));                        
                    self.device.location.set(0, x[0] + minAbs(Math.signum(xError[0])*VELX*dt,xError[0]));
                    self.device.location.set(1, x[1] + minAbs(Math.signum(xError[1])*VELY*dt,xError[1]));
                    UTM utm = UTM.valueOf((int)containers.longitudeZone.get(), containers.latitudeZone.get().charAt(0), 
                            self.device.location.get(0), self.device.location.get(1), SI.METER);
                    
                    // remember, GUI listens to latitude and longitude, so they must also be updated
                    LatLong latLong = UTM.utmToLatLong(utm, ReferenceEllipsoid.WGS84);
                    containers.latLong.set(0,latLong.latitudeValue(NonSI.DEGREE_ANGLE));
                    containers.latLong.set(1,latLong.longitudeValue(NonSI.DEGREE_ANGLE));
                }
                updateDistToDest();
                try {
                    Thread.sleep(100);
                } 
                catch (InterruptedException ex) {                                                    
                }
            }            
        }
        
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
        return 0.0;
    }
    @Override
    public double getPositionAccuracy() {
        return 0.0;
    }
    @Override
    public Position getPosition() {
        Position position = new Position(containers.latLong.get(0),containers.latLong.get(1),0.0);
        return position;
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
        // Arrays.equals(a,b) --> compare contents of two arrays, see if they are equal
        // do this with current destination and the input target
        double[] targetDA = new double[2];
        targetDA[0] = target.getX();
        targetDA[1] = target.getY();        
        if (!Arrays.equals(currentDestination, targetDA)) {
            currentDestination = targetDA.clone();            
            UTM utmLoc = UTM.latLongToUtm(LatLong.valueOf(target.getX(),target.getY(), NonSI.DEGREE_ANGLE),ReferenceEllipsoid.WGS84);
            self.device.dest.set(0,utmLoc.eastingValue(SI.METER));
            self.device.dest.set(1,utmLoc.northingValue(SI.METER));        
            self.device.source.set(0,self.device.location.get(0));
            self.device.source.set(1,self.device.location.get(1));
            self.device.source.set(2,self.device.location.get(2));                    
        }        
        return PlatformStatusEnum.OK.value();
    }
    
    void updateDistToDest() {
        double[] x = self.device.location.toRecord().toDoubleArray();
        double[] xd = self.device.dest.toRecord().toDoubleArray();
        containers.distToDest.set(Math.pow(Math.pow(xd[0]-x[0],2.0) + Math.pow(xd[1]-x[1],2.0),0.5));
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
