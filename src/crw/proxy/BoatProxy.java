package crw.proxy;

import com.madara.EvalSettings;
import com.madara.KnowledgeBase;
import com.madara.KnowledgeRecord;
import com.madara.containers.NativeDoubleVector;
import com.madara.threads.Threader;
import com.madara.threads.BaseThread;

import com.perc.mitpas.adi.mission.planning.task.Task;
import crw.Conversion;
import crw.event.input.proxy.ProxyPathCompleted;
import crw.event.input.proxy.ProxyPoseUpdated;
import crw.event.output.proxy.ProxyEmergencyAbort;
import crw.event.output.proxy.ProxyExecutePath;
import crw.event.output.proxy.ProxyGotoPoint;
import crw.event.output.proxy.ProxyResendWaypoints;
import crw.sensor.CrwObserverServer;
import edu.cmu.ri.crw.AsyncVehicleServer;
import edu.cmu.ri.crw.FunctionObserver;
import edu.cmu.ri.crw.FunctionObserver.FunctionError;
import edu.cmu.ri.crw.ImageListener;
import edu.cmu.ri.crw.PoseListener;
import edu.cmu.ri.crw.SensorListener;
import edu.cmu.ri.crw.WaypointListener;
import edu.cmu.ri.crw.data.Utm;
import edu.cmu.ri.crw.data.UtmPose;
import edu.cmu.ri.crw.udp.UdpVehicleServer;
import gov.nasa.worldwind.avlist.AVKey;
import gov.nasa.worldwind.geom.Angle;
import gov.nasa.worldwind.geom.LatLon;
import gov.nasa.worldwind.geom.Position;
import gov.nasa.worldwind.geom.coords.UTMCoord;
import java.awt.Color;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.Timer;
import robotutils.Pose3D;
import sami.engine.Engine;
import sami.engine.PlanManager;
import sami.event.InputEvent;
import sami.event.OutputEvent;
import sami.event.TaskComplete;
import sami.event.TaskDelayed;
import sami.event.TaskReleased;
import sami.event.TaskStarted;
import sami.path.Location;
import sami.path.PathUtm;
import sami.proxy.ProxyInt;
import sami.proxy.ProxyListenerInt;

/**
 * @todo Need a flag for autonomous or under human control
 *
 * @author pscerri
 */
public class BoatProxy extends Thread implements ProxyInt {

    private static final Logger LOGGER = Logger.getLogger(BoatProxy.class.getName());
    final int MIN_TIME_BTW_CMDS = 1000; // Required time between sending waypoint update commands to avoid unexpected server behavior (ms)
    // InputEvent generation rates
    final int EVENT_GENERATION_TIMER = 500; // ms

    public static final int NUM_SENSOR_PORTS = 4;
    // Identifiers
    int _boatNo;
    private final String name;
    private Color color = null;
    // SAMI variables
    // OutputEvents that must occur sequentially (require movement)
    protected OutputEvent curSequentialEvent = null;
    final ArrayList<OutputEvent> sequentialOutputEvents = new ArrayList<OutputEvent>();
    // The resulting InputEvents from sequentialOutputEvents
    final ArrayList<InputEvent> sequentialInputEvents = new ArrayList<InputEvent>();
    // The ordered list of tasks the proxy has been assigned
    final ArrayList<Task> taskList = new ArrayList<Task>();
    Task currentTask = null;
    HashMap<Task, OutputEvent> taskToLastOe = new HashMap<Task, OutputEvent>();
    HashMap<OutputEvent, Task> oeToTask = new HashMap<OutputEvent, Task>();
    protected ArrayList<ProxyListenerInt> listeners = new ArrayList<ProxyListenerInt>();
    protected Hashtable<ProxyListenerInt, Integer> listenerCounter = new Hashtable<ProxyListenerInt, Integer>();
    
    PoseListener _stateListener;
    SensorListener _sensorListener;
    WaypointListener _waypointListener;
    //@todo need to consolidate all these different pose representations
    UtmPose _pose;
    private Position position = null;
    //private UTMCoord utmCoord = null;
    private Location location = null;
    private BufferedImage latestImg = null;
    // Waypoints
    final Queue<Position> _curWaypoints = new LinkedList<Position>();
    final Queue<Position> _futureWaypoints = new LinkedList<Position>();
    
    Iterable<Position> _curWaypointsPos = null;
    Iterable<Position> _futureWaypointsPos = null;
    // FunctionObserver variables
    final AtomicBoolean autonomyActive = new AtomicBoolean(false);
    // InputEvent generation
    final AtomicBoolean sendEvent = new AtomicBoolean(true);
    long lastTime = -1;
    // IP address
    //private final InetSocketAddress address;
    //private final String ipAddress;
    // MADARA KB and containers
    public static final int DEFAULT_TEAM_SIZE = 1;
    KnowledgeBase knowledge;
    final BoatProxy bp;
    
    public LutraMadaraContainers containers;
    Threader madaraListenerThreader;    
    double[] errorEllipse;
    
    static final int MADARA_POSE_UPDATE_RATE = 10; // Hz
    static final int MADARA_WP_UPDATE_RATE = 4; // Hz
    static final int MADARA_CONNECTIVITY_WATCHDOG_RATE = 1; // Hz
    static final int MINIMUM_SAFE_WIFI_SIGNAL_STRENGTH = -75; // dBi
    static final int GPS_WATCHDOG_RATE = 1; // Hz

    // End stuff for simulated data creation
    //public BoatProxy(final String name, Color color, final int boatNo, InetSocketAddress addr, KnowledgeBase knowledge) {
    public BoatProxy(final String name, Color color, final int boatNo, KnowledgeBase knowledge) {
        this._boatNo = boatNo;
        //this.address = addr;
        this.knowledge = knowledge;

        containers = new LutraMadaraContainers(knowledge, boatNo);
        
        madaraListenerThreader = new Threader(knowledge);
        startListeners();

        String message = "Boat proxy created with name: " + name + ", color: " + color;
        LOGGER.info(message);

        Timer stateTimer = new Timer(EVENT_GENERATION_TIMER, new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent ae) {
                sendEvent.set(true);
            }
        });
        stateTimer.start();

        // this.masterURI = masterURI;
        this.name = name;
        this.color = color;

        bp = this;
        
        LOGGER.info("New boat created, boat # " + _boatNo);

        for (int i = 0; i < NUM_SENSOR_PORTS; i++) {
            //((CrwObserverServer) Engine.getInstance().getObserverServer()).createObserver(this, i);  ////////////////////////////////// do we need sensor observers anymore?
        }

        // startCamera();
    }
    
    
    public void shutdown() {
        madaraListenerThreader.free();
        containers.freeAll();
        knowledge.free();
    }
    
    public boolean isTeleop() {
        return ((int)containers.teleopStatus.get() > 0);
    }    
    
    public String getIpAddress() {
        //return ipAddress;
        return "IHaveNoIpAddress";
    }
    
    public KnowledgeBase getKnowledgeBase() {
        return knowledge;
    }
    
    public void sendMadaraModifieds() {
        knowledge.sendModifieds();
    }
    
    void sendWaypointsQueue() {
        int N = _curWaypoints.size();
        EvalSettings delay = new EvalSettings();
        delay.setDelaySendingModifieds(true);
        knowledge.set(containers.prefix + "command","waypoints",delay); // note the delay
        knowledge.set(containers.prefix + "command.size", N,delay);        
        
        Position[] wps = _curWaypoints.toArray(new Position[N]);
        for (int i = 0; i < N; i++) {                                    
            //UTMCoord utmCoordTemp = Conversion.UtmPoseToUTMCoord(utmWaypoints[i]);
            //double lat = utmCoordTemp.getLatitude().degrees;
            //double lon = utmCoordTemp.getLongitude().degrees;            
            double lat = wps[i].latitude.degrees;            
            double lon = wps[i].longitude.degrees;
            
            // TODO: switch to DoubleVector
            NativeDoubleVector wpNDV = new NativeDoubleVector();
            wpNDV.setName(knowledge, java.lang.String.format("%scommand.%d",containers.prefix,i));
            wpNDV.resize(3);            
            wpNDV.set(0,lat);
            wpNDV.set(1,lon);
            wpNDV.set(2,0.0); // altitude
            wpNDV.free();
        }      
        knowledge.sendModifieds();
        delay.free();
    }
    
    public void endGAMSAlgorithm() {        
        containers.keepCurrentLocation();
        try {
            Thread.sleep(100);
        } catch (InterruptedException ex) {
            //
        }        
        knowledge.set(containers.prefix + "command", "null"); // this must happen AFTER at least one call from the GAMS algorithm to the platform's move()
    }    

    public void startListeners() {
        madaraListenerThreader.run(MADARA_POSE_UPDATE_RATE,"poseListener", new MadaraPoseListener());
        madaraListenerThreader.run(MADARA_WP_UPDATE_RATE,"wpListener", new MadaraWaypointListener());        
        madaraListenerThreader.run(MADARA_CONNECTIVITY_WATCHDOG_RATE, "wifiWatchdog", new MadaraConnectivityWatchdogListener());
        madaraListenerThreader.run(GPS_WATCHDOG_RATE, "gpsWatchdog", new GPSWatchdogListener());
    }

    public AsyncVehicleServer getServer() {
        //return _server;
        return null;
    }

    @Override
    public int getProxyId() {
        return _boatNo;
    }

    @Override
    public String getProxyName() {
        return name;
    }

    @Override
    public void addListener(ProxyListenerInt l) {
        if (!listenerCounter.containsKey(l)) {
            LOGGER.log(Level.FINE, "First addition of listener " + l);
            listenerCounter.put(l, 1);
            listeners.add(l);
        } else {
            LOGGER.log(Level.FINE, "Count is now " + (listenerCounter.get(l) + 1) + " for " + l);
            listenerCounter.put(l, listenerCounter.get(l) + 1);
        }
    }

    @Override
    public void removeListener(ProxyListenerInt l) {
        if (!listenerCounter.containsKey(l)) {
            LOGGER.log(Level.WARNING, "Tried to remove ProxyListener that is not in the list!" + l);
        } else if (listenerCounter.get(l) == 1) {
            LOGGER.log(Level.FINE, "Last count of listener, removing " + l);
            listenerCounter.remove(l);
            listeners.remove(l);
        } else {
            LOGGER.log(Level.FINE, listenerCounter.get(l) - 1 + " counts remaining for " + l);
            listenerCounter.put(l, listenerCounter.get(l) - 1);
        }
    }

    @Override
    public void run() {
        // startCamera();
    }

    @Override
    public void handleEvent(OutputEvent oe, Task task) {
        if (task != null && task != currentTask) {
            LOGGER.severe("Proxy [" + this + "] asked to handle event [" + oe + "] for a task [" + task + "] that is not the current task [" + currentTask + "] - ignoring");
            return;
        } else {
            LOGGER.fine("Proxy [" + this + "] asked to handle event [" + oe + "] for task [" + task + "], current task is [" + currentTask + "]");
        }

        int index;
        if (task != null && taskToLastOe.containsKey(task)) {
            // This is an OE for the currently executing task; append after last event for this task
            LOGGER.fine("\tAppending task event after last OE for the task");
            index = sequentialOutputEvents.indexOf(taskToLastOe.get(task)) + 1;

            // What if not in seq OE? Should be ok bec result is -1 + 1
            taskToLastOe.put(task, oe);
        } else if (task != null) {
            // This is the first OE for the currently executing task; start it now
            LOGGER.fine("\tFirst OE for this task");
            index = 0;
            taskToLastOe.put(task, oe);
        } else {
            // If there is no task associated with the OE, put it at the end of the list
            LOGGER.fine("\tAppending no-task event to end of OE list");
            index = sequentialOutputEvents.size();
        }
        oeToTask.put(oe, task);

        LOGGER.fine("\t\tAdding to index [" + index + "] in list of " + sequentialOutputEvents.size());

        // If we have a current task but the front OE does not correspond to it, don't do it
        //  If this happens, the front OE should be a no-task
        //  Unless it is a ProxyResendWaypoints or ProxyEmergencyAbort event
        boolean sendWps = false;
        if (oe instanceof ProxyExecutePath) {
            sequentialOutputEvents.add(index, oe);
            sequentialInputEvents.add(index, new ProxyPathCompleted(oe.getId(), oe.getMissionId(), this));
        } else if (oe instanceof ProxyGotoPoint) {
            sequentialOutputEvents.add(index, oe);
            sequentialInputEvents.add(index, new ProxyPathCompleted(oe.getId(), oe.getMissionId(), this));
        } else if (oe instanceof ProxyEmergencyAbort) {
            // Clear out all events and stop
            LOGGER.severe("Handling ProxyEmergencyAbort! sequentialOutputEvents were: " + sequentialOutputEvents + ", sequentialInputEvents were: " + sequentialInputEvents);
            sequentialOutputEvents.clear();
            sequentialInputEvents.clear();
            sendWps = true;
        } else if (oe instanceof ProxyResendWaypoints) {
            sendWps = true;
        } else if (oe instanceof TaskComplete) {
            if (task == currentTask && currentTask == taskList.get(0)) {
                // Update task list and currentTask
                taskList.remove(0);
                if (!taskList.isEmpty()) {
                    // If there is a next task, start it
                    currentTask = taskList.get(0);
                    PlanManager pm = Engine.getInstance().getPlanManager(currentTask);
                    pm.eventGenerated(new TaskStarted(pm.missionId, currentTask));
                } else {
                    // Otherwise set currentTask to null so non-task OEs will run
                    currentTask = null;
                }
                sendWps = true;
            } else if (task != currentTask) {
                LOGGER.severe("Got TaskComplete event for task [" + task + "], but current task is [" + currentTask + "]");
            } else if (currentTask == taskList.get(0)) {
                LOGGER.severe("Got TaskComplete event for task [" + task + "], but currentTask [" + currentTask + "] does not match first task [" + taskList.get(0) + "]");
            }
        } else {
            LOGGER.severe("Can't handle OutputEvent of class " + oe.getClass().getSimpleName());
        }

        // Update proxy's waypoints
        updateWaypoints(true, true);

        /// not sure about this check  - what about index 0 with non matching task
        if (sequentialOutputEvents.size() == 1
                || index == 0
                || sendWps) {
            // Send waypoints if we modified the first set of waypoints or want to resend them due to spotty comms
            LOGGER.fine("Sending waypoints to finish handling event");
            sendCurrentWaypoints();
        }

        LOGGER.fine("After handle event: sequentialOutputEvents [" + sequentialOutputEvents.toString() + "], curSequentialEvent [" + curSequentialEvent + "], taskList [" + taskList.toString() + "], currentTask [" + currentTask + "]");
    }

    @Override
    public OutputEvent getCurrentEvent() {
        return curSequentialEvent;
    }

    @Override
    public ArrayList<OutputEvent> getEvents() {
        return sequentialOutputEvents;
    }

    @Override
    public void abortEvent(UUID eventId) {
        LOGGER.info("Abort Event called with event id [" + eventId + "] on proxy [" + toString() + "]");
        int numRemoved = 0;
        ArrayList<OutputEvent> outputEventsToRemove = new ArrayList<OutputEvent>();
        ArrayList<InputEvent> inputEventsToRemove = new ArrayList<InputEvent>();
        boolean removeFirst = false, removedEvent = false;
        for (int i = 0; i < sequentialOutputEvents.size(); i++) {
            if (sequentialOutputEvents.get(i).getId() == null) {
                LOGGER.warning("Output event [" + sequentialOutputEvents.get(i) + "] on proxy [" + toString() + "] has no event id!");
            } else if (sequentialOutputEvents.get(i).getId().equals(eventId)) {
                outputEventsToRemove.add(sequentialOutputEvents.get(i));
                inputEventsToRemove.add(sequentialInputEvents.get(i));
                if (i == 0) {
                    // We need to remove the currently executing task
                    removeFirst = true;
                }
                numRemoved++;
            }
        }
        for (OutputEvent outputEvent : outputEventsToRemove) {
            sequentialOutputEvents.remove(outputEvent);
        }
        for (InputEvent inputEvent : inputEventsToRemove) {
            sequentialInputEvents.remove(inputEvent);
        }

        updateWaypoints(removeFirst, numRemoved > 0);
        if (removeFirst) {
            // If we modified the first set of waypoints, start them
            LOGGER.info("\t Removed " + numRemoved + " events while aborting eventId [" + eventId + "] on proxy [" + toString() + "], including current event");
            sendCurrentWaypoints();
        } else {
            LOGGER.info("\t Removed " + numRemoved + " events while aborting eventId [" + eventId + "] on proxy [" + toString() + "], but not the current event");
        }
    }

    @Override
    public void addChildTask(Task parentTask, Task childTask) {
        // Add child task immediately after parent task
        if (!taskList.contains(childTask)) {
            if (taskList.contains(parentTask)) {
                int index = taskList.indexOf(parentTask);
                taskList.add(index + 1, childTask);
            } else {
                LOGGER.warning("Tried to add child task " + childTask + " to proxy " + toString() + ". but parent task " + parentTask + " is not in task list");
            }
        } else {
            LOGGER.warning("Tried to add child task " + childTask + " to proxy " + toString() + " twice");
        }
    }

    @Override
    public Task getCurrentTask() {
        return currentTask;
    }

    @Override
    public ArrayList<Task> getTasks() {
        return (ArrayList<Task>) taskList.clone();
    }

    @Override
    public ArrayList<InputEvent> setTasks(ArrayList<Task> tasks) {
        LOGGER.fine("Set tasks for proxy [" + this + "] from [" + taskList.toString() + "] to [" + tasks.toString() + "]");
        // Create lists for TaskDelayed and TaskReleased events
        ArrayList<InputEvent> taskEvents = new ArrayList<InputEvent>();
        // TaskDelayed
        if (currentTask != null
                && !tasks.isEmpty()
                && currentTask != tasks.get(0)
                && tasks.contains(currentTask)) {
            // The current task is being pushed back in the task order
            LOGGER.fine("Proxy [" + this + "] interrupted first task");
            PlanManager pm = Engine.getInstance().getPlanManager(currentTask);
            taskEvents.add(new TaskDelayed(pm.missionId, currentTask));
        }
        // TaskReleased
        for (Task task : taskList) {
            if (!tasks.contains(task)) {
                LOGGER.fine("Proxy [" + this + "] adding TaskReleased event for task [" + task + "]");
                PlanManager pm = Engine.getInstance().getPlanManager(task);
                taskEvents.add(new TaskReleased(pm.missionId, this, task));
            }
        }
        // Check if we have a new current task we should send begin executing
        boolean changedFirstTask = false;
        if ((tasks.isEmpty() && currentTask != null)
                || (!tasks.isEmpty() && currentTask != tasks.get(0))) {
            LOGGER.fine("Proxy [" + this + "] changed first task");
            changedFirstTask = true;
            currentTask = tasks.get(0);
        }
        // Update waypoint list
        taskList.clear();
        taskList.addAll(tasks);

        // update current task!
        if (changedFirstTask) {
            // Should stop the boat unless it has no tasks, but has no-task events
            updateWaypoints(true, true);
            sendCurrentWaypoints();
            PlanManager pm = Engine.getInstance().getPlanManager(currentTask);
            taskEvents.add(new TaskStarted(pm.missionId, currentTask));
        } else {
            updateWaypoints(false, true);
        }

        LOGGER.fine("After set tasks: sequentialOutputEvents [" + sequentialOutputEvents.toString() + "], curSequentialEvent [" + curSequentialEvent + "], taskList [" + taskList.toString() + "], currentTask [" + currentTask + "]");
        LOGGER.fine("After set tasks: returning taskEvent [" + taskEvents + "]");
        return taskEvents;
    }

    @Override
    public void taskCompleted(Task task) {
        if (currentTask != task) {
            LOGGER.severe("Received task complete for [" + task + "], but the current task is [" + currentTask + "]");
            return;
        }
        // Consume completed task
        taskList.remove(0);
        Engine.getInstance().taskCompleted(task);

        // Update current task
        if (!taskList.isEmpty()) {
            currentTask = taskList.get(0);
            // Send out a Task Started event
            PlanManager pm = Engine.getInstance().getPlanManager(currentTask);
            pm.eventGenerated(new TaskStarted(pm.missionId, currentTask));

            // Update proxy's waypoints
            updateWaypoints(true, true);
            sendCurrentWaypoints();
        } else {
            currentTask = null;
        }

        //@todo What if we have delayed this and already have events for this task????
    }

    @Override
    public void abortMission(UUID missionId) {
        LOGGER.info("Abort Mission called with mission id [" + missionId + "] on proxy [" + toString() + "]");
        int numRemoved = 0;
        ArrayList<OutputEvent> outputEventsToRemove = new ArrayList<OutputEvent>();
        ArrayList<InputEvent> inputEventsToRemove = new ArrayList<InputEvent>();
        boolean removeFirst = false, removedEvent = false;
        for (int i = 0; i < sequentialOutputEvents.size(); i++) {
            if (sequentialOutputEvents.get(i).getMissionId() == null) {
                LOGGER.warning("Output event [" + sequentialOutputEvents.get(i) + "] on proxy [" + toString() + "] has no mission id!");
            } else if (sequentialOutputEvents.get(i).getMissionId().equals(missionId)) {
                outputEventsToRemove.add(sequentialOutputEvents.get(i));
                inputEventsToRemove.add(sequentialInputEvents.get(i));
                if (i == 0) {
                    // We need to remove the currently executing task
                    removeFirst = true;
                }
                numRemoved++;
            }
        }
        for (OutputEvent outputEvent : outputEventsToRemove) {
            sequentialOutputEvents.remove(outputEvent);
        }
        for (InputEvent inputEvent : inputEventsToRemove) {
            sequentialInputEvents.remove(inputEvent);
        }

        updateWaypoints(removeFirst, removedEvent);
        if (removeFirst) {
            // If we modified the first set of waypoints, start them
            LOGGER.info("Removed " + numRemoved + " events while aborting missionId [" + missionId + "] on proxy [" + toString() + "], including current event");
            sendCurrentWaypoints();
        } else {
            LOGGER.info("Removed " + numRemoved + " events while aborting missionId [" + missionId + "] on proxy [" + toString() + "], but not the current event");
        }
    }

    public OutputEvent getCurSequentialEvent() {
        return curSequentialEvent;
    }

    public int getBoatNo() {
        return _boatNo;
    }

    public UtmPose getUtmPose() {
        return _pose;
    }

    public Position getPosition() {
        return position;
    }

    public Location getLocation() {
        return location;
    }
    
    public double[] getErrorEllipse() {
        return errorEllipse;
    }

    //public Queue<UtmPose> getCurrentWaypoints() {
    public Queue<Position> getCurrentWaypoints() {
        return _curWaypoints;
    }

    public Iterable<Position> getCurrentWaypointsAsPositions() {
        return _curWaypointsPos;
    }

    //public Queue<UtmPose> getFutureWaypoints() {
    public Queue<Position> getFutureWaypoints() {
        return _futureWaypoints;
    }

    public Iterable<Position> getFutureWaypointsAsPositions() {
        return _futureWaypointsPos;
    }

    public void addImageListener(ImageListener l) {
//        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void addSensorListener(int channel, SensorListener l) {
//        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void addPoseListener(PoseListener l) {
//        throw new UnsupportedOperationException("Not supported yet.");
    }

    public void addWaypointListener(WaypointListener l) {
//        throw new UnsupportedOperationException("Not supported yet.");
    }

    /**
     * Updates the variables and lists used to execute and visualize current and
     * future waypoints from the output event list. Note this does not actually
     * send waypoints to the server
     *
     * @param updateCurrent Whether to update the current event and waypoint
     * list
     * @param updateFuture Whether to update the waypoint list to be executed
     * after the current event's
     */
    public void updateWaypoints(boolean updateCurrent, boolean updateFuture) {
        // Current waypoint must be first in the list AND match the currentTask if there is one
        if (updateCurrent) {
            LOGGER.fine("Proxy [" + this + "]; updating current waypoint: oeToTask [" + oeToTask + "]");
            if (!sequentialOutputEvents.isEmpty()) {
                LOGGER.fine("Proxy [" + this + "]; current task [" + currentTask + "] and first OE [" + sequentialOutputEvents.get(0) + "]");
            }
            _curWaypointsPos = null;
            _curWaypoints.clear();
            curSequentialEvent = null;

            if (sequentialOutputEvents.isEmpty()) {
                // Have no events to process
                LOGGER.fine("Proxy [" + this + "] has no current WP, sequentialOutputEvents is empty");
            } else if (currentTask != null && oeToTask.get(sequentialOutputEvents.get(0)) != currentTask) {
                // First event doesn't belong to current event
                LOGGER.fine("Proxy [" + this + "] has no current WP, first event [" + sequentialOutputEvents.get(0) + "] is not for current task [" + currentTask + "]");
            } else {
                LOGGER.fine("Proxy [" + this + "] updating current WP with current task [" + currentTask + "] and first event [" + sequentialOutputEvents.get(0) + "]");
                // We have no current task or first event belongs to current task
                if (sequentialOutputEvents.get(0) instanceof ProxyExecutePath) {
                    ProxyExecutePath executePath = (ProxyExecutePath) sequentialOutputEvents.get(0);
                    if (executePath.getProxyPaths().containsKey(this)
                            && executePath.getProxyPaths().get(this) instanceof PathUtm) {
                        curSequentialEvent = sequentialOutputEvents.get(0);
                        PathUtm pathUtm = (PathUtm) executePath.getProxyPaths().get(this);
                        ArrayList<Position> positions = new ArrayList<Position>();
                        for (Location waypoint : pathUtm.getPoints()) {
                            positions.add(Conversion.locationToPosition(waypoint));
                        }
                        _curWaypointsPos = positions;
                        for (Position position : positions) {
                            _curWaypoints.add(position);
                        }
                        if (_curWaypoints.isEmpty()) {                            
                            // Add current position so waypoint complete fires
                            //LOGGER.info("Constructed curWaypoints was empty, adding current boat position");
                            //UtmPose pose = new UtmPose(new Pose3D(utmCoord.getEasting(), utmCoord.getNorthing(), 0.0, 0.0, 0.0, 0.0), new Utm(utmCoord.getZone(), utmCoord.getHemisphere().contains("North")));
                            //_curWaypoints.add(pose);
                        }
                    } else {
                        if (!executePath.getProxyPaths().containsKey(this)) {
                            LOGGER.severe("Proxy Paths has no entry for this proxy: " + this + ": " + executePath.getProxyPaths());
                        } else {
                            LOGGER.severe("Can't handle Path of class " + executePath.getProxyPaths().get(this).getClass().getSimpleName());
                        }
                    }
                } else if (sequentialOutputEvents.get(0) instanceof ProxyGotoPoint) {
                    ProxyGotoPoint gotoPoint = (ProxyGotoPoint) sequentialOutputEvents.get(0);
                    if (gotoPoint.getProxyPoints().containsKey(this)) {
                        curSequentialEvent = sequentialOutputEvents.get(0);
                        Location location = gotoPoint.getProxyPoints().get(this);
                        ArrayList<Position> positions = new ArrayList<Position>();
                        positions.add(Conversion.locationToPosition(location));

                        _curWaypointsPos = positions;
                        for (Position position : positions) {
                            _curWaypoints.add(position);
                        }
                    } else {
                        LOGGER.severe("Proxy points has no entry for this proxy: " + this + ": " + gotoPoint.getProxyPoints());
                    }
                } else {
                    LOGGER.severe("Can't handle OutputEvent of class " + sequentialOutputEvents.get(0).getClass().getSimpleName());
                }
            }
        }

        // Future
        if (updateFuture) {
            _futureWaypoints.clear();
            _futureWaypointsPos = null;

            boolean includeFirst = !sequentialOutputEvents.isEmpty()
                    && currentTask != null
                    && oeToTask.get(sequentialOutputEvents.get(0)) != currentTask;
            if (sequentialInputEvents.isEmpty()
                    || (sequentialOutputEvents.size() == 1 && !includeFirst)) {
                LOGGER.fine("No future WPs, includeFirst [" + includeFirst + "] and sequentialInputEvents size [" + sequentialInputEvents.size() + "]");
            } else {
                int startIndex = includeFirst ? 0 : 1;
                LOGGER.fine("Updating future WPs, starting at index [" + startIndex + "] with sequentialOutputEvents of size [" + sequentialOutputEvents.size() + "]");
                ArrayList<Position> positions = new ArrayList<Position>();
                for (int i = startIndex; i < sequentialOutputEvents.size(); i++) {
                    if (sequentialOutputEvents.get(i) instanceof ProxyExecutePath) {
                        ProxyExecutePath executePath = (ProxyExecutePath) sequentialOutputEvents.get(i);
                        if (executePath.getProxyPaths().containsKey(this)
                                && executePath.getProxyPaths().get(this) instanceof PathUtm) {
                            PathUtm pathUtm = (PathUtm) executePath.getProxyPaths().get(this);
                            for (Location waypoint : pathUtm.getPoints()) {
                                positions.add(Conversion.locationToPosition(waypoint));
                            }
                        } else {
                            if (!executePath.getProxyPaths().containsKey(this)) {
                                LOGGER.severe("Proxy paths [" + executePath.getProxyPaths() + "] has no entry for proxy [" + toString() + "]!");
                            } else {
                                LOGGER.severe("Can't handle Path of class [" + executePath.getProxyPaths().get(this).getClass().getSimpleName() + "]!");
                            }
                        }
                    } else if (sequentialOutputEvents.get(i) instanceof ProxyGotoPoint) {
                        ProxyGotoPoint gotoPoint = (ProxyGotoPoint) sequentialOutputEvents.get(i);
                        if (gotoPoint.getProxyPoints().containsKey(this)) {
                            Location location = gotoPoint.getProxyPoints().get(this);
                            positions.add(Conversion.locationToPosition(location));
                        } else {
                            LOGGER.severe("Proxy points [" + gotoPoint.getProxyPoints() + "] has no entry for proxy [" + toString() + "]!");
                        }
                    } else {
                        LOGGER.severe("BoatProxy can't handle OutputEvent of class [" + sequentialOutputEvents.get(i).getClass().getSimpleName() + "]");
                    }
                }
                _futureWaypointsPos = positions;
                for (Position position : positions) {
                    _futureWaypoints.add(position);
                }
            }
        }

        // Notify listeners
        for (ProxyListenerInt boatProxyListener : listeners) {
            boatProxyListener.waypointsUpdated();
        }
    }

    /**
     * Takes in an output event: if the current sequential output event has the
     * same UUID, it is replaced with the passed in event: if not, it is
     * inserted at the beginning of the sequential list and the proxy begins
     * executing the event
     *
     * @param updatedEvent The event to replace or insert in front of the
     * current sequential event
     * @return Whether the event was handled
     */
    public boolean updateCurrentSeqEvent(OutputEvent updatedEvent) {
        LOGGER.fine("updateCurrentSeqEvent with updatedEvent: " + updatedEvent + "sequentialOutputEvents: " + sequentialOutputEvents);
        boolean handled = true;
        if (curSequentialEvent != null && curSequentialEvent.getId().equals(updatedEvent.getId())) {
            LOGGER.fine("Replace");
            // The updated event is the currently executing event, replace it
            if (updatedEvent instanceof ProxyExecutePath) {
                // Replace the existing event with the updated event and begin executing it
                sequentialOutputEvents.set(0, updatedEvent);
                updateWaypoints(true, false);
                sendCurrentWaypoints();
            } else {
                LOGGER.severe("BoatProxy can't handle OutputEvent of class [" + updatedEvent.getClass().getSimpleName() + "]!");
                handled = false;
            }
        } else {
            LOGGER.fine("Insert at 0 while size is " + sequentialOutputEvents.size());
            // The updated event is not the currently executing event, insert it at the beginning of the list
            if (updatedEvent instanceof ProxyExecutePath) {
                // Insert the event and begin executing it
                sequentialOutputEvents.add(0, updatedEvent);
                sequentialInputEvents.add(0, new ProxyPathCompleted(updatedEvent.getId(), updatedEvent.getMissionId(), this));
                updateWaypoints(true, true);
                sendCurrentWaypoints();
            } else {
                LOGGER.severe("BoatProxy can't handle OutputEvent of class [" + updatedEvent.getClass().getSimpleName() + "]!");
                handled = false;
            }
        }
        return handled;
    }

    /**
     * Stops and removes the current sequential output event being executed and
     * then begins the next (if applicable)
     */
    public void cancelCurrentSeqEvent() {
        if (sequentialOutputEvents.isEmpty()) {
            return;
        }
        OutputEvent removedEvent = sequentialOutputEvents.remove(0);
        sequentialInputEvents.remove(0);

        updateWaypoints(true, true);
        sendCurrentWaypoints();
    }

    public void sendCurrentWaypoints() {
        LOGGER.fine("sendCurrentWaypoints");
        if (_curWaypoints.isEmpty()) {
            // There weren't any more waypoints - stop the proxy
            // Stop proxy as opposed to sending empty list of waypoints because 
            //  that will make the system think it finished assigned waypoints
            LOGGER.info("BoatProxy [" + toString() + "] stopWaypoints");

            // Make sure we don't send waypoint commands too fast - stop and go commands can get out of order otherwise
            checkAndSleepForCmd();
            
            // set GAMS algorithm to null
            //knowledge.set(containers.prefix + "algorithm.waypoints.finished", 0);
            //knowledge.set(containers.prefix + "command", "null");            
            endGAMSAlgorithm();
            
            
        } else {
            LOGGER.info("BoatProxy [" + toString() + "] startWaypoints [" + _curWaypoints.toString() + "]");
            activateAutonomy(true);

            // Make sure we don't send waypoint commands too fast - stop and go commands can get out of order otherwise
            checkAndSleepForCmd();

            // Update MADARA containers
            endTeleop();
            sendWaypointsQueue();
                        
        }
    }

    void beginTeleop() {
        containers.teleopStatus.set(TELEOPERATION_TYPES.GUI_MS.getLongValue());
        knowledge.set(containers.prefix + "command", "null");
        
        // TODO: joystick control in java?
        // see "package crw.ui.teleop", "GamepadController"
        
    }
    
    void endTeleop() {
        containers.teleopStatus.set(TELEOPERATION_TYPES.NONE.getLongValue());
    }
    

    public void activateAutonomy(final boolean activate) {
        //autonomyEnabled.set((activate ? 1 : 0));
        //autonomyEnabledReceivedAck.set(1);
        //autonomyEnabled.set((activate ? beginTeleop() : endTeleop()));
    }

    public Color getColor() {
        return color;
    }

    public BufferedImage getLatestImg() {
        return latestImg;
    }

    public UdpVehicleServer getVehicleServer() {
        //return _server;
        return null;
    }

    @Override
    public String toString() {
        return name;
//        return name + "@" + _server.getVehicleService();
    }

    private void checkAndSleepForCmd() {
        if (lastTime >= 0) {
            long timeGap = MIN_TIME_BTW_CMDS - (System.currentTimeMillis() - lastTime);
            if (timeGap > 0) {
                LOGGER.fine("Proxy [" + this + "] requires time gap before sending next command, sleeping for " + timeGap + "ms");
                try {
                    Thread.sleep(timeGap);
                } catch (InterruptedException ex) {
                    Logger.getLogger(BoatProxy.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        lastTime = System.currentTimeMillis();
    }

    @Override
    public void completeMission(UUID missionId) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }
    
    class GPSWatchdogListener extends BaseThread {
        @Override
        public void run() {
            if (containers.gpsWatchdog.get() == 1L) {
                containers.gpsWatchdog.set(0L);
            }
            else {
                // TODO: how to alert the user that the boat has lost GPS?
                // A crw.event.output.ui event?
                System.out.println(String.format("WARNING: GPS may not available for boat # %d",_boatNo));
            }
        }
        
    }
    
    class MadaraConnectivityWatchdogListener extends BaseThread {
        @Override
        public void run() {
            if (containers.connectivityWatchdog.get() == 1L) {
                containers.connectivityWatchdog.set(0L);
            }
            else {
                // TODO: how to alert the user that the boat isn't connected?
                // A crw.event.output.ui event?
                System.out.println(String.format("WARNING: Madara connectivity NOT available for boat # %d",_boatNo));
            }
            if (containers.wifiStrength.get() < MINIMUM_SAFE_WIFI_SIGNAL_STRENGTH) {
                // TODO: alert the user that the wifi signal is weak
                System.out.println(String.format("WARNING: Wifi signal strength is low: %d", containers.wifiStrength.get()));
            }
        }
    }

    class MadaraPoseListener extends BaseThread {
        @Override
        public void run() {            
            KnowledgeRecord kr = containers.eastingNorthingBearing.toRecord();
            double[] eastingNorthingBearing = kr.toDoubleArray();
            kr.free();
            double yaw = eastingNorthingBearing[2];

            double altitude = 0.0;
            double roll = 0.0;
            double pitch = 0.0;

            Angle lat = Angle.fromDegrees(containers.location.get(0));
            Angle lon = Angle.fromDegrees(containers.location.get(1));
            LatLon latlon = new LatLon(lat, lon);
            int zone = (int)containers.longitudeZone.get();
            String hemisphere;  
            String wwHemi;
            if (lat.getDegrees() > 0) {
                hemisphere = "N";
                wwHemi = AVKey.NORTH;
            } 
            else {
                hemisphere = "S";
                wwHemi = AVKey.SOUTH;
            }
            UTMCoord boatPos = UTMCoord.fromLatLon(lat, lon);
            double easting = boatPos.getEasting();
            double northing = boatPos.getNorthing();
            
            errorEllipse = containers.getErrorEllipse();
            
            // update local variables
            try {                                
                Position p = new Position(latlon, 0.0);

                // Update state variables
                _pose = new UtmPose(new Pose3D(easting, northing, altitude, roll, pitch, yaw), new Utm(zone, (hemisphere.startsWith("N") || hemisphere.startsWith("n"))));
                position = p;
                location = Conversion.positionToLocation(position);
                

                for (ProxyListenerInt boatProxyListener : listeners) {
                    boatProxyListener.poseUpdated();
                }

                // Send out event update
                if (sendEvent.get()) {
                    ProxyPoseUpdated ie = new ProxyPoseUpdated(null, null, bp);
                    for (ProxyListenerInt boatProxyListener : listeners) {
                        boatProxyListener.eventOccurred(ie);
                    }
                    sendEvent.set(false);
                }
            } catch (java.lang.IllegalArgumentException iae) {
                //iae.printStackTrace();
            }                                    
        }
    }
    
    
    class MadaraWaypointListener extends BaseThread {
        @Override
        public void run() {
            // Check if waypoints were completed
            if (knowledge.get(containers.prefix + "algorithm.waypoints.finished").toLong() == 1) {

                knowledge.set(containers.prefix + "command","null");
                containers.waypointsFinishedStatus.set(0); // need to manually set this to 0 or this poll will fire over and over
                
                // Notify listeners
                for (ProxyListenerInt boatProxyListener : listeners) {
                    boatProxyListener.waypointsComplete();
                }
                if (sequentialOutputEvents.isEmpty()) {
                    LOGGER.severe("Got a waypoints done message, but sequentialOutputEvents is empty!");
                } 
                else {
                    // Remove the OutputEvent that held this path
                    OutputEvent oe = sequentialOutputEvents.remove(0);
                    // Send out the InputEvent assocaited with this path
                    InputEvent ie = sequentialInputEvents.remove(0);

                    updateWaypoints(true, true);

                    LOGGER.log(Level.FINE, "BoatProxy " + getName() + " completed sequential OE " + oe + ", sending out IE " + ie);
                    for (ProxyListenerInt boatProxyListener : listeners) {
                        boatProxyListener.eventOccurred(ie);
                    }
                    if (!sequentialOutputEvents.isEmpty()) {
                        LOGGER.log(Level.FINE, "Sequential OE list is not empty, do the next one!");
                        sendCurrentWaypoints();
                    }
                }
            }
        }       
    }
}
