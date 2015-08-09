package crw.event.output.proxy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import sami.event.OutputEvent;

/**
 *
 * @author jjb
 */
public class ProxyReHomeAutonomy extends OutputEvent {

    // List of fields for which a definition should be provided
    public static final ArrayList<String> fieldNames = new ArrayList<String>();
    // Description for each field
    public static final HashMap<String, String> fieldNameToDescription = new HashMap<String, String>();
    // Fields
    // NONE

    static {
        // NONE
    }

    public ProxyReHomeAutonomy() {
        id = UUID.randomUUID();
    }

    public String toString() {
        return "ProxyReHomeAutonomy";
    }    
    
}