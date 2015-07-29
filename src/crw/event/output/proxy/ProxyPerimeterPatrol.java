/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package crw.event.output.proxy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import sami.event.OutputEvent;

/**
 *
 * @author jjb
 */
public class ProxyPerimeterPatrol extends OutputEvent {
    // List of fields for which a definition should be provided
    public static final ArrayList<String> fieldNames = new ArrayList<String>();
    // Description for each field
    public static final HashMap<String, String> fieldNameToDescription = new HashMap<String, String>();
    // Fields
    public int regionNo;
    
    static {
        fieldNames.add("regionNo");        
        fieldNameToDescription.put("regionNo", "Number for region id?");
    }

    public ProxyPerimeterPatrol() {
        id = UUID.randomUUID();
    }
    
    public int getRegionNo() {
        return regionNo;
    }
    
    public void setRegionNo(int id) {
        this.regionNo = id;
    }

    public String toString() {
        return "ProxyPerimeterPatrol";
    }    
}
