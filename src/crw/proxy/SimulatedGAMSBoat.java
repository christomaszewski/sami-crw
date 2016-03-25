package crw.proxy;

import com.gams.algorithms.BaseAlgorithm;
import com.gams.controllers.BaseController;
//import com.gams.utility.Logging;

import com.madara.KnowledgeBase;
import com.madara.transport.QoSTransportSettings;
import com.madara.transport.TransportType;
import crw.sensor.BoatSensor;
import gov.nasa.worldwind.geom.coords.UTMCoord;

/**
 *
 * @author jjb
 */
public class SimulatedGAMSBoat implements Runnable {
    
    int id;
    int teamSize;
    java.lang.String name; 
    BaseController controller;
    SimulatedGAMSBoatPlatform platform;
    QoSTransportSettings settings;
    KnowledgeBase knowledge;
    BaseAlgorithm algorithm;
    UTMCoord initialUTMCoord;
    BoatProxy bp;
    BoatSensor sensor;
    
    public SimulatedGAMSBoat(int id, int teamSize, UTMCoord initialUTMCoord, BoatProxy bp, boolean spoofData) {
        this.id = id;
        this.teamSize = teamSize;
        this.initialUTMCoord = initialUTMCoord;
        this.bp = bp;        
        if (spoofData) {
            sensor = new BoatSensor(this.bp, 0); // one fake sensor on channel 0
        }
        settings = new QoSTransportSettings();
        settings.setHosts(new String[]{"239.255.0.1:4150"});
        settings.setType(TransportType.MULTICAST_TRANSPORT);
        settings.setRebroadcastTtl(2);
        settings.enableParticipantTtl(1);
        name = String.format("SimBoat#%d",id);
        knowledge = new KnowledgeBase(name,settings);
        controller = new BaseController(knowledge);
        
        if (id == 0) {
            //com.gams.utility.Logging.setLevel(6);
            //com.madara.logger.GlobalLogger.setLevel(6);
        }
    }
    
    void start() {
        controller.initVars(id, teamSize);
        platform = new SimulatedGAMSBoatPlatform(knowledge, id, initialUTMCoord);
        algorithm = new DwellAlgorithm();
        controller.initPlatform(platform);
        controller.initAlgorithm(algorithm);
        new Thread(new Runnable() {
            @Override
            public void run() { 
                controller.runHz(5.0, 3600.0, 5.0);
            }
        }).start();
    }

    @Override
    public void run() {
        start();
    }
    
    
    
    
}