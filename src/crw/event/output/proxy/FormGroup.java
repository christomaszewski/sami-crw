package crw.event.output.proxy;

import sami.event.OutputEvent;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

/**
 *
 * @author jjb
 */
public class FormGroup extends OutputEvent {

    // List of fields for which a definition should be provided
    public static final ArrayList<String> fieldNames = new ArrayList<String>();
    // Description for each field
    public static final HashMap<String, String> fieldNameToDescription = new HashMap<String, String>();
    // Fields
    public String groupName;
    
    static {
        fieldNames.add("groupName");
        
        fieldNameToDescription.put("groupName", "Name of the group?");
    }

    public FormGroup() {
        id = UUID.randomUUID();
    }

    public String toString() {
        return "FormGroup";
    }        
    
    public String getGroupName() {
        return this.groupName;
    }
}
