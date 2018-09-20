import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.edif.*;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class CustomDesign {

    /*
     * Top level class which does everything
     */

    public static final String EXAMPLE_ROUTES_FILE_NAME = ResourcesManager.RESOURCES_DIR
            + "routes_example.conf";

    private static void printUsage(OptionParser parser) throws IOException {
        System.out.println("java CustomDesign [-h] [-v] [--example] [--name DESIGN_NAME] [--out OUT_FILE_NAME]\n");
        System.out.println("  Create and route a design based on placements.conf and routes.conf. Uses routes_example.conf instead if --example is specified.\n");
        parser.printHelpOn(System.out);
    }

    private static OptionParser createOptionParser() {
        OptionParser p = new OptionParser();
        p.accepts("name").withOptionalArg().defaultsTo("custom_design")
                .describedAs("Name of design");
        p.accepts("out").withOptionalArg().defaultsTo("custom_design")
                .describedAs("Prefix for output *_unrouted.dcp, *_custom_routed.dcp");
        p.accepts("help").forHelp();
        p.accepts("verbose");
        p.accepts("example");
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

        if (options.has("example")) {
            ResourcesManager.COMPONENTS_FILE_NAME = ComplexRegister.EXAMPLE_COMPONENTS_FILE_NAME;
            ResourcesManager.PLACEMENTS_FILE_NAME = RegisterPair.EXAMPLE_PLACEMENTS_FILE_NAME;
            ResourcesManager.ROUTES_FILE_NAME = EXAMPLE_ROUTES_FILE_NAME;
        }

        ResourcesManager.initPlacementsConfig();
        ResourcesManager.initRoutesConfig();
        ResourcesManager.initComponentsConfig();

        Design d = ResourcesManager.newDesignFromSources((String) options.valueOf("name"));

        EDIFCell top = d.getNetlist().getTopCell();
        EDIFPort clkPort = top.createPort(ComplexRegister.CLK_NAME, EDIFDirection.INPUT, 1);
        EDIFNet clk = top.createNet(ComplexRegister.CLK_NAME);
        clk.createPortRef(clkPort);

        HashMap<String, ComplexRegister> registers = ResourcesManager.registersFromPlacements(d);

        ArrayList<RegisterConnection> connections = ResourcesManager.connectionsFromRoutes(d, registers);

        int inBitWidth = 0;
        int outBitWidth = 0;
        int interIndex = 0;
        for (RegisterConnection connection : connections) {
            if (connection.isInputConnection()) {
                inBitWidth += connection.getBitWidth();
            }
            else if (connection.isOutputConnection()) {
                outBitWidth += connection.getBitWidth();
            }
            else {
                connection.getSrcReg().createOutputEDIFPortRefs(d, "inter" + interIndex, connection.getSrcRegLowestBit(),
                        connection.getSrcRegHighestBit(), 0);
                connection.getSnkReg().createInputEDIFPortRefs(d, "inter" + interIndex, connection.getSnkRegLowestBit(),
                        connection.getSnkRegHighestBit(), 0);
                interIndex += 1;
            }
        }

        EDIFPortRef[] srcPortRefs = EDIFTools.createPortRefs(top, "src", EDIFDirection.INPUT, inBitWidth);
        EDIFPortRef[] resPortRefs = EDIFTools.createPortRefs(top, "res", EDIFDirection.OUTPUT, outBitWidth);
        int srcIndex = 0;
        int resIndex = 0;
        for (RegisterConnection connection : connections) {
            if (connection.isInputConnection()) {
                connection.getSnkReg().createInputEDIFPortRefs(d, "src", connection.getSnkRegLowestBit(),
                        connection.getSnkRegHighestBit(), srcIndex);
                srcIndex += connection.getBitWidth();
            }
            else if (connection.isOutputConnection()) {
                connection.getSrcReg().createOutputEDIFPortRefs(d, "res", connection.getSrcRegLowestBit(),
                        connection.getSrcRegHighestBit(), resIndex);
                resIndex += connection.getBitWidth();
            }
        }

        for (int i = 0; i < inBitWidth; i++) {
            EDIFNet srcNet = top.getNet("src[" + i + "]");
            srcNet.addPortRef(srcPortRefs[i]);
        }

        for (int i = 0; i < outBitWidth; i++) {
            EDIFNet resNet = top.getNet("res[" + i + "]");
            resNet.addPortRef(resPortRefs[i]);
        }

        d.writeCheckpoint(ResourcesManager.OUTPUT_DIR + options.valueOf("out") + "_unrouted.dcp");

        for (RegisterConnection connection : connections) {
            if (!connection.isInputConnection() && !connection.isOutputConnection())
                CustomRouter.routeConnection(d, connection).commit(d);
        }

        d.writeCheckpoint(ResourcesManager.OUTPUT_DIR + options.valueOf("out") + "_custom_routed.dcp");

    }
}
