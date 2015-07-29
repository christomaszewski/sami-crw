package crw.event.output.proxy;

import sami.event.OutputEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

/**
 *
 * @author jjb
 */
public class ProxyEndGAMSAlgorithm extends OutputEvent {
    // List of fields for which a definition should be provided
    public static final ArrayList<String> fieldNames = new ArrayList<String>();
    // Description for each field
    public static final HashMap<String, String> fieldNameToDescription = new HashMap<String, String>();
    // Fields
    // NOTHING
    
    static {
        // NOTHING   
    }

    public ProxyEndGAMSAlgorithm() {
        id = UUID.randomUUID();
    }

    public String toString() {
        return "ProxyEndGAMSAlgorithm";
    }    
    
}
