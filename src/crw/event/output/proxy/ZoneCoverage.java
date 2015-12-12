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
    public String defendersGroupName;
    public String assetsGroupName;
    public String enemiesGroupName;
    public String formationType;
    public double bufferDistance;
    
    static {
        fieldNames.add("defendersGroupName");
        fieldNames.add("assetsGroupName");
        fieldNames.add("enemiesGroupName");
        fieldNames.add("formationType");
        fieldNames.add("bufferDistance");
        
        fieldNameToDescription.put("defendersGroupName", "Name of the defenders group?");
        fieldNameToDescription.put("assetsGroupName", "Name of the assets group?");
        fieldNameToDescription.put("enemiesGroupName", "Name of the enemies group?");
        fieldNameToDescription.put("formationType", "Type of formation?");
        fieldNameToDescription.put("bufferDistance", "Meters of buffer distance between agents?");
    }

    public ZoneCoverage() {
        id = UUID.randomUUID();
    }

    public String toString() {
        return "ZoneCoverage";
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
    public String getFormationType() {
        return this.formationType;
    }                    
}
