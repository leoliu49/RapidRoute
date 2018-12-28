package com.uwaterloo.watcag.common;

import com.uwaterloo.watcag.config.ComplexRegModule;
import com.uwaterloo.watcag.config.RegisterComponent;
import com.uwaterloo.watcag.config.RegisterDefaults;
import com.uwaterloo.watcag.config.ResourcesManager;
import com.uwaterloo.watcag.placer.DesignPlacer;
import com.uwaterloo.watcag.util.RouterLog;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.ModuleInst;

import com.xilinx.rapidwright.device.Site;
import com.xilinx.rapidwright.edif.*;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

import java.io.IOException;
import java.util.ArrayList;

public class ComplexRegister {

    public static final String EXAMPLE_COMPONENTS_FILE_NAME = ResourcesManager.RESOURCES_DIR
            + "complex_register_example.conf";

    private String name;

    private ArrayList<RegisterComponent> components;
    private int componentSize;

    private int bitWidth;

    public ComplexRegister(Design d, String name, ArrayList<RegisterComponent> components) {

        this.name = name;

        this.components = components;
        componentSize = components.size();

        bitWidth = 0;

        int i = 0;
        for (RegisterComponent component : components) {
            ComplexRegModule regModule = RegisterDefaults.dcpFileToRegModuleMap.get(component.getParentDcp());

            if (!component.hasName())
                component.setName("component" + i++);

            bitWidth += regModule.getBitWidth();
        }
    }

    /*
     * Permanently associates register with given design
     */
    public void populateAndPlace(Design d) {

        EDIFCell top = d.getNetlist().getTopCell();

        for (RegisterComponent component : components) {
            ComplexRegModule regModule = RegisterDefaults.dcpFileToRegModuleMap.get(component.getParentDcp());

            EDIFCellInst ci = top.createChildCellInst(name + "_" + component.getName(),
                    regModule.getModule().getNetlist().getTopCell());
            ModuleInst mi = d.createModuleInst(name + "_" + component.getName(), regModule.getModule());
            mi.setCellInst(ci);

            Site anchorSite = d.getDevice().getSite(component.getSiteName());
            mi.place(anchorSite);

            component.setModuleInstance(mi);

            top.getNet(RegisterDefaults.CLK_NAME).createPortInst(RegisterDefaults.CLK_NAME, ci);

            RouterLog.log("Placed component " + component.toString() + " for <" + name + "> at site <"
                    + component.getSiteName() + ">.", RouterLog.Level.INFO);
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
                net.createPortInst(RegisterDefaults.INPUT_NAME, j, component.getCellInstance());
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
                    net.createPortInst(RegisterDefaults.INPUT_NAME, j, component.getCellInstance());
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
                net.createPortInst(RegisterDefaults.OUTPUT_NAME, j, component.getCellInstance());
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
                    net.createPortInst(RegisterDefaults.OUTPUT_NAME, j, component.getCellInstance());
                }
            }
        }

    }

    @Override
    public String toString() {
        return "<" + name + ">[" + bitWidth + "b]";
    }

    private static void printUsage(OptionParser parser) throws IOException {
        System.out.println("java com.uwaterloo.watcag.common.ComplexRegister [-h] [-v] [--out OUT_FILE_NAME]\n");
        System.out.println("  Create a complex register of 3 modules at SLICE_X56Y120, SLICE_X57Y120, SLICE_X56Y121.\n");
        parser.printHelpOn(System.out);
    }

    private static OptionParser createOptionParser() {
        OptionParser p = new OptionParser();
        p.accepts("out").withOptionalArg().defaultsTo("complex_register_example.dcp")
                .describedAs("Output DCP file name");
        p.accepts("help").forHelp();
        p.accepts("verbose");
        return p;
    }

    public static void main(String[] args) throws IOException {

        OptionParser parser = createOptionParser();
        OptionSet options = parser.parse(args);

        RouterLog.Level logLevel = (options.has("verbose")) ? RouterLog.Level.VERBOSE : RouterLog.Level.INFO;
        RouterLog.init(logLevel);

        if (options.has("help")) {
            printUsage(parser);
            return;
        }

        ResourcesManager.COMPONENTS_FILE_NAME = EXAMPLE_COMPONENTS_FILE_NAME;
        ResourcesManager.initConfigs();

        Design d = ResourcesManager.newDesignFromSources("complex_register_example");

        DesignPlacer.initializePlacer(d);
        DesignPlacer.createTopLevelClk();

        ArrayList<RegisterComponent> components = new ArrayList<>();
        components.add(new RegisterComponent("type6", "SLICE_X56Y120"));
        components.add(new RegisterComponent("type7", "SLICE_X57Y120"));
        components.add(new RegisterComponent("type6", "SLICE_X56Y121"));

        ComplexRegister reg = new ComplexRegister(d, "example_register", components);
        reg.populateAndPlace(d);

        d.writeCheckpoint(ResourcesManager.OUTPUT_DIR + options.valueOf("out"));
    }
}
