package crw.proxy;

import com.gams.algorithms.BaseAlgorithm;
import com.gams.controllers.BaseController;
//import com.gams.utility.Logging;

import com.madara.KnowledgeBase;
import com.madara.transport.QoSTransportSettings;
import com.madara.transport.TransportType;
import gov.nasa.worldwind.geom.coords.UTMCoord;

/**
 *
 * @author jjb
 */
public class SimulatedGAMSBoat implements Runnable {
    
    int id;
    int teamSize;
    java.lang.String name = java.lang.String.format("SimBoat#%d",id);
    BaseController controller;
    SimulatedGAMSBoatPlatform platform;
    QoSTransportSettings settings;
    KnowledgeBase knowledge;
    BaseAlgorithm algorithm;
    UTMCoord initialUTMCoord;
    BoatProxy bp;
    
    public SimulatedGAMSBoat(int id, int teamSize, UTMCoord initialUTMCoord, BoatProxy bp) {
        this.id = id;
        this.teamSize = teamSize;
        this.initialUTMCoord = initialUTMCoord;
        this.bp = bp;        
        settings = new QoSTransportSettings();
        settings.setHosts(new String[]{"239.255.0.1:4150"});
        settings.setType(TransportType.MULTICAST_TRANSPORT);
        //settings.setHosts(new String[]{"192.168.1.255:15000"});
        //settings.setType(TransportType.BROADCAST_TRANSPORT);
        settings.setRebroadcastTtl(2);
        settings.enableParticipantTtl(1);
        knowledge = new KnowledgeBase(name,settings);
        controller = new BaseController(knowledge);
        
        com.gams.utility.Logging.setLevel(6);
    }
    
    void start() {
        controller.initVars(id, teamSize);
        platform = new SimulatedGAMSBoatPlatform(knowledge, id, initialUTMCoord);
        algorithm = new DwellAlgorithm();
        controller.initPlatform(platform);
        controller.initAlgorithm(algorithm);
        //platform.start();
        new Thread(new Runnable() {
            @Override
            public void run() {    
                controller.run(1.0/5.0,3600.0); // run --> time interval, duration |  runHz --> run Hz, run duration, send Hz
            }
        }).start();
    }

    @Override
    public void run() {
        start();
    }
    
    
    
    
}