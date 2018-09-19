import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Module;
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


    public static String partName;

    public static HashMap<Integer, ComplexRegModule> typeToRegModuleMap = new HashMap<Integer, ComplexRegModule>();


    public static Module newModuleFromDcp(String dcpFileName) {
        Design regDesign = Design.readCheckpoint(dcpFileName);

        partName = regDesign.getPartName();

        Module regModule = new Module(regDesign);
        regModule.setNetlist(regDesign.getNetlist());

        RouterLog.log("Initialized register module anchored at <"
                + regModule.getAnchor().getSiteName() + ">.", RouterLog.Level.VERBOSE);

        return regModule;
    }

    public static void loadRegModulesFromConfig() throws IOException {
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

            ComplexRegModule regModule = new ComplexRegModule(typeKey, bitWidth, inPIPNames, outPIPNames);
            regModule.setModule(newModuleFromDcp(ResourcesManager.COMPONENTS_DIR + typeKeyPrefix + typeKey + ".dcp"));
            typeToRegModuleMap.put(typeKey, regModule);

            typeKey += 1;
        }
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

        ComplexRegister.loadRegModulesFromConfig();

    }
}
