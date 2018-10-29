import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.edif.EDIFCell;
import com.xilinx.rapidwright.edif.EDIFLibrary;
import org.ini4j.Wini;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ResourcesManager {

    /*
     * Management class for DCPs, config files, and outputs
     */

    public static final String RESOURCES_DIR = "src/main/resources/";
    public static final String COMPONENTS_DIR = RESOURCES_DIR + "components/";
    public static final String OUTPUT_DIR = "output/";

    public static String COMPONENTS_FILE_NAME = ResourcesManager.RESOURCES_DIR + "register_components.conf";
    public static String PLACEMENTS_FILE_NAME = ResourcesManager.RESOURCES_DIR + "placements.conf";
    public static String ROUTES_FILE_NAME = ResourcesManager.RESOURCES_DIR + "routes.conf";

    public static String PART_NAME;

    public static Wini componentsConfig = null;
    public static Wini placementsConfig = null;
    public static Wini routesConfig = null;

    public static final String commonKey = "common";
    public static final String bwKey = "bw";
    public static final String nameKey = "name";
    public static final String inKey = "in";
    public static final String outKey = "out";
    public static final String routesKey = "routes";


    public static final String componentKeyPrefix = "comp";
    public static final String typeKeyPrefix = "type";
    public static final String regKeyPrefix = "reg";

    public static void setPartName(String partName) {
        PART_NAME = partName;
    }

    public static void initComponentsConfig() throws IOException {
        if (componentsConfig == null)
            componentsConfig = new Wini(new File(COMPONENTS_FILE_NAME));
    }

    public static void initPlacementsConfig() throws IOException {
        if (placementsConfig == null)
            placementsConfig = new Wini(new File(PLACEMENTS_FILE_NAME));
    }

    public static void initRoutesConfig() throws IOException {
        if (routesConfig == null)
            routesConfig = new Wini(new File(ROUTES_FILE_NAME));
    }

    public static Design newDesignFromSources(String designName) throws IOException {
        ComplexRegister.loadRegModulesFromConfig();
        Design d = new Design(designName, ResourcesManager.PART_NAME);
        d.setAutoIOBuffers(false);

        for (ComplexRegModule module : ComplexRegister.typeToRegModuleMap.values()) {
            Design regDesign = module.getSrcDesign();
            for (EDIFCell cell : regDesign.getNetlist().getWorkLibrary().getCells()) {
                cell.rename("type" + module.getType() + "_" + cell.getName());
                d.getNetlist().getWorkLibrary().addCell(cell);
            }
            EDIFLibrary hdi = d.getNetlist().getHDIPrimitivesLibrary();
            for (EDIFCell cell : regDesign.getNetlist().getHDIPrimitivesLibrary().getCells()) {
                if (!hdi.containsCell(cell)) hdi.addCell(cell);
            }
        }

        return d;
    }

    public static HashMap<String, ComplexRegister> registersFromPlacements(Design d) throws IOException {
        initPlacementsConfig();

        HashMap<String, ComplexRegister> registersMap = new HashMap<String, ComplexRegister>();

        //int bitWidth = Integer.valueOf(placementsConfig.get(commonKey, bwKey));

        int regKey = 0;
        while (placementsConfig.containsKey(regKeyPrefix + regKey)) {

            ArrayList<RegisterComponent> components = new ArrayList<RegisterComponent>();

            String name = placementsConfig.get(regKeyPrefix + regKey).get(nameKey);

            int compKey = 0;
            while (placementsConfig.get(regKeyPrefix + regKey).containsKey(componentKeyPrefix + compKey)) {
                String compInfo = placementsConfig.get(regKeyPrefix + regKey).get(componentKeyPrefix + compKey);

                compInfo = compInfo.replaceAll(" ", "");
                String[] compInfoArray = compInfo.split(",");

                String typeStr = compInfoArray[0];
                String siteName = compInfoArray[1];

                int type = Integer.valueOf(typeStr.substring(4));

                components.add(new RegisterComponent(type, siteName));

                compKey += 1;
            }

            registersMap.put(name, new ComplexRegister(d, name, components));

            regKey += 1;
        }

        return registersMap;
    }


    public static ArrayList<RegisterConnection> connectionsFromRoutes(Design d, HashMap<String,
            ComplexRegister> registersMap) throws IOException {
        initRoutesConfig();
        ArrayList<RegisterConnection> connections = new ArrayList<RegisterConnection>();

        List<String> inConns = routesConfig.get(routesKey).getAll(inKey);
        List<String> outConns = routesConfig.get(routesKey).getAll(outKey);

        for (String inConn : inConns) {
            String[] elem = inConn.split("_");
            String regName = elem[0];
            int lowBit = Integer.valueOf(elem[1].substring(1));
            int hiBit = Integer.valueOf(elem[2].substring(1));

            connections.add(new RegisterConnection(null, registersMap.get(regName), 0, 0, lowBit, hiBit));
        }
        for (String outConn : outConns) {
            String[] elem = outConn.split("_");
            String regName = elem[0];
            int lowBit = Integer.valueOf(elem[1].substring(1));
            int hiBit = Integer.valueOf(elem[2].substring(1));

            connections.add(new RegisterConnection(registersMap.get(regName), null, lowBit, hiBit, 0, 0));
        }

        for (String key : routesConfig.get(routesKey).keySet()) {
            if (!key.equals(inKey) && !key.equals(outKey)) {
                String[] elem;

                elem = key.split("_");
                String srcRegName = elem[0];
                int srcLowBit = Integer.valueOf(elem[1].substring(1));
                int srcHiBit = Integer.valueOf(elem[2].substring(1));

                elem = routesConfig.get(routesKey).get(key).split("_");
                String snkRegName = elem[0];
                int snkLowBit = Integer.valueOf(elem[1].substring(1));
                int snkHiBit = Integer.valueOf(elem[2].substring(1));

                connections.add(new RegisterConnection(registersMap.get(srcRegName), registersMap.get(snkRegName),
                        srcLowBit, srcHiBit, snkLowBit, snkHiBit));
            }
        }

        return connections;
    }

}
