import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.edif.*;
import joptsimple.OptionParser;
import joptsimple.OptionSet;

import java.io.IOException;
import java.util.ArrayList;

public class RegisterPair {



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

        Design d = ComplexRegister.newDesignFromSources("reg_pair");

        EDIFCell top = d.getNetlist().getTopCell();
        EDIFPort clkPort = top.createPort(ComplexRegister.CLK_NAME, EDIFDirection.INPUT, 1);
        EDIFNet clk = top.createNet(ComplexRegister.CLK_NAME);
        clk.createPortRef(clkPort);

        ArrayList<RegisterComponent> reg1_components = new ArrayList<RegisterComponent>();
        reg1_components.add(new RegisterComponent(0, "SLICE_X56Y120"));
        reg1_components.add(new RegisterComponent(1, "SLICE_X57Y120"));
        reg1_components.add(new RegisterComponent(0, "SLICE_X56Y121"));
        ComplexRegister reg1 = new ComplexRegister(d, "reg1", reg1_components);

        ArrayList<RegisterComponent> reg2_components = new ArrayList<RegisterComponent>();
        reg2_components.add(new RegisterComponent(0, "SLICE_X56Y122"));
        reg2_components.add(new RegisterComponent(1, "SLICE_X57Y122"));
        reg2_components.add(new RegisterComponent(0, "SLICE_X56Y123"));
        ComplexRegister reg2 = new ComplexRegister(d, "reg2", reg2_components);

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

        CustomRouter.routeComplexRegisters(d, reg1, reg2);
    }
}
