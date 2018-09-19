import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.edif.*;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

import java.io.IOException;
import java.util.ArrayList;

public class RegisterPair {

    public static final String EXAMPLE_PLACEMENTS_FILE_NAME = ResourcesManager.RESOURCES_DIR
            + "register_pair_example.conf";

    private static void printUsage(OptionParser parser) throws IOException {
        parser.printHelpOn(System.out);
    }

    private static OptionParser createOptionParser() {
        OptionParser p = new OptionParser();
        p.accepts("out").withOptionalArg().defaultsTo("reg_pair")
                .describedAs("Prefix for output *_unrouted.dcp, *_custom_routed.dcp");
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

        ResourcesManager.COMPONENTS_FILE_NAME = ComplexRegister.EXAMPLE_COMPONENTS_FILE_NAME;
        ResourcesManager.PLACEMENTS_FILE_NAME = EXAMPLE_PLACEMENTS_FILE_NAME;
        ResourcesManager.initComponentsConfig();
        ResourcesManager.initPlacementsConfig();

        Design d = ResourcesManager.newDesignFromSources("reg_pair");

        EDIFCell top = d.getNetlist().getTopCell();
        EDIFPort clkPort = top.createPort(ComplexRegister.CLK_NAME, EDIFDirection.INPUT, 1);
        EDIFNet clk = top.createNet(ComplexRegister.CLK_NAME);
        clk.createPortRef(clkPort);

        ArrayList<ComplexRegister> registers = ResourcesManager.registersFromPlacements(d);

        ComplexRegister reg1 = registers.get(0);
        ComplexRegister reg2 = registers.get(1);

        int bitWidth = reg1.getBitWidth();

        EDIFPortRef[] srcPortRefs = EDIFTools.createPortRefs(top, "src", EDIFDirection.INPUT, bitWidth);
        EDIFPortRef[] resPortRefs = EDIFTools.createPortRefs(top, "res", EDIFDirection.OUTPUT, bitWidth);

        for (int i = 0; i < bitWidth; i++) {
            EDIFNet srcNet = top.createNet("src[" + i + "]");
            srcNet.addPortRef(srcPortRefs[i]);

            EDIFNet resNet = top.createNet("res[" + i + "]");
            resNet.addPortRef(resPortRefs[i]);
        }

        reg1.createInputEDIFPortRefs(d, "src");
        reg1.createOutputEDIFPortRefs(d, "mid");
        reg2.createInputEDIFPortRefs(d, "mid");
        reg2.createOutputEDIFPortRefs(d, "res");

        d.writeCheckpoint(ResourcesManager.OUTPUT_DIR + options.valueOf("out") + "_unrouted.dcp");

        RoutingFootprint footprint = CustomRouter.routeComplexRegisters(d, reg1, reg2);

        footprint.commit(d);

        d.writeCheckpoint(ResourcesManager.OUTPUT_DIR + options.valueOf("out") + "_custom_routed.dcp");
    }
}
