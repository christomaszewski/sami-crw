package crw.event.output.proxy;

import java.awt.Color;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import sami.event.OutputEvent;
import sami.path.Location;

/**
 *
 * @author nbb
 */
public class CreateSimulatedProxy extends OutputEvent {

    // List of fields for which a definition should be provided
    public static final ArrayList<String> fieldNames = new ArrayList<String>();
    // Description for each field
    public static final HashMap<String, String> fieldNameToDescription = new HashMap<String, String>();
    // Fields
    public String name;
    public Color color;
    public Location startLocation;
    public int numberToCreate;
    public boolean spoofData;

    static {
        fieldNames.add("name");
        fieldNames.add("color");
        fieldNames.add("startLocation");
        fieldNames.add("numberToCreate");
        fieldNames.add("spoofData");

        fieldNameToDescription.put("name", "Name of the proxy?");
        fieldNameToDescription.put("color", "Visualization color for the proxy?");
        fieldNameToDescription.put("startLocation", "Where to create the proxy?");
        fieldNameToDescription.put("numberToCreate", "Number of proxies to create at this location?");
        fieldNameToDescription.put("spoofData", "Want spoofed environmental data?");
    }

    public CreateSimulatedProxy() {
        id = UUID.randomUUID();
    }

    public String toString() {
        return "CreateSimulatedProxy [" + name + ", " + color + ", " + startLocation + ", " + numberToCreate + "]";
    }
}
