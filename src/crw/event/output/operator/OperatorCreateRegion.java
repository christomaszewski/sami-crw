package crw.event.output.operator;

import static crw.event.output.proxy.ProxyExploreArea.fieldNameToDescription;
import static crw.event.output.proxy.ProxyExploreArea.fieldNames;
import crw.proxy.BoatProxy;
import sami.event.OutputEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import sami.area.Area2D;

/**
 *
 * @author jjb
 */
public class OperatorCreateRegion extends OutputEvent {
     // List of fields for which a definition should be provided
    public static final ArrayList<String> fieldNames = new ArrayList<String>();
    // Description for each field
    public static final HashMap<String, String> fieldNameToDescription = new HashMap<String, String>();
    // Fields
    public Area2D region;
    public int regionNo;
    
    static {
        fieldNames.add("region");
        fieldNames.add("regionNo");
        
        fieldNameToDescription.put("region", "Region to create?");
        fieldNameToDescription.put("regionNo", "Number for region id?");
    }

    public OperatorCreateRegion() {
        id = UUID.randomUUID();
    }
    
    public Area2D getRegion() {
        return region;
    }

    public void setRegion(Area2D area) {
        this.region = area;
    }
    
    public int getRegionNo() {
        return regionNo;
    }
    
    public void setRegionNo(int id) {
        this.regionNo = id;
    }

    public String toString() {
        return "OperatorCreateRegion";
    }
   
}
