import com.xilinx.rapidwright.dcp.CheckpointTools;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.ModuleInstance;
import com.xilinx.rapidwright.device.Site;
import com.xilinx.rapidwright.edif.*;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import org.ini4j.Wini;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class ComplexRegister {

    public static final String EXAMPLE_COMPONENTS_FILE_NAME = ResourcesManager.RESOURCES_DIR
            + "complex_register_example.conf";

    public static final String typeKeyPrefix = "type";
    public static final String inPIPKeyPrefix = "inPIP";
    public static final String outPIPKeyPrefix = "outPIP";

    public static final String bwKey = "bw";

    public static final String commonKey = "common";
    public static final String clkKey = "clkName";
    public static final String rstKey = "rstName";
    public static final String ceKey = "ceName";

    public static final String inKey = "inName";
    public static final String outKey = "outName";

    public static String CLK_NAME;
    public static String RST_NAME;
    public static String CE_NAME;

    public static String INPUT_NAME;
    public static String OUTPUT_NAME;

    public static HashMap<Integer, ComplexRegModule> typeToRegModuleMap = new HashMap<Integer, ComplexRegModule>();


    private static Design readDcp(String dcpFileName) {
        Design regDesign = Design.readCheckpoint(dcpFileName);
        ResourcesManager.setPartName(regDesign.getPartName());
        return regDesign;
    }

    public static void loadRegModulesFromConfig() throws IOException {
        ResourcesManager.initComponentsConfig();
        Wini ini = ResourcesManager.componentsConfig;

        CLK_NAME = ini.get(commonKey, clkKey);
        RST_NAME = ini.get(commonKey, rstKey);
        CE_NAME = ini.get(commonKey, ceKey);

        INPUT_NAME = ini.get(commonKey, inKey);
        OUTPUT_NAME = ini.get(commonKey, outKey);

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

    public static String getInPIPName(int type, int index) {
        return typeToRegModuleMap.get(type).getInPIPName(index);
    }

    public static String getOutPIPName(int type, int index) {
        return typeToRegModuleMap.get(type).getOutPIPName(index);
    }

    private String name;

    private ArrayList<RegisterComponent> components;
    private int componentSize;

    private int bitWidth;

    public ComplexRegister(Design d, String name, ArrayList<RegisterComponent> components) {

        this.name = name;

        this.components = components;
        this.componentSize = components.size();

        EDIFCell top = d.getNetlist().getTopCell();

        bitWidth = 0;

        int i = 0;
        for (RegisterComponent component : components) {
            ComplexRegModule regModule = ComplexRegister.typeToRegModuleMap.get(component.getType());

            if (!component.hasName())
                component.setName("component" + i++);
            EDIFCellInstance ci = top.createChildCellInstance(name + "_" + component.getName(),
                    regModule.getModule().getNetlist().getTopCell());
            ModuleInstance mi = d.createModuleInstance(name + "_" + component.getName(), regModule.getModule());
            mi.setCellInstance(ci);

            Site anchorSite = d.getDevice().getSite(component.getSiteName());
            mi.place(anchorSite);

            component.setModuleInstance(mi);

            top.getNet(ComplexRegister.CLK_NAME).createPortRef(ComplexRegister.CLK_NAME, ci);

            RouterLog.log("Placed component " + component.toString() + " for <" + name + "> at site <"
                            + component.getSiteName() + ">.", RouterLog.Level.INFO);

            bitWidth += regModule.getBitWidth();
        }
    }

    public String getName() {
        return name;
    }

    public ArrayList<RegisterComponent> getComponents() {
        return components;
    }

    public RegisterComponent getComponent(int index) {
        return components.get(index);
    }

    public int getComponentSize() {
        return componentSize;
    }

    public int getBitWidth() {
        return bitWidth;
    }

    public void createInputEDIFPortRefs(Design d, String netPrefix) {

        EDIFCell top = d.getNetlist().getTopCell();

        int i = 0;
        for (RegisterComponent component : components) {
            for (int j = 0; j < component.getBitWidth(); j++, i++) {
                EDIFNet net = top.getNet(netPrefix + "[" + i + "]");
                if (net == null)
                    net = top.createNet(netPrefix + "[" + i + "]");
                net.createPortRef(ComplexRegister.INPUT_NAME, j, component.getCellInstance());
            }
        }
    }

    public void createInputEDIFPortRefs(Design d, String netPrefix, int startBit, int endBit, int indexOffset) {

        EDIFCell top = d.getNetlist().getTopCell();

        int i = 0;
        for (RegisterComponent component : components) {
            for (int j = 0; j < component.getBitWidth(); j++, i++) {
                if (i >= startBit && i <= endBit) {
                    EDIFNet net = top.getNet(netPrefix + "[" + (i + indexOffset) + "]");
                    if (net == null)
                        net = top.createNet(netPrefix + "[" + (i + indexOffset) + "]");
                    net.createPortRef(ComplexRegister.INPUT_NAME, j, component.getCellInstance());
                }
            }
        }
    }

    public void createOutputEDIFPortRefs(Design d, String netPrefix) {

        EDIFCell top = d.getNetlist().getTopCell();

        int i = 0;
        for (RegisterComponent component : components) {
            for (int j = 0; j < component.getBitWidth(); j++, i++) {
                EDIFNet net = top.getNet(netPrefix + "[" + i + "]");
                if (net == null)
                    net = top.createNet(netPrefix + "[" + i + "]");
                net.createPortRef(ComplexRegister.OUTPUT_NAME, j, component.getCellInstance());
            }
        }

    }

    public void createOutputEDIFPortRefs(Design d, String netPrefix, int startBit, int endBit, int indexOffset) {

        EDIFCell top = d.getNetlist().getTopCell();

        int i = 0;
        for (RegisterComponent component : components) {
            for (int j = 0; j < component.getBitWidth(); j++, i++) {
                if (i >= startBit && i <= endBit) {
                    EDIFNet net = top.getNet(netPrefix + "[" + (i + indexOffset) + "]");
                    if (net == null)
                        net = top.createNet(netPrefix + "[" + (i + indexOffset) + "]");
                    net.createPortRef(ComplexRegister.OUTPUT_NAME, j, component.getCellInstance());
                }
            }
        }

    }

    @Override
    public String toString() {
        return "<" + name + ">[" + bitWidth + "b]";
    }

    private static void printUsage(OptionParser parser) throws IOException {
        System.out.println("java ComplexRegister [-h] [-v] [--out OUT_FILE_NAME]\n");
        System.out.println("  Create a complex register of 3 modules at SLICE_X56Y120, SLICE_X57Y120, SLICE_X56Y121.\n");
        parser.printHelpOn(System.out);
    }

    private static OptionParser createOptionParser() {
        OptionParser p = new OptionParser();
        p.accepts("out").withOptionalArg().defaultsTo("complex_register_example.dcp").describedAs("Output DCP file");
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

        ResourcesManager.COMPONENTS_FILE_NAME = EXAMPLE_COMPONENTS_FILE_NAME;
        ResourcesManager.initComponentsConfig();

        Design d = ResourcesManager.newDesignFromSources("complex_register_example");

        EDIFCell top = d.getNetlist().getTopCell();
        EDIFPort clkPort = top.createPort(ComplexRegister.CLK_NAME, EDIFDirection.INPUT, 1);
        EDIFNet clk = top.createNet(ComplexRegister.CLK_NAME);
        clk.createPortRef(clkPort);

        ArrayList<RegisterComponent> components = new ArrayList<RegisterComponent>();
        components.add(new RegisterComponent(0, "SLICE_X56Y120"));
        //components.add(new RegisterComponent(1, "SLICE_X57Y120"));
        components.add(new RegisterComponent(0, "SLICE_X56Y121"));
        ComplexRegister reg = new ComplexRegister(d, "example_register", components);

        d.writeCheckpoint(ResourcesManager.OUTPUT_DIR + options.valueOf("out"));
    }
}
