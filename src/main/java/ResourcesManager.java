import com.xilinx.rapidwright.dcp.CheckpointTools;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.edif.EDIFCell;
import com.xilinx.rapidwright.edif.EDIFLibrary;
import org.ini4j.Wini;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;

public class ResourcesManager {

    /*
     * Management class for DCPs, config files, and outputs
     */

    public static final String RESOURCES_DIR = "src/main/resources/";
    public static final String COMPONENTS_DIR = RESOURCES_DIR + "components/";
    public static final String OUTPUT_DIR = "output/";

    public static String COMPONENTS_FILE_NAME = ResourcesManager.RESOURCES_DIR + "register_components.conf";
    public static String PLACEMENTS_FILE_NAME = ResourcesManager.RESOURCES_DIR + "placements.conf";

    public static String PART_NAME;

    public static Wini componentsConfig = null;
    public static Wini placementsConfig = null;

    public static final String commonKey = "common";
    public static final String bwKey = "bw";
    public static final String nameKey = "name";

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

    public static Design newDesignFromSources(String designName) throws IOException {
        ComplexRegister.loadRegModulesFromConfig();
        Design d = new Design(designName, ResourcesManager.PART_NAME);
        d.setDCPXMLAttribute(CheckpointTools.DISABLE_AUTO_IO_BUFFERS_NAME, "1");

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

    public static ArrayList<ComplexRegister> registersFromPlacements(Design d) throws IOException {
        initPlacementsConfig();

        ArrayList<ComplexRegister> registers = new ArrayList<ComplexRegister>();

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

            registers.add(new ComplexRegister(d, name, components));

            regKey += 1;
        }

        return registers;
    }
}
