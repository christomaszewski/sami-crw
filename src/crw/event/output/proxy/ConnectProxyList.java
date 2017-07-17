/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package crw.event.output.proxy;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import sami.event.OutputEvent;

/**
 *
 * @author ckt
 */
public class ConnectProxyList extends OutputEvent {
    // List of fields for which a definition should be provided
    public static final ArrayList<String> fieldNames = new ArrayList<String>();
    // Description for each field
    public static final HashMap<String, String> fieldNameToDescription = new HashMap<String, String>();
    
    // Fields
    public String names;
    //public String server;
    public String ids;
    
    public List<Color> colors = Arrays.asList(Color.RED, Color.BLUE, Color.GREEN, Color.CYAN, Color.MAGENTA,
                                    Color.YELLOW, Color.ORANGE, Color.LIGHT_GRAY, Color.PINK, Color.WHITE, 
                                    Color.DARK_GRAY, Color.BLACK, Color.GRAY);
    
    static {
        fieldNames.add("names");
        fieldNames.add("ids");
        
        fieldNameToDescription.put("names", "Comma seperated names");
        fieldNameToDescription.put("ids", "Madara agent numbers for the proxies");
    }

    public ConnectProxyList() {
        id = UUID.randomUUID();
    }
    
    public String toString() {
        return "ConnectMultipleProxies [" + names + ", " + ids + "]";
    }
}