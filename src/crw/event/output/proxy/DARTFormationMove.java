package crw.event.output.proxy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import sami.event.OutputEvent;
import sami.path.Location;

/**
 *
 * @author jjb
 */
public class DARTFormationMove extends OutputEvent {

    // List of fields for which a definition should be provided
    public static final ArrayList<String> fieldNames = new ArrayList<String>();
    // Description for each field
    public static final HashMap<String, String> fieldNameToDescription = new HashMap<String, String>();
    // Fields
    public Location upperLeft;
    public Location bottomRight;    
    public int gridXCount;
    public int gridYCount;
    public int gridXDestIndex;
    public int gridYDestIndex;
    
    static {
        fieldNames.add("upperLeft");
        fieldNames.add("bottomRight");
        fieldNames.add("gridXCount");
        fieldNames.add("gridYCount");
        fieldNames.add("gridXDestIndex");
        fieldNames.add("gridYDestIndex");        

        fieldNameToDescription.put("upperLeft", "upper left corner of grid?");
        fieldNameToDescription.put("bottomRight", "bottom right corner of grid?");        
        fieldNameToDescription.put("gridXCount", "number of grid divisions along x?");
        fieldNameToDescription.put("gridYCount", "number of grid divisions along y?");
        fieldNameToDescription.put("gridXDestIndex", "x index of destination cell?");
        fieldNameToDescription.put("gridYDestIndex", "y index of destination cell?");
    }
    
    public DARTFormationMove() {
        id = UUID.randomUUID();
    }
    
    public Location getUpperLeft () {
        return this.upperLeft;
    }
    
    public void setUpperLeft(Location upperLeft) {
        this.upperLeft = upperLeft;
    }
    
    public Location getBottomRight () {
        return this.bottomRight;
    }
    
    public void setBottomRight(Location bottomRight) {
        this.bottomRight = bottomRight;
    }    
    
    public int getGridXCount () {
        return this.gridXCount;
    }
    
    public void setGridXCount(int gridXCount) {
        this.gridXCount = gridXCount;
    }
    
    public int getGridYCount () {
        return this.gridYCount;
    }
    
    public void setGridYCount(int gridYCount) {
        this.gridYCount = gridYCount;
    }    
    
    public int getGridXDestIndex () {
        return this.gridXDestIndex;
    }
    
    public void setGridXDestIndex(int gridXDestIndex) {
        this.gridXDestIndex = gridXDestIndex;
    }
    
    public int getGridYDestIndex () {
        return this.gridYDestIndex;
    }
    
    public void setGridYDestIndex(int gridYDestIndex) {
        this.gridYDestIndex = gridYDestIndex;
    }    
    

    public String toString() {
        return "DARTFormationMove";
    }    
    
}
