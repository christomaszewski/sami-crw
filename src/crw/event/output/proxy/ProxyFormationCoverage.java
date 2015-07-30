package crw.event.output.proxy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import sami.event.OutputEvent;

/**
 *
 * @author jjb
 */
public class ProxyFormationCoverage extends OutputEvent {
    
    // List of fields for which a definition should be provided
    public static final ArrayList<String> fieldNames = new ArrayList<String>();
    // Description for each field
    public static final HashMap<String, String> fieldNameToDescription = new HashMap<String, String>();
    // Fields
    public int regionNo;
    public int leaderNo;
    public double spacing;
    //public String teamMembers; ////// will use OperatorSelectBoatList instead
    public String modifier;
    public String coverageType;
    
    static {
        fieldNames.add("regionNo");
        fieldNames.add("leaderNo");
        fieldNames.add("spacing");
        //fieldNames.add("teamMembers");
        fieldNames.add("modifier");
        fieldNames.add("coverageType");

        fieldNameToDescription.put("regionNo", "Region to explore?");
        fieldNameToDescription.put("leaderNo", "boatNo of leading agent?");
        fieldNameToDescription.put("spacing", "distance from leader to ring of agents around leader?");
        //fieldNameToDescription.put("teamMembers", "a,b,c,... team boatNo N-tuple?");
        fieldNameToDescription.put("modifier", "algorithm modifier ('default' or 'rotation' ONLY)?");
        fieldNameToDescription.put("coverageType", "coverage type ('urec' or 'waypoints')?");
    }
    
    public ProxyFormationCoverage() {
        id = UUID.randomUUID();
    }
    
    public int getRegionNo() {
        return regionNo;
    }
    public void setRegionNo(int regionNo) {
        this.regionNo = regionNo;
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
    public String getModifier() {
        return modifier;
    }
    public void setModifier(String modifier) {
        this.modifier = modifier;
    }    
    public String getCoverageType() {
        return coverageType;
    }
    public void setCoverageType(String coverageType) {
        this.coverageType = coverageType;
    }    
    
    public String toString() {
        return "ProxyFormationCoverage";
    }

    
}
