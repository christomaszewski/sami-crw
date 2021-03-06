package crw.handler;

import com.madara.EvalSettings;
import com.madara.KnowledgeBase;
import com.madara.containers.StringVector;
import crw.Conversion;
import crw.event.input.proxy.GainsSent;
import crw.event.input.proxy.GenericGAMSCommandSent;
import crw.event.input.proxy.ProxyAutonomyReHomed;
import crw.event.input.proxy.ProxyCreated;
import crw.event.input.proxy.ProxyEndsGAMSAlgorithm;
import crw.event.input.proxy.ProxyPathCompleted;
import crw.event.input.proxy.ProxyPathFailed;
import crw.event.input.proxy.ProxyPoseUpdated;
import crw.event.input.proxy.ProxyResetsLocalization;
import crw.event.input.proxy.StoppedAllAgents;
import crw.event.input.service.AssembleLocationResponse;
import crw.event.input.service.QuantityEqual;
import crw.event.input.service.QuantityGreater;
import crw.event.input.service.QuantityLess;
import crw.event.output.proxy.ConnectExistingProxy;
import crw.event.output.proxy.ConnectProxyList;
import crw.event.output.proxy.CreateSimulatedProxy;
import crw.event.output.proxy.FormCylindricalFormation;
import crw.event.output.proxy.FormGroup;
import crw.event.output.proxy.FormationSyncMove;
import crw.event.output.proxy.GroupBarrierTest;
import crw.event.output.service.AssembleLocationRequest;
import crw.event.output.proxy.ProxyEmergencyAbort;
import crw.event.output.proxy.ProxyEndGAMSAlgorithm;
import crw.event.output.proxy.ProxyExecutePath;
import crw.event.output.proxy.ProxyExploreArea;
import crw.event.output.proxy.ProxyGotoPoint;
import crw.event.output.proxy.ProxyReHomeAutonomy;
import crw.event.output.proxy.ProxyResendWaypoints;
import crw.event.output.proxy.ProxyResetLocalization;
import crw.event.output.proxy.SetGains;
import crw.event.output.proxy.StopAllAgents;
import crw.event.output.proxy.ZoneCoverage;
import crw.event.output.service.ProxyCompareDistanceRequest;
import crw.proxy.BoatProxy;
import crw.proxy.CrwProxyServer;
import crw.proxy.TELEOPERATION_TYPES;
import crw.ui.ImagePanel;
import static crw.ui.teleop.GainsPanel.RUDDER_GAINS_AXIS;
import static crw.ui.teleop.GainsPanel.THRUST_GAINS_AXIS;
import edu.cmu.ri.crw.FunctionObserver;
import edu.cmu.ri.crw.data.Utm;
import edu.cmu.ri.crw.data.UtmPose;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.geom.coords.UTMCoord;
import gov.nasa.worldwind.render.Polygon;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import robotutils.Pose3D;
import sami.CoreHelper;
import sami.area.Area2D;
import sami.engine.Engine;
import sami.event.GeneratedEventListenerInt;
import sami.event.GeneratedInputEventSubscription;
import sami.event.InputEvent;
import sami.event.OutputEvent;
import sami.event.ProxyAbortMission;
import sami.handler.EventHandlerInt;
import sami.mission.Token;
import sami.path.Location;
import sami.path.Path;
import sami.path.PathUtm;
import sami.path.UTMCoordinate;
import sami.path.UTMCoordinate.Hemisphere;
import sami.proxy.ProxyInt;
import sami.proxy.ProxyListenerInt;
import sami.proxy.ProxyServerInt;
import sami.proxy.ProxyServerListenerInt;
import sami.service.information.InformationServer;
import sami.service.information.InformationServiceProviderInt;

/**
 *
 * @author pscerri
 */
public class ProxyEventHandler implements EventHandlerInt, ProxyListenerInt, InformationServiceProviderInt, ProxyServerListenerInt {

    private static final Logger LOGGER = Logger.getLogger(ProxyEventHandler.class.getName());
    // For most of the interesting part of the planet, 1 degree latitude is something like 110,000m
    // Longtitude varies a bit more, but 90,000m is a decent number for the purpose of this calculation
    // See http://www.csgnetwork.com/degreelenllavcalc.html
    final double LON_D_PER_M = 1.0 / 90000.0;
    final double LAT_D_PER_M = 1.0 / 110000.0;
    // Sending a waypoints list of size > 68 causes failure due to data size
    final int MAX_SEGMENTS_PER_PROXY = 68;
    ArrayList<GeneratedEventListenerInt> listeners = new ArrayList<GeneratedEventListenerInt>();
    HashMap<GeneratedEventListenerInt, Integer> listenerGCCount = new HashMap<GeneratedEventListenerInt, Integer>();
    int portCounter = 0;
    int boatCounter = 0;
    private Hashtable<UUID, Integer> eventIdToAssembleCounter = new Hashtable<UUID, Integer>();

    public ProxyEventHandler() {
        LOGGER.log(Level.FINE, "Adding ProxyEventHandler as service provider");
        InformationServer.addServiceProvider(this);
        // Do not add as Proxy server listener here, will cause java.lang.ExceptionInInitializerError
        // Engine will add this for us
        //Engine.getInstance().getProxyServer().addListener(this);
    }

    @Override
    public void invoke(final OutputEvent oe, ArrayList<Token> tokens) {
        LOGGER.log(Level.FINE, "ProxyEventHandler invoked with OE [" + oe + "] and tokens [" + tokens + "]");
        if (oe.getId() == null) {
            LOGGER.log(Level.WARNING, "\tOutputEvent " + oe + " has null event id");
        }
        if (oe.getMissionId() == null) {
            LOGGER.log(Level.WARNING, "\tOutputEvent " + oe + " has null mission id");
        }

        if (oe instanceof ProxyGotoPoint) {
            int numProxies = 0;
            for (Token token : tokens) {
                if (token.getProxy() != null && token.getProxy() instanceof BoatProxy) {
                    // Send the path
                    token.getProxy().handleEvent(oe, token.getTask());
                    numProxies++;
                }
            }
            if (numProxies == 0) {
                LOGGER.log(Level.WARNING, "Place with ProxyGotoPoint has no tokens with proxies attached: " + oe);
            }
        } else if (oe instanceof ProxyExploreArea) {
            // Get the lawnmower path for the whole area
            ArrayList<Position> positions = new ArrayList<Position>();
            Area2D area = ((ProxyExploreArea) oe).getArea();
            // How many meters the proxy should move north after each horizontal section of the lawnmower pattern
            double latDegInc = ((ProxyExploreArea) oe).getSpacing() * LAT_D_PER_M;
            for (Location location : area.getPoints()) {
                positions.add(Conversion.locationToPosition(location));
            }
            Polygon polygon = new Polygon(positions);
            Object[] tuple = getLawnmowerPath(polygon, latDegInc);
            ArrayList<Position> lawnmowerPositions = (ArrayList<Position>) tuple[0];
            double totalLength = (Double) tuple[1];

            // Divy up the waypoints to the selected proxies
            // Explore rectangle using horizontally oriented lawnmower paths
            int numProxies = 0;
            ArrayList<Token> tokensWithProxy = new ArrayList<Token>();
            for (Token token : tokens) {
                if (token.getProxy() != null && token.getProxy() instanceof BoatProxy) {
                    tokensWithProxy.add(token);
                    numProxies++;
                }
            }
            if (numProxies == 0) {
                LOGGER.log(Level.WARNING, "ProxyExecutePath had no relevant proxies attached: " + oe);
            }
            double lengthPerProxy = totalLength / numProxies, proxyLength, length;
            List<Location> lawnmowerLocations;
            int lawnmowerIndex = 0;
            for (int proxyIndex = 0; proxyIndex < numProxies; proxyIndex++) {
                lawnmowerLocations = new ArrayList<Location>();
                proxyLength = 0.0;
                // Must have at least two remaining positions to form a path segment
                Position p1 = null;
                if (lawnmowerIndex < lawnmowerPositions.size() - 1) {
                    // We still have lawnmower segments to assign
                    p1 = lawnmowerPositions.get(lawnmowerIndex);
                    lawnmowerIndex++;
                    boolean loop = lawnmowerIndex < lawnmowerPositions.size() && proxyLength < lengthPerProxy;
                    while (loop) {
                        Position p2 = lawnmowerPositions.get(lawnmowerIndex);
                        if (lawnmowerIndex % 2 == 0) {
                            // Horizontal segment
                            length = Math.abs((p1.longitude.degrees - p2.longitude.degrees) * LON_D_PER_M);
                        } else {
                            // Vertical shift
                            length = latDegInc;
                        }
                        if (proxyLength + length < lengthPerProxy) {
                            lawnmowerLocations.add(Conversion.positionToLocation(p2));
                            proxyLength += length;
                            p1 = p2;
                            lawnmowerIndex++;
                            loop = lawnmowerIndex < lawnmowerPositions.size() && proxyLength < lengthPerProxy;
                        } else {
                            loop = false;
                        }
                    }

                    if (lawnmowerLocations.size() > MAX_SEGMENTS_PER_PROXY) {
                        LOGGER.log(Level.WARNING, "Waypoint list size is " + lawnmowerLocations.size() + ": Breaking waypoints list into pieces to prevent communication failure");
                    }
                    List<Location> proxyLocations;
                    for (int i = 0; i < lawnmowerLocations.size() / MAX_SEGMENTS_PER_PROXY + 1; i++) {
//                        LOGGER.log(Level.FINE, "i = " + i + " of " + (lawnmowerLocations.size() / MAX_SEGMENTS_PER_PROXY + 1) + ": sublist " + i * MAX_SEGMENTS_PER_PROXY + ", " + Math.min(lawnmowerLocations.size(), (i + 1) * MAX_SEGMENTS_PER_PROXY));
                        proxyLocations = lawnmowerLocations.subList(i * MAX_SEGMENTS_PER_PROXY, Math.min(lawnmowerLocations.size(), (i + 1) * MAX_SEGMENTS_PER_PROXY));
                        // Send the path
//                        LOGGER.log(Level.FINE, "Creating ProxyExecutePath with " + proxyLocations.size() + " waypoints for proxy " + tokenProxies.get(proxyIndex));
                        if (proxyLocations.isEmpty()) {
                            LOGGER.warning("ExploreArea path for proxy " + tokensWithProxy.get(proxyIndex).getProxy().getProxyName() + " is empty");
                        }
                        PathUtm path = new PathUtm(proxyLocations);
                        Hashtable<ProxyInt, Path> thisProxyPath = new Hashtable<ProxyInt, Path>();
                        thisProxyPath.put(tokensWithProxy.get(proxyIndex).getProxy(), path);
                        ProxyExecutePath proxyEvent = new ProxyExecutePath(oe.getId(), oe.getMissionId(), thisProxyPath);
                        tokensWithProxy.get(proxyIndex).getProxy().handleEvent(proxyEvent, tokensWithProxy.get(proxyIndex).getTask());
                    }
                } else {
                    // We have finished assigning all the lawnmower path segments
                    // Send a blank path to the remaining proxies otherwise we won't get a ProxyPathComplete InputEvent                        
                    // Send the path
                    Hashtable<ProxyInt, Path> thisProxyPath = new Hashtable<ProxyInt, Path>();
                    thisProxyPath.put(tokensWithProxy.get(proxyIndex).getProxy(), new PathUtm(new ArrayList<Location>()));
                    ProxyExecutePath proxyEvent = new ProxyExecutePath(oe.getId(), oe.getMissionId(), thisProxyPath);
                    tokensWithProxy.get(proxyIndex).getProxy().handleEvent(proxyEvent, tokensWithProxy.get(proxyIndex).getTask());
                }
            }
        } else if (oe instanceof AssembleLocationRequest) {
            AssembleLocationRequest request = (AssembleLocationRequest) oe;
            int assembleCounter = 0;
            if (eventIdToAssembleCounter.contains(request.getId())) {
                assembleCounter = eventIdToAssembleCounter.get(request.getId());
            }

            int numProxies = 0;
            Hashtable<ProxyInt, Location> proxyPoints = new Hashtable<ProxyInt, Location>();
            ArrayList<ProxyInt> relevantProxies = new ArrayList<ProxyInt>();
            for (Token token : tokens) {
                if (token.getProxy() != null && token.getProxy() instanceof BoatProxy) {
                    Location assembleLocation = getSpacedLocation(request.getLocation(), assembleCounter, request.getSpacing());
                    proxyPoints.put(token.getProxy(), assembleLocation);
                    relevantProxies.add(token.getProxy());
                    numProxies++;
                    assembleCounter++;
                }
            }
            if (numProxies == 0) {
                LOGGER.log(Level.WARNING, "Place with AssembleLocationRequest has no tokens with proxies attached: " + oe);
            }

            eventIdToAssembleCounter.put(request.getId(), assembleCounter);

            AssembleLocationResponse responseEvent = new AssembleLocationResponse(oe.getId(), oe.getMissionId(), proxyPoints, relevantProxies);
            for (GeneratedEventListenerInt listener : listeners) {
                LOGGER.log(Level.FINE, "\tSending response to listener: " + listener);
                listener.eventGenerated(responseEvent);
            }
        } else if (oe instanceof ProxyCompareDistanceRequest) {
            ProxyCompareDistanceRequest request = (ProxyCompareDistanceRequest) oe;
            ArrayList<InputEvent> responses = new ArrayList<InputEvent>();

            int numProxies = 0;
            ArrayList<ProxyInt> relevantProxies;
            for (Token token : tokens) {
                if (token.getProxy() != null && token.getProxy() instanceof BoatProxy) {
                    BoatProxy boatProxy = (BoatProxy) token.getProxy();
                    if (!request.getProxyCompareLocation().containsKey(boatProxy)) {
                        LOGGER.severe("Passed in proxy token for " + boatProxy + " to place with ProxyCompareDistanceRequest, but there is no compare location entry for the proxy!");
                    } else {
                        Position stationKeepPosition = Conversion.locationToPosition(request.getProxyCompareLocation().get(boatProxy));
                        UTMCoord stationKeepUtm = UTMCoord.fromLatLon(stationKeepPosition.latitude, stationKeepPosition.longitude);
                        UtmPose stationKeepPose = new UtmPose(new Pose3D(stationKeepUtm.getEasting(), stationKeepUtm.getNorthing(), 0.0, 0.0, 0.0, 0.0), new Utm(stationKeepUtm.getZone(), stationKeepUtm.getHemisphere().contains("North")));
                        Position boatPosition = boatProxy.getPosition();
                        UTMCoord boatUtm = UTMCoord.fromLatLon(boatPosition.latitude, boatPosition.longitude);
                        UtmPose boatPose = new UtmPose(new Pose3D(boatUtm.getEasting(), boatUtm.getNorthing(), 0.0, 0.0, 0.0, 0.0), new Utm(boatUtm.getZone(), boatUtm.getHemisphere().contains("North")));
                        double distance = boatPose.pose.getEuclideanDistance(stationKeepPose.pose);

                        InputEvent response;
                        relevantProxies = new ArrayList<ProxyInt>();
                        relevantProxies.add(boatProxy);
                        if (distance > request.compareDistance) {
                            response = new QuantityGreater(request.getId(), request.getMissionId(), relevantProxies);
                        } else if (distance < request.compareDistance) {
                            response = new QuantityLess(request.getId(), request.getMissionId(), relevantProxies);
                        } else {
                            response = new QuantityEqual(request.getId(), request.getMissionId(), relevantProxies);
                        }
                        responses.add(response);
                    }
                    numProxies++;
                }
            }
            if (numProxies == 0) {
                LOGGER.log(Level.WARNING, "Place with ProxyCompareDistanceRequest has no tokens with proxies attached: " + oe + ", tokens [" + tokens + "]");
            }

            for (GeneratedEventListenerInt listener : listeners) {
                for (InputEvent response : responses) {
                    LOGGER.log(Level.FINE, "\tSending response: " + response + " to listener: " + listener);
                    listener.eventGenerated(response);
                }
            }
        } else if (oe instanceof ProxyAbortMission) {
            for (Token token : tokens) {
                if (token.getProxy() != null) {
                    token.getProxy().abortMission(oe.getMissionId());
                }
            }
        } else if (oe instanceof ConnectExistingProxy) {
            // Connect to a non-simulated proxy
            ConnectExistingProxy connectEvent = (ConnectExistingProxy) oe;
            ProxyInt proxy = Engine.getInstance().getProxyServer().createNumberedProxy(connectEvent.name, connectEvent.color, Integer.parseInt(connectEvent.boatNo));
            
            ImagePanel.setImagesDirectory(connectEvent.imageStorageDirectory);
            if (proxy != null) {
                ProxyCreated proxyCreated = new ProxyCreated(oe.getId(), oe.getMissionId(), proxy);
                for (GeneratedEventListenerInt listener : listeners) {
                    listener.eventGenerated(proxyCreated);
                }
            } else {
                LOGGER.severe("Failed to connect proxy");
            }
        } else if (oe instanceof CreateSimulatedProxy) {
            CreateSimulatedProxy createEvent = (CreateSimulatedProxy) oe;
            String name = createEvent.name;
            Color color = createEvent.color;
            boolean error = false;
            boolean spoofData = createEvent.spoofData;
            ArrayList<ProxyInt> relevantProxyList = new ArrayList<ProxyInt>();
            ArrayList<String> proxyNames = new ArrayList<String>();
            ArrayList<ProxyInt> proxyList = Engine.getInstance().getProxyServer().getProxyListClone();
            for (ProxyInt proxy : proxyList) {
                proxyNames.add(proxy.getProxyName());
            }
            for (int i = 0; i < createEvent.numberToCreate; i++) {              
                // Start simulated GAMS server
                name = CoreHelper.getUniqueName(name, proxyNames);
                proxyNames.add(name);
                int boatNo = Engine.getInstance().getProxyServer().nextHighestBoatNo();
                ProxyInt proxy = Engine.getInstance().getProxyServer().createNumberedProxy(name, color, boatNo);
                
                // Space out multiple simulated boats by 1m
                Location spacedLocation = getSpacedLocation(createEvent.startLocation, i, 3);
                UTMCoordinate utmc = spacedLocation.getCoordinate();
                UtmPose p1 = new UtmPose(new Pose3D(utmc.getEasting(), utmc.getNorthing(), 0.0, 0.0, 0.0, 0.0), new Utm(utmc.getZoneNumber(), utmc.getHemisphere().equals(Hemisphere.NORTH)));
                UTMCoord utmCoord = Conversion.UtmPoseToUTMCoord(p1);
                
                if (proxy != null) {
                    relevantProxyList.add(proxy);
                    if (proxy instanceof BoatProxy) {                        
                        BoatProxy bp = (BoatProxy) proxy;                                                
                        //new Thread(new SimulatedGAMSBoat(boatNo, BoatProxy.DEFAULT_TEAM_SIZE, utmCoord, bp,spoofData)).start();
                    }
                } else {
                    LOGGER.severe("Failed to create simulated proxy");
                    error = true;
                }                                

                boatCounter++;
                portCounter++;
            }
            // Sleep to give time for processes to start
            try {
                Thread.sleep(1000);
            } catch (InterruptedException ex) {
                Logger.getLogger(ProxyEventHandler.class.getName()).log(Level.SEVERE, null, ex);
            }
            // After sleep, generated ProxyCreated event
            if (!error) {
                ProxyCreated proxyCreated = new ProxyCreated(oe.getId(), oe.getMissionId(), relevantProxyList);
                for (GeneratedEventListenerInt listener : listeners) {
                    listener.eventGenerated(proxyCreated);
                }
            }
        } else if (oe instanceof SetGains) {
            final SetGains setGains = (SetGains) oe;
            ArrayList<InputEvent> responses = new ArrayList<InputEvent>();

            int numBoatProxies = 0;
            ArrayList<ProxyInt> relevantProxies;
            for (Token token : tokens) {
                if (token.getProxy() != null && token.getProxy() instanceof BoatProxy) {
                    numBoatProxies++;

                    BoatProxy boatProxy = (BoatProxy) token.getProxy();
                    boatProxy.getVehicleServer().setGains(THRUST_GAINS_AXIS, new double[]{setGains.thrustP, setGains.thrustI, setGains.thrustD}, new FunctionObserver<Void>() {
                        public void completed(Void v) {
                            LOGGER.fine("Set thrust gains succeeded: Axis [" + THRUST_GAINS_AXIS + "] PID [" + setGains.thrustP + ", " + setGains.thrustI + ", " + setGains.thrustD + "]");
                        }

                        public void failed(FunctionObserver.FunctionError fe) {
                            LOGGER.severe("Set thrust gains failed: Axis [" + THRUST_GAINS_AXIS + "] PID [" + setGains.thrustP + ", " + setGains.thrustI + ", " + setGains.thrustD + "]");
                        }
                    });
                    boatProxy.getVehicleServer().setGains(RUDDER_GAINS_AXIS, new double[]{setGains.rudderP, setGains.rudderI, setGains.rudderD}, new FunctionObserver<Void>() {
                        public void completed(Void v) {
                            LOGGER.fine("Set rudder gains succeeded: Axis [" + RUDDER_GAINS_AXIS + "] PID [" + setGains.rudderP + ", " + setGains.rudderI + ", " + setGains.rudderD + "]");
                        }

                        public void failed(FunctionObserver.FunctionError fe) {
                            LOGGER.severe("Set rudder gains failed: Axis [" + RUDDER_GAINS_AXIS + "] PID [" + setGains.rudderP + ", " + setGains.rudderI + ", " + setGains.rudderD + "]");
                        }
                    });

                    //@todo add in recognition of success or failure
                    GainsSent gainsSent = new GainsSent(oe.getId(), oe.getMissionId(), boatProxy);
                    responses.add(gainsSent);
                }
            }
            if (numBoatProxies == 0) {
                LOGGER.log(Level.WARNING, "Place with SetGains has no tokens with boat proxies attached: " + oe + ", tokens [" + tokens + "]");
            }

            for (GeneratedEventListenerInt listener : listeners) {
                for (InputEvent response : responses) {
                    LOGGER.log(Level.FINE, "\tSending response: " + response + " to listener: " + listener);
                    listener.eventGenerated(response);
                }
            }
        } 
        else if (oe instanceof ProxyEndGAMSAlgorithm) {
            int numProxies = 0;     
            ArrayList<Token> tokensWithProxy = new ArrayList<Token>();
            ArrayList<BoatProxy> boatProxies = new ArrayList<BoatProxy>();
            for (Token token : tokens) {
                if (token.getProxy() != null && token.getProxy() instanceof BoatProxy) {
                    ((BoatProxy)token.getProxy()).endGAMSAlgorithm();
                }                
            }
            if (numProxies == 0) {
                LOGGER.log(Level.WARNING, "ProxyEndGAMSAlgorithm had no relevant proxies attached: " + oe);
            }                   
            
            ProxyEndsGAMSAlgorithm ie = new ProxyEndsGAMSAlgorithm(oe.getId(), oe.getMissionId());
            for (GeneratedEventListenerInt listener : listeners) {
                listener.eventGenerated(ie);
            }
        }
        
        else if (oe instanceof ProxyReHomeAutonomy) {
            int numProxies = 0;     
            ArrayList<Token> tokensWithProxy = new ArrayList<Token>();
            ArrayList<BoatProxy> boatProxies = new ArrayList<BoatProxy>();
            ProxyServerInt proxyServer = Engine.getInstance().getProxyServer();            
            for (Token token : tokens) {
                if (token.getProxy() != null && token.getProxy() instanceof BoatProxy) {
                    BoatProxy bp = (BoatProxy)token.getProxy();
                    bp.containers.setTeleopStatus(TELEOPERATION_TYPES.NONE);
                    //bp.containers.keepCurrentLocation();
                    //bp.containers.reHome();                    
                }                
            }            
            if (numProxies == 0) {
                LOGGER.log(Level.WARNING, "ProxyReHomeAutonomy had no relevant proxies attached: " + oe);
            }
            KnowledgeBase knowledge;
            if (proxyServer instanceof CrwProxyServer) {            
                knowledge = ((CrwProxyServer)proxyServer).knowledge;            
                knowledge.sendModifieds();
            }            
            
            ProxyAutonomyReHomed ie = new ProxyAutonomyReHomed(oe.getId(), oe.getMissionId());
            for (GeneratedEventListenerInt listener : listeners) {
                listener.eventGenerated(ie);
            }                        
        }
        
        else if (oe instanceof ProxyResetLocalization) {
            int numProxies = 0;     
            ArrayList<Token> tokensWithProxy = new ArrayList<Token>();
            ArrayList<BoatProxy> boatProxies = new ArrayList<BoatProxy>();
            for (Token token : tokens) {
                if (token.getProxy() != null && token.getProxy() instanceof BoatProxy) {
                    BoatProxy bp = (BoatProxy)token.getProxy();
                    bp.containers.resetLocalization();
                }                
            }
            if (numProxies == 0) {
                LOGGER.log(Level.WARNING, "ProxyEndGAMSAlgorithm had no relevant proxies attached: " + oe);
            }
            ProxyServerInt proxyServer = Engine.getInstance().getProxyServer();            
            KnowledgeBase knowledge;
            if (proxyServer instanceof CrwProxyServer) {            
                knowledge = ((CrwProxyServer)proxyServer).knowledge;            
                knowledge.sendModifieds();
            }                        
            
            ProxyResetsLocalization ie = new ProxyResetsLocalization(oe.getId(), oe.getMissionId());
            for (GeneratedEventListenerInt listener : listeners) {
                listener.eventGenerated(ie);
            }
        }
        
        else if (oe instanceof StopAllAgents) {
            ProxyServerInt proxyServer = Engine.getInstance().getProxyServer();            
            KnowledgeBase knowledge;
            if (proxyServer instanceof CrwProxyServer) {            
                CrwProxyServer crwProxyServer = (CrwProxyServer)proxyServer;
                knowledge = crwProxyServer.knowledge;
                ArrayList<ProxyInt> proxyList = crwProxyServer.getProxyListClone();
                for (int i = 0; i < proxyList.size(); i++) {
                    ProxyInt proxyCandidate = proxyList.get(i);
                    if (proxyCandidate instanceof BoatProxy) {
                        BoatProxy bp = (BoatProxy)proxyCandidate;
                        bp.beginTeleop();
                    }
                }
            }                        
            
            StoppedAllAgents ie = new StoppedAllAgents(oe.getId(), oe.getMissionId());
            for (GeneratedEventListenerInt listener : listeners) {
                listener.eventGenerated(ie);
            }            
        }
        
        else if (oe instanceof FormCylindricalFormation) {
            FormCylindricalFormation request = (FormCylindricalFormation) oe;
            int leaderNo = request.getLeaderNo();
            double spacing = request.getSpacing();
            java.lang.String groupName = request.getGroupName();         
            
            ProxyServerInt proxyServer = Engine.getInstance().getProxyServer();
            if (proxyServer instanceof CrwProxyServer) { 
                KnowledgeBase knowledge = ((CrwProxyServer)proxyServer).knowledge;
                // TODO - Need to fix FormCylindricalFormation
                /*
                EvalSettings delay = new EvalSettings();
                delay.setDelaySendingModifieds(true);
                StringVector groupMembersContainer = new StringVector();
                groupMembersContainer.setName(knowledge, String.format("group.%s.members",groupName));                                  
                long numMembers = knowledge.get(String.format("group.%s.members.size",groupName)).toLong();
                int numFollowers = (int)numMembers - 1;
                int followerCount = 0;
                double anglePerFollower = 2*Math.PI/numFollowers;
                double[] destination = new double[3];
                // need to get the leader's current location as the destination                
                NativeDoubleVector destNDV = new NativeDoubleVector();                
                destNDV.free();                                        
                knowledge.sendModifieds();                
                //knowledge.print();
                delay.free();     
                */
                
                GenericGAMSCommandSent ie = new GenericGAMSCommandSent(oe.getId(),oe.getMissionId());
                for (GeneratedEventListenerInt listener : listeners) {
                    listener.eventGenerated(ie);
                }
            }
        }        

        else if (oe instanceof FormationSyncMove) {
            FormationSyncMove request = (FormationSyncMove) oe;
            int uid = request.getUID();
            Location destination = request.getDestination();
            Position destinationPosition = Conversion.locationToPosition(destination);
            Location stagingPoint = request.getStagingPoint();
            Position stagingPosition = Conversion.locationToPosition(stagingPoint);
            double bufferDistance = request.getBufferDistance();
            String barrier = request.getBarrier();
            String formationType = request.getFormationType();             
            String groupName = request.getGroupName();

            ProxyServerInt proxyServer = Engine.getInstance().getProxyServer();
            if (proxyServer instanceof CrwProxyServer) { 
                KnowledgeBase knowledge = ((CrwProxyServer)proxyServer).knowledge;
                
                for (int resends = 0; resends < 10; resends++) {
                    EvalSettings delay = new EvalSettings();
                    delay.setDelaySendingModifieds(true);

                    // need to extract group members using the group name
                    //ArrayList<String> groupMembers = new ArrayList<>();
                    StringVector groupMembersContainer = new StringVector();
                    groupMembersContainer.setName(knowledge, String.format("group.%s.members",groupName));                
                    for (int i = 0; i < groupMembersContainer.size(); i++) {
                        //groupMembers.add(groupMembersContainer.get(i));
                        //Vector args = new Vector();
                        com.madara.containers.String command = new com.madara.containers.String();
                        command.setName(knowledge, groupMembersContainer.get(i) + ".algorithm");
                        command.set("formation sync");                    
                        command.free();

                        double[] latlon = new double[] {stagingPosition.latitude.degrees, stagingPosition.longitude.degrees};
                        knowledge.set(groupMembersContainer.get(i) + ".algorithm.args.start", latlon, delay);
                        latlon = new double[] {destinationPosition.latitude.degrees, destinationPosition.longitude.degrees};
                        knowledge.set(groupMembersContainer.get(i) + ".algorithm.args.end", latlon, delay);
                        knowledge.set(groupMembersContainer.get(i) + ".algorithm.args.formation", formationType, delay);
                        knowledge.set(groupMembersContainer.get(i) + ".algorithm.args.buffer", bufferDistance, delay);
                        knowledge.set(groupMembersContainer.get(i) + ".algorithm.args.group", groupName, delay);
                        knowledge.set(groupMembersContainer.get(i) + ".algorithm.args.barrier", barrier, delay);
                        knowledge.set(groupMembersContainer.get(i) + ".algorithm.id", uid, delay);
                    }
                    knowledge.sendModifieds();
                    delay.free();
                }
            }            
            
            GenericGAMSCommandSent ie = new GenericGAMSCommandSent(oe.getId(),oe.getMissionId());
            for (GeneratedEventListenerInt listener : listeners) {
                listener.eventGenerated(ie);
            }                        
        }
        
        else if (oe instanceof FormGroup) {
            FormGroup request = (FormGroup) oe;
            String groupName = request.getGroupName();

            int numProxies = 0;     
            ArrayList<String> groupMembers = new ArrayList<>();
            ArrayList<Token> tokensWithProxy = new ArrayList<Token>();
            ArrayList<BoatProxy> boatProxies = new ArrayList<BoatProxy>();
            for (Token token : tokens) {
                if (token.getProxy() != null && token.getProxy() instanceof BoatProxy) {
                    tokensWithProxy.add(token);
                    boatProxies.add((BoatProxy)token.getProxy());
                    numProxies++;
                    groupMembers.add(String.format("agent.%d",((BoatProxy)token.getProxy()).getBoatNo()));
                }                
            }
            if (numProxies == 0) {
                LOGGER.log(Level.WARNING, "FormGroup had no relevant proxies attached: " + oe);
            }                        
            
            if (groupMembers.size() > 0) {
                ProxyServerInt proxyServer = Engine.getInstance().getProxyServer();
                if (proxyServer instanceof CrwProxyServer) { 
                    KnowledgeBase knowledge = ((CrwProxyServer)proxyServer).knowledge;
                    
                    StringVector groupMembersContainer = new StringVector();
                    groupMembersContainer.setName(knowledge, String.format("group.%s.members",groupName));
                    groupMembersContainer.resize(groupMembers.size());
                    for (int i = 0; i < groupMembers.size(); i++) {
                        groupMembersContainer.set(i, groupMembers.get(i));
                    }
                    knowledge.sendModifieds();                
                    //knowledge.print();////////////////////////////////                
                    groupMembersContainer.free();
                }
            }
            
            GenericGAMSCommandSent ie = new GenericGAMSCommandSent(oe.getId(),oe.getMissionId());
            for (GeneratedEventListenerInt listener : listeners) {
                listener.eventGenerated(ie);
            }                                    
        }
        
        else if (oe instanceof GroupBarrierTest) {
            GroupBarrierTest request = (GroupBarrierTest)oe;
            String groupName = request.getGroupName();
            
            ProxyServerInt proxyServer = Engine.getInstance().getProxyServer();
            if (proxyServer instanceof CrwProxyServer) { 
                KnowledgeBase knowledge = ((CrwProxyServer)proxyServer).knowledge;
                EvalSettings delay = new EvalSettings();
                delay.setDelaySendingModifieds(true);
                StringVector groupMembersContainer = new StringVector();
                groupMembersContainer.setName(knowledge, String.format("group.%s.members",groupName));
                for (int i = 0; i < groupMembersContainer.size(); i++) {
                    knowledge.set(groupMembersContainer.get(i) + ".algorithm", "barrier", delay);
                    knowledge.set(groupMembersContainer.get(i) + ".algorithm.args.group", groupName, delay);
                }                
                knowledge.sendModifieds();
                delay.free();
                groupMembersContainer.free();                                                                  
            }
            GenericGAMSCommandSent ie = new GenericGAMSCommandSent(oe.getId(),oe.getMissionId());
            for (GeneratedEventListenerInt listener : listeners) {
                listener.eventGenerated(ie);
            }            
        }
        
        else if (oe instanceof ConnectProxyList) {
            // Connect to a non-simulated proxy
            ConnectProxyList connectEvent = (ConnectProxyList) oe;
            List<String> names = Arrays.asList(connectEvent.names.split("\\s*,\\s*"));
            List<String> ids = Arrays.asList(connectEvent.ids.split("\\s*,\\s*"));
            for(int i = 0; i < names.size(); i++)
            {    
                ProxyInt proxy = Engine.getInstance().getProxyServer().createNumberedProxy(names.get(i), connectEvent.colors.get(i%connectEvent.colors.size()), Integer.parseInt(ids.get(i)));

                ImagePanel.setImagesDirectory("not_a_directory");
                if (proxy != null) {
                    ProxyCreated proxyCreated = new ProxyCreated(oe.getId(), oe.getMissionId(), proxy);
                    for (GeneratedEventListenerInt listener : listeners) {
                        listener.eventGenerated(proxyCreated);
                    }
                } else {
                    LOGGER.severe("Failed to connect proxy " + ids.get(i));
                }
            }
        } 
        
        else if (oe instanceof ZoneCoverage) {
            ZoneCoverage request = (ZoneCoverage)oe;
            int uid = request.getUID();
            String defendersGroupName = request.getDefendersGroupName();
            String assetsGroupName = request.getAssetsGroupName();
            String enemiesGroupName = request.getEnemiesGroupName();
            String formationType = request.getFormationType();
            double bufferDistance = request.getBufferDistance();
            double distanceFraction = request.getDistanceFraction();
            
             ProxyServerInt proxyServer = Engine.getInstance().getProxyServer();
            if (proxyServer instanceof CrwProxyServer) { 
                KnowledgeBase knowledge = ((CrwProxyServer)proxyServer).knowledge;
                
                for (int resends = 0; resends < 10; resends++) {
                    EvalSettings delay = new EvalSettings();
                    delay.setDelaySendingModifieds(true);

                    // defenders group commands                
                    StringVector defendersGroupMembersContainer = new StringVector();
                    defendersGroupMembersContainer.setName(knowledge, String.format("group.%s.members",defendersGroupName));                
                    for (int i = 0; i < defendersGroupMembersContainer.size(); i++) {
                        knowledge.set(defendersGroupMembersContainer.get(i) + ".algorithm", "zone coverage", delay);
                        knowledge.set(defendersGroupMembersContainer.get(i) + ".algorithm.id", uid, delay);
                        knowledge.set(defendersGroupMembersContainer.get(i) + ".algorithm.args.assets", assetsGroupName, delay);
                        knowledge.set(defendersGroupMembersContainer.get(i) + ".algorithm.args.protectors", defendersGroupName, delay);
                        knowledge.set(defendersGroupMembersContainer.get(i) + ".algorithm.args.enemies", enemiesGroupName, delay);
                        knowledge.set(defendersGroupMembersContainer.get(i) + ".algorithm.args.buffer", bufferDistance, delay);
                        knowledge.set(defendersGroupMembersContainer.get(i) + ".algorithm.args.distance", distanceFraction, delay);
                        knowledge.set(defendersGroupMembersContainer.get(i) + ".algorithm.args.formation", formationType, delay);
                    }
                    defendersGroupMembersContainer.free();

                    // assets group commands                
                    StringVector assetsGroupMembersContainer = new StringVector();
                    assetsGroupMembersContainer.setName(knowledge, String.format("group.%s.members",assetsGroupName));                
                    for (int i = 0; i < assetsGroupMembersContainer.size(); i++) {
                        knowledge.set(assetsGroupMembersContainer.get(i) + ".algorithm", "zone coverage", delay);
                        knowledge.set(assetsGroupMembersContainer.get(i) + ".algorithm.id", uid, delay);
                        knowledge.set(assetsGroupMembersContainer.get(i) + ".algorithm.args.assets", assetsGroupName, delay);
                        knowledge.set(assetsGroupMembersContainer.get(i) + ".algorithm.args.protectors", defendersGroupName, delay);
                        knowledge.set(assetsGroupMembersContainer.get(i) + ".algorithm.args.enemies", enemiesGroupName, delay);
                        knowledge.set(assetsGroupMembersContainer.get(i) + ".algorithm.args.buffer", bufferDistance, delay);
                        knowledge.set(assetsGroupMembersContainer.get(i) + ".algorithm.args.distance", distanceFraction, delay);
                        knowledge.set(assetsGroupMembersContainer.get(i) + ".algorithm.args.formation", formationType, delay);
                    }
                    assetsGroupMembersContainer.free();

                    // enemies group commands                
                    StringVector enemiesGroupMembersContainer = new StringVector();
                    enemiesGroupMembersContainer.setName(knowledge, String.format("group.%s.members",enemiesGroupName));                
                    for (int i = 0; i < enemiesGroupMembersContainer.size(); i++) {
                        knowledge.set(enemiesGroupMembersContainer.get(i) + ".algorithm", "zone coverage", delay);
                        knowledge.set(enemiesGroupMembersContainer.get(i) + ".algorithm.id", uid, delay);
                        knowledge.set(enemiesGroupMembersContainer.get(i) + ".algorithm.args.assets", assetsGroupName, delay);
                        knowledge.set(enemiesGroupMembersContainer.get(i) + ".algorithm.args.protectors", defendersGroupName, delay);
                        knowledge.set(enemiesGroupMembersContainer.get(i) + ".algorithm.args.enemies", enemiesGroupName, delay);
                        knowledge.set(enemiesGroupMembersContainer.get(i) + ".algorithm.args.buffer", bufferDistance, delay);
                        knowledge.set(enemiesGroupMembersContainer.get(i) + ".algorithm.args.distance", distanceFraction, delay);
                        knowledge.set(enemiesGroupMembersContainer.get(i) + ".algorithm.args.formation", formationType, delay);
                    }                   
                    enemiesGroupMembersContainer.free();

                    knowledge.sendModifieds();
                    delay.free();
                }
                
                GenericGAMSCommandSent ie = new GenericGAMSCommandSent(oe.getId(),oe.getMissionId());
                for (GeneratedEventListenerInt listener : listeners) {
                    listener.eventGenerated(ie);
                }                  
            }
          
        }
        
        /*
        else if (oe instanceof) {
            
        }
        */
        
        else if (oe instanceof ProxyExecutePath
                || oe instanceof ProxyEmergencyAbort
                || oe instanceof ProxyResendWaypoints) {
            int numProxies = 0;
            for (Token token : tokens) {
                if (token.getProxy() != null && token.getProxy() instanceof BoatProxy) {
                    token.getProxy().handleEvent(oe, token.getTask());
                    numProxies++;
                }
            }
            if (numProxies == 0) {
                LOGGER.log(Level.WARNING, "Place with ProxyEventHandler OE has no tokens with proxies attached: " + oe);
            }
        }
    }

    @Override
    public boolean offer(GeneratedInputEventSubscription sub) {
        LOGGER.log(Level.FINE, "ProxyEventHandler offered subscription: " + sub);
        if (sub.getSubscriptionClass() == ProxyPathCompleted.class
                || sub.getSubscriptionClass() == ProxyPathFailed.class
                || sub.getSubscriptionClass() == ProxyCreated.class
                || sub.getSubscriptionClass() == AssembleLocationResponse.class
                || sub.getSubscriptionClass() == QuantityGreater.class
                || sub.getSubscriptionClass() == QuantityLess.class
                || sub.getSubscriptionClass() == QuantityEqual.class
                || sub.getSubscriptionClass() == ProxyPoseUpdated.class
                || sub.getSubscriptionClass() == SetGains.class
                || sub.getSubscriptionClass() == ProxyEndsGAMSAlgorithm.class
                || sub.getSubscriptionClass() == ProxyAutonomyReHomed.class
                || sub.getSubscriptionClass() == ProxyResetsLocalization.class
                || sub.getSubscriptionClass() == GenericGAMSCommandSent.class
                || sub.getSubscriptionClass() == StoppedAllAgents.class
                ) {
            LOGGER.log(Level.FINE, "\tProxyEventHandler taking subscription: " + sub);
            if (!listeners.contains(sub.getListener())) {
                LOGGER.log(Level.FINE, "\t\tProxyEventHandler adding listener: " + sub.getListener());
                listeners.add(sub.getListener());
                listenerGCCount.put(sub.getListener(), 1);
            } else {
                LOGGER.log(Level.FINE, "\t\tProxyEventHandler incrementing listener: " + sub.getListener());
                listenerGCCount.put(sub.getListener(), listenerGCCount.get(sub.getListener()) + 1);
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean cancel(GeneratedInputEventSubscription sub) {
        LOGGER.log(Level.FINE, "ProxyEventHandler asked to cancel subscription: " + sub);
        if ((sub.getSubscriptionClass() == ProxyPathCompleted.class
                || sub.getSubscriptionClass() == ProxyPathFailed.class
                || sub.getSubscriptionClass() == ProxyCreated.class
                || sub.getSubscriptionClass() == AssembleLocationResponse.class
                || sub.getSubscriptionClass() == QuantityGreater.class
                || sub.getSubscriptionClass() == QuantityLess.class
                || sub.getSubscriptionClass() == QuantityEqual.class
                || sub.getSubscriptionClass() == ProxyPoseUpdated.class
                || sub.getSubscriptionClass() == SetGains.class
                || sub.getSubscriptionClass() == ProxyEndsGAMSAlgorithm.class
                || sub.getSubscriptionClass() == ProxyAutonomyReHomed.class
                || sub.getSubscriptionClass() == ProxyResetsLocalization.class
                || sub.getSubscriptionClass() == GenericGAMSCommandSent.class
                || sub.getSubscriptionClass() == StoppedAllAgents.class
                )
                && listeners.contains(sub.getListener())) {
            LOGGER.log(Level.FINE, "\tProxyEventHandler canceling subscription: " + sub);
            if (listenerGCCount.get(sub.getListener()) == 1) {
                // Remove listener
                LOGGER.log(Level.FINE, "\t\tProxyEventHandler removing listener: " + sub.getListener());
                listeners.remove(sub.getListener());
                listenerGCCount.remove(sub.getListener());
            } else {
                // Decrement garbage colleciton count
                LOGGER.log(Level.FINE, "\t\tProxyEventHandler decrementing listener: " + sub.getListener());
                listenerGCCount.put(sub.getListener(), listenerGCCount.get(sub.getListener()) - 1);
            }
            return true;
        }
        return false;
    }

    @Override
    public void eventOccurred(InputEvent proxyEventGenerated) {
        LOGGER.log(Level.FINE, "Event occurred: " + proxyEventGenerated + ", rp: " + proxyEventGenerated.getRelevantProxyList() + ", listeners: " + listeners);
        for (GeneratedEventListenerInt listener : listeners) {
            listener.eventGenerated(proxyEventGenerated);
        }
    }

    @Override
    public void poseUpdated() {
//        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void waypointsUpdated() {
//        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void waypointsComplete() {
//        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public void proxyAdded(ProxyInt p) {
        p.addListener(this);
    }

    @Override
    public void proxyRemoved(ProxyInt p) {
    }

    private Object[] getLawnmowerPath(Polygon area, double stepSize) {
        // Compute the bounding box
        Angle minLat = Angle.POS360;
        Angle maxLat = Angle.NEG360;
        Angle minLon = Angle.POS360;
        Angle maxLon = Angle.NEG360;
        Angle curLat = null;
        for (LatLon latLon : area.getOuterBoundary()) {
            if (latLon.latitude.degrees > maxLat.degrees) {
                maxLat = latLon.latitude;
            } else if (latLon.latitude.degrees < minLat.degrees) {
                minLat = latLon.latitude;
            }
            if (latLon.longitude.degrees > maxLon.degrees) {
                maxLon = latLon.longitude;
            } else if (latLon.longitude.degrees < minLon.degrees) {
                minLon = latLon.longitude;
            }
        }
        curLat = minLat;

        double totalLength = 0.0;
        Angle leftLon = null, rightLon = null;
        ArrayList<Position> path = new ArrayList<Position>();
        while (curLat.degrees <= maxLat.degrees) {
            // Left to right
            leftLon = getMinLonAt(area, minLon, maxLon, curLat);
            rightLon = getMaxLonAt(area, minLon, maxLon, curLat);
            if (leftLon != null && rightLon != null) {
                path.add(new Position(new LatLon(curLat, leftLon), 0.0));
                path.add(new Position(new LatLon(curLat, rightLon), 0.0));
                totalLength += Math.abs((rightLon.degrees - leftLon.degrees) * LON_D_PER_M);
            } else {
            }
            // Right to left
            curLat = curLat.addDegrees(stepSize);
            if (curLat.degrees <= maxLat.degrees) {
                totalLength += stepSize;
                rightLon = getMaxLonAt(area, minLon, maxLon, curLat);
                leftLon = getMinLonAt(area, minLon, maxLon, curLat);
                if (leftLon != null && rightLon != null) {
                    path.add(new Position(new LatLon(curLat, rightLon), 0.0));
                    path.add(new Position(new LatLon(curLat, leftLon), 0.0));
                    totalLength += Math.abs((rightLon.degrees - leftLon.degrees) * LON_D_PER_M);
                } else {
                }
            }
            curLat = curLat.addDegrees(stepSize);
            if (curLat.degrees <= maxLat.degrees) {
                totalLength += stepSize;
            }
        }

        return new Object[]{path, totalLength};
    }

    private static Angle getMinLonAt(Polygon area, Angle minLon, Angle maxLon, Angle lat) {
        final double lonDiff = 1.0 / 90000.0 * 10.0;
        LatLon latLon = new LatLon(lat, minLon);
        while (!isLocationInside(latLon, (ArrayList<LatLon>) area.getOuterBoundary()) && minLon.degrees <= maxLon.degrees) {
            minLon = minLon.addDegrees(lonDiff);
            latLon = new LatLon(lat, minLon);
            if (minLon.degrees > maxLon.degrees) {
                // Overshot (this part of the area is tiny), so ignore it by returning null
                return null;
            }
        }
        return minLon;
    }

    private static Angle getMaxLonAt(Polygon area, Angle minLon, Angle maxLon, Angle lat) {
        final double lonDiff = 1.0 / 90000.0 * 10.0;
        LatLon latLon = new LatLon(lat, maxLon);
        while (!isLocationInside(latLon, (ArrayList<LatLon>) area.getOuterBoundary())) {
            maxLon = maxLon.addDegrees(-lonDiff);
            latLon = new LatLon(lat, maxLon);
            if (maxLon.degrees < minLon.degrees) {
                // Overshot (this part of the area is tiny), so ignore it by returning null
                return null;
            }
        }
        return maxLon;
    }

    /**
     * From: http://forum.worldwindcentral.com/showthread.php?t=20739
     *
     * @param point
     * @param positions
     * @return
     */
    public static boolean isLocationInside(LatLon point, ArrayList<? extends LatLon> positions) {
        boolean result = false;
        LatLon p1 = positions.get(0);
        for (int i = 1; i < positions.size(); i++) {
            LatLon p2 = positions.get(i);

            if (((p2.getLatitude().degrees <= point.getLatitude().degrees
                    && point.getLatitude().degrees < p1.getLatitude().degrees)
                    || (p1.getLatitude().degrees <= point.getLatitude().degrees
                    && point.getLatitude().degrees < p2.getLatitude().degrees))
                    && (point.getLongitude().degrees < (p1.getLongitude().degrees - p2.getLongitude().degrees)
                    * (point.getLatitude().degrees - p2.getLatitude().degrees)
                    / (p1.getLatitude().degrees - p2.getLatitude().degrees) + p2.getLongitude().degrees)) {
                result = !result;
            }
            p1 = p2;
        }
        return result;
    }

    private Location getSpacedLocation(Location centerLocation, int assembleCounter, double spacing) {
        Location spacedLocation;
        if (assembleCounter == 0) {
            spacedLocation = centerLocation;
        } else {
            int direction = (assembleCounter - 1) % 8;
            int magnitude = (assembleCounter - 1) / 8 + 1;
            UTMCoordinate centerCoord = centerLocation.getCoordinate();
            UTMCoordinate proxyCoord = new UTMCoordinate(centerCoord.getNorthing(), centerCoord.getEasting(), centerCoord.getZone());
            switch (direction) {
                case 0:
                    //  0: N
                    proxyCoord.setNorthing(centerCoord.getNorthing() + magnitude * spacing);
                    break;
                case 1:
                    //  1: NE
                    proxyCoord.setNorthing(centerCoord.getNorthing() + magnitude * spacing);
                    proxyCoord.setEasting(centerCoord.getEasting() + magnitude * spacing);
                    break;
                case 2:
                    //  2: E
                    proxyCoord.setEasting(centerCoord.getEasting() + magnitude * spacing);
                    break;
                case 3:
                    //  3: SE
                    proxyCoord.setNorthing(centerCoord.getNorthing() - magnitude * spacing);
                    proxyCoord.setEasting(centerCoord.getEasting() + magnitude * spacing);
                    break;
                case 4:
                    //  4: S
                    proxyCoord.setNorthing(centerCoord.getNorthing() - magnitude * spacing);
                    break;
                case 5:
                    //  5: SW
                    proxyCoord.setNorthing(centerCoord.getNorthing() - magnitude * spacing);
                    proxyCoord.setEasting(centerCoord.getEasting() - magnitude * spacing);
                    break;
                case 6:
                    //  6: W
                    proxyCoord.setEasting(centerCoord.getEasting() - magnitude * spacing);
                    break;
                case 7:
                    //  7: NW
                    proxyCoord.setNorthing(centerCoord.getNorthing() + magnitude * spacing);
                    proxyCoord.setEasting(centerCoord.getEasting() - magnitude * spacing);
                    break;
            }
            spacedLocation = new Location(proxyCoord, centerLocation.getAltitude());
        }
        return spacedLocation;
    }
}
