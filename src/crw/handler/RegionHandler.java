package crw.handler;

import com.madara.EvalSettings;
import com.madara.KnowledgeBase;
import com.madara.containers.NativeDoubleVector;
import crw.Conversion;
import crw.event.input.operator.OperatorCreatesRegion;
import crw.event.output.operator.OperatorCreateRegion;
import crw.event.output.proxy.ProxyPerimeterPatrol;
import crw.event.output.proxy.ProxyFormationCoverage;
import crw.event.output.proxy.ProxyRandomEdgeCoverage;
import crw.proxy.BoatProxy;
import crw.proxy.CrwProxyServer;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.render.Polygon;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import sami.area.Area2D;
import sami.engine.Engine;
import sami.event.GeneratedEventListenerInt;
import sami.event.GeneratedInputEventSubscription;
import sami.event.InputEvent;
import sami.event.OutputEvent;
import sami.handler.EventHandlerInt;
import sami.mission.Token;
import sami.path.Location;
import sami.proxy.ProxyServerInt;
import sami.service.information.InformationServer;
import sami.service.information.InformationServiceProviderInt;

/**
 *
 * @author jjb
 */
public class RegionHandler implements EventHandlerInt, InformationServiceProviderInt {

    private final static Logger LOGGER = Logger.getLogger(RegionHandler.class.getName());
    ArrayList<GeneratedEventListenerInt> listeners = new ArrayList<GeneratedEventListenerInt>();
    HashMap<GeneratedEventListenerInt, Integer> listenerGCCount = new HashMap<GeneratedEventListenerInt, Integer>();
    InputEvent ie = null;

    public RegionHandler() {
        InformationServer.addServiceProvider(this);
    }
    
    @Override
    public void invoke(OutputEvent oe, ArrayList<Token> tokens) {
        LOGGER.log(Level.FINE, "RegionHandler invoked with " + oe);
        if (oe.getId() == null) {
            LOGGER.log(Level.WARNING, "\tOutputEvent " + oe + " has null UUID");
        }
        
        
        
        if (oe instanceof OperatorCreateRegion) {
            OperatorCreateRegion request = (OperatorCreateRegion) oe;
            ArrayList<Position> positions = new ArrayList<Position>();            
            Area2D region = request.getRegion();
            List<Location> locations = region.getPoints();
            int regionNo = request.getRegionNo();
            for (Location location : locations) {
                positions.add(Conversion.locationToPosition(location));
            }            
            Polygon polygon = new Polygon(positions);
            
            ProxyServerInt proxyServer = Engine.getInstance().getProxyServer();
            if (proxyServer instanceof CrwProxyServer) {            
                KnowledgeBase knowledge = ((CrwProxyServer)proxyServer).knowledge;
                NativeDoubleVector newRegion = new NativeDoubleVector();
                java.lang.String prefix = java.lang.String.format("region.%d.",regionNo);                
                EvalSettings delay = new EvalSettings();
                delay.setDelaySendingModifieds(true);
                
                // manually create Madara keys for a region
                knowledge.set(prefix + "type", 0, delay);
                knowledge.set(prefix + "priority", 0, delay);
                knowledge.set(prefix + "size", positions.size() ,delay);
                for (int i = 0; i < positions.size(); i++) {
                    NativeDoubleVector vertexNDV = new NativeDoubleVector();                    
                    vertexNDV.setName(knowledge, java.lang.String.format("%s%d",prefix,i));
                    vertexNDV.resize(2);                                                               
                    vertexNDV.set(0,positions.get(i).latitude.degrees);
                    vertexNDV.set(1,positions.get(i).longitude.degrees);
                    vertexNDV.free();
                }
                knowledge.sendModifieds();
            }                        
            
            OperatorCreatesRegion myIE = new OperatorCreatesRegion(oe.getId(),oe.getMissionId());
            ie = myIE;            
            
            // @todo TODO: draw a polygon on the worldwind map for user's visual feedback
        }
        else if (oe instanceof ProxyFormationCoverage) {
            ProxyFormationCoverage request = (ProxyFormationCoverage) oe;
            int regionNo = request.getRegionNo();
            int leaderNo = request.getLeaderNo();
            String modifier = request.getModifier();            
            double spacing = request.getSpacing();
            String coverageType = request.getCoverageType();
            
            int numProxies = 0;     
            String teamMembers = "";
            ArrayList<Token> tokensWithProxy = new ArrayList<Token>();
            ArrayList<BoatProxy> boatProxies = new ArrayList<BoatProxy>();
            for (Token token : tokens) {
                if (token.getProxy() != null && token.getProxy() instanceof BoatProxy) {
                    tokensWithProxy.add(token);
                    boatProxies.add((BoatProxy)token.getProxy());
                    numProxies++;
                    teamMembers = teamMembers.concat(String.format("%d,",((BoatProxy)token.getProxy()).getBoatNo()));
                }                
            }
            teamMembers = teamMembers.substring(0, teamMembers.length()-1); // remove trailing comma            
            teamMembers = String.format("%d,",numProxies).concat(teamMembers); // append number of members to the front of this list
            if (numProxies == 0) {
                LOGGER.log(Level.WARNING, "ProxyFormationCoverage had no relevant proxies attached: " + oe);
            }            
            
            ProxyServerInt proxyServer = Engine.getInstance().getProxyServer();
            if (proxyServer instanceof CrwProxyServer) { 
                KnowledgeBase knowledge = ((CrwProxyServer)proxyServer).knowledge;
                EvalSettings delay = new EvalSettings();
                delay.setDelaySendingModifieds(true);
                int numFollowers = numProxies - 1;
                int followerCount = 0;
                double anglePerFollower = 2*Math.PI/numFollowers;
                                                               
                for (int member = 0; member < numProxies; member++) { // for each team member
                    int boatNo = boatProxies.get(member).getBoatNo();
                    String prefix = String.format("device.%d.command",boatNo);
                    knowledge.set(prefix + ".size",6,delay);
                    knowledge.set(prefix,"formation coverage",delay);
                    knowledge.set(prefix + ".0",leaderNo,delay);
                    if (boatNo == leaderNo) {
                        knowledge.set(prefix + ".1","0,0,0",delay);
                    }
                    else {                        
                        knowledge.set(prefix + ".1",String.format("%f,%f,0",spacing,anglePerFollower*followerCount),delay);
                        followerCount++;
                    }
                    knowledge.set(prefix + ".2",teamMembers,delay);
                    knowledge.set(prefix + ".3",modifier,delay);
                    knowledge.set(prefix + ".4",coverageType,delay);
                    knowledge.set(prefix + ".5",String.format("region.%d",regionNo),delay);
                }
                knowledge.sendModifieds();                
                //knowledge.print();////////////////////////////////                
                delay.free();      
                
                // TODO: create input event
            }
        }    
        else if (oe instanceof ProxyPerimeterPatrol) {
            ProxyPerimeterPatrol request = (ProxyPerimeterPatrol) oe;
            int regionNo = request.getRegionNo();      
            int numProxies = 0;     
            ArrayList<Token> tokensWithProxy = new ArrayList<Token>();
            ArrayList<BoatProxy> boatProxies = new ArrayList<BoatProxy>();
            for (Token token : tokens) {
                if (token.getProxy() != null && token.getProxy() instanceof BoatProxy) {
                    tokensWithProxy.add(token);
                    boatProxies.add((BoatProxy)token.getProxy());
                    numProxies++;
                }                
            }      
            if (numProxies == 0) {
                LOGGER.log(Level.WARNING, "ProxyPerimeterPatrol had no relevant proxies attached: " + oe);
            }            
            
            ProxyServerInt proxyServer = Engine.getInstance().getProxyServer();
            if (proxyServer instanceof CrwProxyServer) { 
                KnowledgeBase knowledge = ((CrwProxyServer)proxyServer).knowledge;
                EvalSettings delay = new EvalSettings();
                delay.setDelaySendingModifieds(true);                
                for (int member = 0; member < numProxies; member++) { // for each team member
                    int boatNo = boatProxies.get(member).getBoatNo();
                    String prefix = String.format("device.%d.command",boatNo);
                    knowledge.set(prefix + ".size",1,delay);
                    knowledge.set(prefix + ".0",String.format("region.%d",regionNo),delay);
                    knowledge.set(prefix,"perimeter patrol",delay);
                }          
                knowledge.sendModifieds();                
                knowledge.print();////////////////////////////////                
                delay.free();    
                
                // TODO: create input event
            }            
        }        
        else if (oe instanceof ProxyRandomEdgeCoverage) {
            ProxyRandomEdgeCoverage request = (ProxyRandomEdgeCoverage) oe;
            int regionNo = request.getRegionNo();      
            int numProxies = 0;     
            ArrayList<Token> tokensWithProxy = new ArrayList<Token>();
            ArrayList<BoatProxy> boatProxies = new ArrayList<BoatProxy>();
            for (Token token : tokens) {
                if (token.getProxy() != null && token.getProxy() instanceof BoatProxy) {
                    tokensWithProxy.add(token);
                    boatProxies.add((BoatProxy)token.getProxy());
                    numProxies++;
                }                
            }      
            if (numProxies == 0) {
                LOGGER.log(Level.WARNING, "ProxyRandomEdgeCoverage had no relevant proxies attached: " + oe);
            }            
            
            ProxyServerInt proxyServer = Engine.getInstance().getProxyServer();
            if (proxyServer instanceof CrwProxyServer) { 
                KnowledgeBase knowledge = ((CrwProxyServer)proxyServer).knowledge;
                EvalSettings delay = new EvalSettings();
                delay.setDelaySendingModifieds(true);                
                for (int member = 0; member < numProxies; member++) { // for each team member
                    int boatNo = boatProxies.get(member).getBoatNo();
                    String prefix = String.format("device.%d.command",boatNo);
                    knowledge.set(prefix + ".size",1,delay);
                    knowledge.set(prefix + ".0",String.format("region.%d",regionNo),delay);
                    knowledge.set(prefix,"urec",delay);
                }          
                knowledge.sendModifieds();                
                knowledge.print();////////////////////////////////                
                delay.free();    
                
                // TODO: create input event
            }               
        }
                
        // cast and publish an input event response
        if (ie != null) {
            ArrayList<GeneratedEventListenerInt> listenersCopy = (ArrayList<GeneratedEventListenerInt>) listeners.clone();
            for (GeneratedEventListenerInt listener : listenersCopy) {
                listener.eventGenerated(ie);
            }
        }
        else {
            LOGGER.log(Level.WARNING, "RegionHandler produces a null input event");
        }
        
    }

    @Override
    public boolean offer(GeneratedInputEventSubscription sub) {
        LOGGER.log(Level.FINE, "RegionHandler offered subscription: " + sub);
        if (sub.getSubscriptionClass() == OperatorCreatesRegion.class) {
            LOGGER.log(Level.FINE, "\tRegionHandler taking subscription: " + sub);
            if (!listeners.contains(sub.getListener())) {
                LOGGER.log(Level.FINE, "\t\tRegionHandler adding listener: " + sub.getListener());
                listeners.add(sub.getListener());
                listenerGCCount.put(sub.getListener(), 1);
            } else {
                LOGGER.log(Level.FINE, "\t\tRegionHandler incrementing listener: " + sub.getListener());
                listenerGCCount.put(sub.getListener(), listenerGCCount.get(sub.getListener()) + 1);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean cancel(GeneratedInputEventSubscription sub) {
        LOGGER.log(Level.FINE, "RegionHandler asked to cancel subscription: " + sub);
        if ((sub.getSubscriptionClass() == OperatorCreatesRegion.class)
                && listeners.contains(sub.getListener())) {
            LOGGER.log(Level.FINE, "\tRegionHandler canceling subscription: " + sub);
            if (listenerGCCount.get(sub.getListener()) == 1) {
                // Remove listener
                LOGGER.log(Level.FINE, "\t\tRegionHandler removing listener: " + sub.getListener());
                listeners.remove(sub.getListener());
                listenerGCCount.remove(sub.getListener());
            } else {
                // Decrement garbage colleciton count
                LOGGER.log(Level.FINE, "\t\tRegionHandler decrementing listener: " + sub.getListener());
                listenerGCCount.put(sub.getListener(), listenerGCCount.get(sub.getListener()) - 1);
            }
            return true;
        }
        return false;
    }
    
}
