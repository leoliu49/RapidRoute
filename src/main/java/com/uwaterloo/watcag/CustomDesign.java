package com.uwaterloo.watcag;

import com.uwaterloo.watcag.common.ComplexRegister;
import com.uwaterloo.watcag.common.RegisterConnection;
import com.uwaterloo.watcag.config.ComplexRegModule;
import com.uwaterloo.watcag.config.RegisterComponent;
import com.uwaterloo.watcag.config.RegisterDefaults;
import com.uwaterloo.watcag.config.ResourcesManager;
import com.uwaterloo.watcag.placer.DesignPlacer;
import com.uwaterloo.watcag.router.*;
import com.uwaterloo.watcag.router.browser.FabricBrowser;
import com.uwaterloo.watcag.router.elements.CustomRoute;
import com.uwaterloo.watcag.router.elements.EnterWireJunction;
import com.uwaterloo.watcag.router.elements.ExitWireJunction;
import com.uwaterloo.watcag.router.elements.TilePath;
import com.uwaterloo.watcag.util.RouterLog;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.edif.EDIFCell;
import com.xilinx.rapidwright.edif.EDIFLibrary;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

import java.io.IOException;
import java.util.*;

public class CustomDesign {

    /*
     * Top level class which does everything
     */

    private static int numJobs;
    private static Design coreDesign;
    private static final HashMap<String, ComplexRegister> registers = new HashMap<>();
    private static final ArrayList<RegisterConnection> connections = new ArrayList<>();

    private static int srcNetPortIndex = 0;
    private static int resNetPortIndex = 0;


    /*
     * API functions exposed to jython
     */
    public static void init(int numJobs) {
        CustomDesign.numJobs = numJobs;
        RouterLog.init(RouterLog.Level.INFO);
    }

    public static Design newDesign(String designName, String partName) {

        if (coreDesign != null)
            reset();

        coreDesign = new Design(designName, partName);
        coreDesign.setAutoIOBuffers(false);

        RouterLog.log("Initiating new design <" + designName + "> for part <" + coreDesign.getPartName() + ">.",
                RouterLog.Level.NORMAL);

        DesignPlacer.initializePlacer(coreDesign);
        DesignPlacer.createTopLevelClk();

        DesignRouter.initializeRouter(coreDesign, numJobs);

        return coreDesign;
    }

    public static void reset() {

        RouterLog.log("Resetting design <" + coreDesign.getName() + ">.", RouterLog.Level.NORMAL);

        coreDesign = null;
        DesignPlacer.reset();
        DesignRouter.reset();
        registers.clear();
        connections.clear();

        srcNetPortIndex = 0;
        resNetPortIndex = 0;
    }

    public static void addModule(String dcpFilePath, int bitwidth, String[] inPIPNames, String[] outPIPNames) {
        String[] pathTokens = dcpFilePath.split("/");
        String fileName = pathTokens[pathTokens.length - 1].replace("\\.dcp", "");

        ComplexRegModule module = new ComplexRegModule(fileName, bitwidth, inPIPNames, outPIPNames,
                ResourcesManager.readDcp(dcpFilePath, coreDesign.getPartName()));

        if (!module.getSrcDesign().getPartName().equals(coreDesign.getPartName()))
            throw new DesignFailureException("Module DCP is using a different Xilinx part.");

        Design regDesign = module.getSrcDesign();
        for (EDIFCell cell : regDesign.getNetlist().getWorkLibrary().getCells()) {
            cell.rename("__" + module.getParentDcp() + "_" + cell.getName());
            coreDesign.getNetlist().getWorkLibrary().addCell(cell);
        }
        EDIFLibrary hdi = coreDesign.getNetlist().getHDIPrimitivesLibrary();
        for (EDIFCell cell : regDesign.getNetlist().getHDIPrimitivesLibrary().getCells()) {
            if (!hdi.containsCell(cell)) hdi.addCell(cell);
        }

        RegisterDefaults.dcpFileToRegModuleMap.put(fileName, module);
    }

    public static ArrayList<String> getAllValidPlacements(String dcpModuleName) {
        String[] pathTokens = dcpModuleName.split("/");
        String moduleName = pathTokens[pathTokens.length - 1].replace("\\.dcp", "");

        return RegisterDefaults.dcpFileToRegModuleMap.get(moduleName).getAllValidSitePlacements();
    }

    public static void loadModulesFromTemplate(String dirPath) {
        dirPath = dirPath.endsWith("/") ? dirPath : dirPath + "/";
        ResourcesManager.COMPONENTS_FILE_NAME = dirPath + "components.conf";
        ResourcesManager.resetConfigs();

        ResourcesManager.loadRegModulesFromConfig(coreDesign, dirPath);
    }

    public static RegisterComponent createNewComponent(String parentDcp, String siteName) {
        return new RegisterComponent(parentDcp.replace("\\.dcp", ""), siteName);
    }

    public static void addNewComplexRegister(String name, RegisterComponent[] components) {
        ComplexRegister register = new ComplexRegister(name, new ArrayList<>(Arrays.asList(components)));
        registers.put(name, register);
        DesignPlacer.prepareNewRegisterForPlacement(register);
    }

    public static void addNewInputConnection(String snkRegName) {
        RegisterConnection connection = new RegisterConnection(null, registers.get(snkRegName),
                srcNetPortIndex, srcNetPortIndex + registers.get(snkRegName).getBitWidth() - 1,
                0, registers.get(snkRegName).getBitWidth() - 1);
        connections.add(connection);

        srcNetPortIndex += connection.getBitWidth();
        DesignRouter.prepareNewConnectionForRouting(connection);
    }

    public static void addNewInputConnection(String snkRegName, int snkRegLowestBit, int snkRegHighestBit) {
        RegisterConnection connection = new RegisterConnection(null, registers.get(snkRegName),
                srcNetPortIndex, srcNetPortIndex + snkRegHighestBit - snkRegLowestBit + 1,
                snkRegLowestBit, snkRegHighestBit);
        connections.add(connection);

        srcNetPortIndex += connection.getBitWidth();
        DesignRouter.prepareNewConnectionForRouting(connection);
    }

    public static void addNewOutputConnection(String srcRegName) {
        RegisterConnection connection = new RegisterConnection(registers.get(srcRegName), null,
                0, registers.get(srcRegName).getBitWidth() - 1,
                resNetPortIndex, resNetPortIndex + registers.get(srcRegName).getBitWidth() - 1);
        connections.add(connection);

        resNetPortIndex += connection.getBitWidth();
        DesignRouter.prepareNewConnectionForRouting(connection);
    }

    public static void addNewOutputConnection(String srcRegName, int srcRegLowestBit, int srcRegHighestBit) {
        RegisterConnection connection = new RegisterConnection(registers.get(srcRegName), null,
                srcRegLowestBit, srcRegHighestBit,
                resNetPortIndex, resNetPortIndex + srcRegHighestBit - srcRegLowestBit + 1);
        connections.add(connection);

        resNetPortIndex += connection.getBitWidth();
        DesignRouter.prepareNewConnectionForRouting(connection);
    }

    public static void addNewRegisterConnection(String srcRegName, String snkRegName) {
        RegisterConnection connection = new RegisterConnection(registers.get(srcRegName), registers.get(snkRegName),
                0, registers.get(srcRegName).getBitWidth() - 1, 0, registers.get(snkRegName).getBitWidth() - 1);
        connections.add(connection);
        DesignRouter.prepareNewConnectionForRouting(connection);
    }

    public static void addNewRegisterConnection(String srcRegName, String snkRegName, int srcRegLowestBit,
                                                int srcRegHighestBit, int snkRegLowestBit, int snkRegHighestBit) {
        RegisterConnection connection = new RegisterConnection(registers.get(srcRegName), registers.get(snkRegName),
                srcRegLowestBit, srcRegHighestBit, snkRegLowestBit, snkRegHighestBit);
        connections.add(connection);
        DesignRouter.prepareNewConnectionForRouting(connection);
    }

    public static void placeDesign() {
        DesignPlacer.place();
    }

    public static void netSynthesis() {
        DesignRouter.createNetsForConnections();
    }

    public static void routeDesign() throws Exception {
        DesignRouter.routeDesign();
    }

    public static void writeCheckpoint(String name) {
        if (!name.endsWith(".dcp"))
            name += ".dcp";
        coreDesign.writeCheckpoint(ResourcesManager.OUTPUT_DIR + name);
    }




    /*
     * Main function for entire design
     */

    private static void printUsage(OptionParser parser) throws IOException {
        System.out.println("java com.uwaterloo.watcag.CustomDesign [-h] [-v] [--example] [--name DESIGN_NAME] [--out OUT_FILE_NAME] [--jobs NUM_JOBS]\n");
        System.out.println("  Create and route a design based on placements.conf and routes.conf. Uses register_pair_example.conf and routes_example.conf instead if --example is specified.\n");
        parser.printHelpOn(System.out);
    }

    private static OptionParser createOptionParser() {
        OptionParser p = new OptionParser();
        p.accepts("name").withOptionalArg().defaultsTo("custom_design").describedAs("Name of design");
        p.accepts("part").withOptionalArg().defaultsTo("xcku5p-ffvb676-2-e").describedAs("Part name");
        p.accepts("out").withOptionalArg().defaultsTo("custom_design").describedAs("Prefix for output *_unrouted.dcp, *_custom_routed.dcp");
        p.accepts("jobs").withOptionalArg().defaultsTo("4").describedAs("Number of concurrent jobs for routing");
        p.accepts("help").forHelp();
        p.accepts("verbose");
        return p;
    }

    public static void main(String[] args) throws Exception {

        //System.out.println("Main entry point temporarily disabled. Please use the launch script instead.");

        /*
        OptionParser parser = createOptionParser();
        OptionSet options = parser.parse(args);

        RouterLog.Level logLevel = (options.has("verbose")) ? RouterLog.Level.VERBOSE : RouterLog.Level.INFO;
        RouterLog.init(logLevel);

        if (options.has("help")) {
            printUsage(parser);
            return;
        }

        ResourcesManager.resetConfigs();

        coreDesign = new Design((String) options.valueOf("name"), (String) options.valueOf("part"));
        coreDesign.setAutoIOBuffers(false);

        RouterLog.log("Initiating new design <" + coreDesign.getName() + "> for part <" + coreDesign.getPartName() + ">.",
                RouterLog.Level.NORMAL);

        ResourcesManager.loadRegModulesFromConfig(coreDesign, ResourcesManager.COMPONENTS_DIR);

        HashMap<String, ComplexRegister> registers = ResourcesManager.registersFromPlacements(coreDesign);
        ArrayList<RegisterConnection> connections = ResourcesManager.connectionsFromRoutes(coreDesign, registers);

        // Placement
        DesignPlacer.initializePlacer(coreDesign);
        DesignPlacer.createTopLevelClk();
        for (ComplexRegister register : registers.values()) {
            DesignPlacer.prepareNewRegisterForPlacement(register);
        }

        DesignPlacer.place();


        // Routing
        DesignRouter.initializeRouter(coreDesign, Integer.valueOf((String) options.valueOf("jobs")));
        for (RegisterConnection connection : connections) {
            DesignRouter.prepareNewConnectionForRouting(connection);
        }
        DesignRouter.createNetsForConnections();

        coreDesign.writeCheckpoint(ResourcesManager.OUTPUT_DIR + options.valueOf("out") + "_unrouted.dcp");

        DesignRouter.routeDesign();

        coreDesign.writeCheckpoint(ResourcesManager.OUTPUT_DIR + options.valueOf("out") + "_custom_routed.dcp");
        */

    }
}
