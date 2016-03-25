package crw.event.output.proxy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import sami.event.OutputEvent;

/**
 *
 * @author jjb
 */
public class FormCylindricalFormation extends OutputEvent{
    
    // List of fields for which a definition should be provided
    public static final ArrayList<String> fieldNames = new ArrayList<String>();
    // Description for each field
    public static final HashMap<String, String> fieldNameToDescription = new HashMap<String, String>();
    // Fields
    public int leaderNo;
    public double spacing;
    public String groupName;
    //public String teamMembers; ////// will use OperatorSelectBoatList instead
    
    static {
        fieldNames.add("leaderNo");
        fieldNames.add("spacing");
        fieldNames.add("groupName");

        fieldNameToDescription.put("leaderNo", "boatNo of leading agent?");
        fieldNameToDescription.put("spacing", "distance from leader to ring of agents around leader?");
        fieldNameToDescription.put("groupName", "name of group?");
        
        //fieldNameToDescription.put("teamMembers", "a,b,c,... team boatNo N-tuple?");
    }
    
    public FormCylindricalFormation() {
        id = UUID.randomUUID();
    }
    
    public int getLeaderNo() {
        return leaderNo;
    }
    public void setLeaderNo(int leaderNo) {
        this.leaderNo = leaderNo;
    }
    public double getSpacing() {
        return spacing;
    }
    public void setSpacing(double spacing) {
        this.spacing = spacing;
    }
    public String getGroupName() {
        return groupName; 
    }
    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
    public String toString() {
        return "FormCylindricalFormation";
    }

    
}
