package crw.event.output.proxy;

import sami.event.OutputEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

/**
 *
 * @author jjb
 */
public class ZoneCoverage extends OutputEvent {

    // List of fields for which a definition should be provided
    public static final ArrayList<String> fieldNames = new ArrayList<String>();
    // Description for each field
    public static final HashMap<String, String> fieldNameToDescription = new HashMap<String, String>();
    // Fields
    public int uid;
    public String defendersGroupName;
    public String assetsGroupName;
    public String enemiesGroupName;
    public String formationType;
    public double bufferDistance;
    public double distanceFraction;
    
    static {
        fieldNames.add("uid");
        fieldNames.add("defendersGroupName");
        fieldNames.add("assetsGroupName");
        fieldNames.add("enemiesGroupName");
        fieldNames.add("formationType");
        fieldNames.add("bufferDistance");
        fieldNames.add("distanceFraction");
        
        fieldNameToDescription.put("uid", "Unique ID of the command (> 0)");
        fieldNameToDescription.put("defendersGroupName", "Name of the defenders group?");
        fieldNameToDescription.put("assetsGroupName", "Name of the assets group?");
        fieldNameToDescription.put("enemiesGroupName", "Name of the enemies group?");
        fieldNameToDescription.put("formationType", "Type of formation?");
        fieldNameToDescription.put("bufferDistance", "Meters of buffer distance between agents?");
        fieldNameToDescription.put("distanceFraction", "Defenders location fraction, 0 at enemy, 1 at asset?");
    }

    public ZoneCoverage() {
        id = UUID.randomUUID();
    }

    public String toString() {
        return "ZoneCoverage";
    }        
    
    public int getUID() {
        return this.uid;
    }    
    void setUID(int uid) {
        this.uid = uid;
    }    
    public String getDefendersGroupName() {
        return this.defendersGroupName;
    }
    public String getAssetsGroupName() {
        return this.assetsGroupName;
    }
    public String getEnemiesGroupName() {
        return this.enemiesGroupName;
    }    
    public double getBufferDistance() {
        return this.bufferDistance;
    }    
    public double getDistanceFraction() {
        return this.distanceFraction;
    }
    public String getFormationType() {
        return this.formationType;
    }                    
}
