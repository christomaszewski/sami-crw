package crw.event.input.operator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import sami.event.InputEvent;

/**
 *
 * @author jjb
 */
public class OperatorCreatesRegion extends InputEvent {
    
    // List of fields for which a definition should be provided
    public static final ArrayList<String> fieldNames = new ArrayList<String>();
    // Description for each field
    public static final HashMap<String, String> fieldNameToDescription = new HashMap<String, String>();
    // List of fields for which a variable name should be provided
    public static final ArrayList<String> variableNames = new ArrayList<String>();
    // Description for each variable
    public static final HashMap<String, String> variableNameToDescription = new HashMap<String, String>();
    
    public OperatorCreatesRegion() {
        id = UUID.randomUUID();
    }    
    
    public OperatorCreatesRegion(UUID relevantOutputEventUuid, UUID missionUuid) {
        this.relevantOutputEventId = relevantOutputEventUuid;
        this.missionId = missionUuid;
        id = UUID.randomUUID();
    }    
    
}
