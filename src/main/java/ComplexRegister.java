import com.xilinx.rapidwright.design.Design;
import org.ini4j.Wini;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;

public class ComplexRegister {

    public static String RESOURCES_DIR = "src/main/resources/";

    public static String COMPONENTS_DIR = RESOURCES_DIR + "components/";
    public static String CONFIG_FILE_NAME = RESOURCES_DIR + "complex_register.conf";

    public static String typeKeyPrefix = "type";
    public static String inPIPKeyPrefix = "inPIP";
    public static String outPIPKeyPrefix = "outPIP";

    public static String bwKey = "bw";


    public static HashMap<Integer, ComplexRegModule> typeToRegModuleMap = new HashMap<Integer, ComplexRegModule>();


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

            typeToRegModuleMap.put(typeKey, new ComplexRegModule(typeKey, bitWidth, inPIPNames, outPIPNames));

            typeKey += 1;
        }
    }

    public static void main(String[] args) throws IOException {
        loadRegModulesFromConfig();

    }
}
