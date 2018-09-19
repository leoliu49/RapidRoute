import com.xilinx.rapidwright.dcp.CheckpointTools;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Module;
import com.xilinx.rapidwright.design.ModuleInstance;
import com.xilinx.rapidwright.device.Site;
import com.xilinx.rapidwright.device.SiteTypeEnum;
import com.xilinx.rapidwright.edif.EDIFCell;
import com.xilinx.rapidwright.edif.EDIFCellInstance;
import com.xilinx.rapidwright.edif.EDIFLibrary;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.ini4j.Wini;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class ComplexRegister {

    public static String CONFIG_FILE_NAME = ResourcesManager.RESOURCES_DIR + "complex_register.conf";

    public static String typeKeyPrefix = "type";
    public static String inPIPKeyPrefix = "inPIP";
    public static String outPIPKeyPrefix = "outPIP";

    public static String bwKey = "bw";

    public static HashMap<Integer, ComplexRegModule> typeToRegModuleMap = new HashMap<Integer, ComplexRegModule>();


    private static Design readDcp(String dcpFileName) {
        Design regDesign = Design.readCheckpoint(dcpFileName);
        ResourcesManager.setPartName(regDesign.getPartName());
        return regDesign;
    }

    private static void loadRegModulesFromConfig() throws IOException {
        Wini ini = new Wini(new File(ComplexRegister.CONFIG_FILE_NAME));

        int typeKey = 0;
        while (ini.containsKey(typeKeyPrefix + typeKey)) {

            int bitWidth = Integer.valueOf(ini.get(typeKeyPrefix + typeKey, bwKey));

            int inPIPKey = 0;
            ArrayList<String> inPIPNames = new ArrayList<String>();
            while (ini.get(typeKeyPrefix + typeKey).containsKey(inPIPKeyPrefix + inPIPKey)) {
                inPIPNames.add(ini.get(typeKeyPrefix + typeKey, inPIPKeyPrefix + inPIPKey));
                inPIPKey += 1;
            }

            int outPIPKey = 0;
            ArrayList<String> outPIPNames = new ArrayList<String>();
            while (ini.get(typeKeyPrefix + typeKey).containsKey(outPIPKeyPrefix + outPIPKey)) {
                outPIPNames.add(ini.get(typeKeyPrefix + typeKey, outPIPKeyPrefix + outPIPKey));
                outPIPKey += 1;
            }

            ComplexRegModule regModule = new ComplexRegModule(typeKey, bitWidth, inPIPNames, outPIPNames,
                    readDcp(ResourcesManager.COMPONENTS_DIR + typeKeyPrefix + typeKey + ".dcp"));
            typeToRegModuleMap.put(typeKey, regModule);

            typeKey += 1;
        }
    }

    public static Design newDesignFromSources(String designName) throws IOException {
        loadRegModulesFromConfig();
        Design d = new Design(designName, ResourcesManager.PART_NAME);
        d.setDCPXMLAttribute(CheckpointTools.DISABLE_AUTO_IO_BUFFERS_NAME, "1");

        for (ComplexRegModule module : typeToRegModuleMap.values()) {
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

    private String name;

    private ArrayList<RegisterComponent> components;
    private int componentSize;

    public ComplexRegister(Design d, String name, ArrayList<RegisterComponent> components) {

        this.name = name;

        this.components = components;
        this.componentSize = components.size();

        EDIFCell top = d.getNetlist().getTopCell();

        for (RegisterComponent component : components) {
            ComplexRegModule regModule = ComplexRegister.typeToRegModuleMap.get(component.getType());
            EDIFCellInstance ci = top.createChildCellInstance(component.getName(),
                    regModule.getModule().getNetlist().getTopCell());
            ModuleInstance mi = d.createModuleInstance(component.getName(), regModule.getModule());
            mi.setCellInstance(ci);

            Site anchorSite = d.getDevice().getSite(component.getSiteName());
            mi.place(anchorSite);

            RouterLog.log("Placed component for <" + name + "> at site <" + component.getSiteName() + ">.",
                    RouterLog.Level.NORMAL);
        }
    }

    public String getName() {
        return name;
    }

    public ArrayList<RegisterComponent> getComponents() {
        return components;
    }

    public int getComponentSize() {
        return componentSize;
    }

    private static void printUsage(OptionParser parser) throws IOException {
        parser.printHelpOn(System.out);
    }

    private static OptionParser createOptionParser() {
        OptionParser p = new OptionParser();
        p.accepts("out").withOptionalArg().defaultsTo("complex_test.dcp").describedAs("Output DCP file");
        p.accepts("help").forHelp();
        p.accepts("verbose");
        return p;
    }

    public static void main(String[] args) throws IOException {

        OptionParser parser = createOptionParser();
        OptionSet options = parser.parse(args);

        RouterLog.Level logLevel = (options.has("verbose")) ? RouterLog.Level.VERBOSE : RouterLog.Level.NORMAL;
        RouterLog.init(logLevel);

        if (options.has("help")) {
            printUsage(parser);
            return;
        }

        Design d = ComplexRegister.newDesignFromSources("complex_register_example");

        ArrayList<RegisterComponent> components = new ArrayList<RegisterComponent>();

        components.add(new RegisterComponent("comp_0_type_0", 0, "SLICE_X56Y120"));
        components.add(new RegisterComponent("comp_1_type_1", 1, "SLICE_X57Y120"));
        components.add(new RegisterComponent("comp_2_type_0", 0, "SLICE_X56Y121"));

        ComplexRegister reg = new ComplexRegister(d, "example_register", components);


        d.writeCheckpoint(ResourcesManager.OUTPUT_DIR + "complex_register_example.dcp");
    }
}
