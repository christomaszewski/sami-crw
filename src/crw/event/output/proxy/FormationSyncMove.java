package crw.event.output.proxy;

import sami.event.OutputEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import sami.path.Location;

/**
 *
 * @author jjb
 */
public class FormationSyncMove extends OutputEvent {
    
    // List of fields for which a definition should be provided
    public static final ArrayList<String> fieldNames = new ArrayList<String>();
    // Description for each field
    public static final HashMap<String, String> fieldNameToDescription = new HashMap<String, String>();
    // Fields
    public Location destination;
    public Location stagingPoint;
    public double bufferDistance;
    public String barrier;
    public String formationType;    
    public String groupName;
    
    static {
        fieldNames.add("stagingPoint");
        fieldNames.add("destination");
        fieldNames.add("bufferDistance");
        fieldNames.add("barrier");
        fieldNames.add("formationType");
        fieldNames.add("groupName");
        
        fieldNameToDescription.put("stagingPoint", "Location of initial formation?");
        fieldNameToDescription.put("destination", "Final location of formation?");
        fieldNameToDescription.put("bufferDistance", "Meters of buffer distance between agents?");
        fieldNameToDescription.put("barrier", "Unique string to identify this event?");
        fieldNameToDescription.put("formationType", "Type of formation?");
        fieldNameToDescription.put("groupName", "Name of group to participate?");
    }

    public FormationSyncMove() {
        id = UUID.randomUUID();
    }

    public String toString() {
        return "FormationSyncMove";
    }        
    
    public Location getDestination() {
        return this.destination;
    }    
    public Location getStagingPoint() {
        return this.stagingPoint;
    }    
    public double getBufferDistance() {
        return this.bufferDistance;
    }    
    public String getBarrier() {
        return this.barrier;
    }    
    public String getFormationType() {
        return this.formationType;
    }                
    public String getGroupName() {
        return this.groupName;
    }
}
