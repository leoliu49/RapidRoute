package com.uwaterloo.watcag.config;

import com.uwaterloo.watcag.DesignFailureException;
import com.uwaterloo.watcag.common.ComplexRegister;
import com.uwaterloo.watcag.common.RegisterConnection;
import com.uwaterloo.watcag.util.RouterLog;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.edif.EDIFCell;
import com.xilinx.rapidwright.edif.EDIFLibrary;
import org.ini4j.Wini;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ResourcesManager {

    /*
     * Management class for DCPs, config files, and outputs
     */

    public static final String RESOURCES_DIR = "src/main/resources/";
    public static final String COMPONENTS_DIR = RESOURCES_DIR + "components/";
    public static final String OUTPUT_DIR = "output/";

    public static String COMPONENTS_FILE_NAME = ResourcesManager.RESOURCES_DIR + "register_components.conf";
    public static String PLACEMENTS_FILE_NAME = ResourcesManager.RESOURCES_DIR + "placements.conf";
    public static String ROUTES_FILE_NAME = ResourcesManager.RESOURCES_DIR + "routes.conf";

    public static String PART_NAME = null;

    public static Wini componentsConfig = null;
    public static Wini placementsConfig = null;
    public static BufferedReader routesConfig = null;

    // INI keys for componentsConfig
    public static final String commonKey = "common";
    public static final String clkKey = "clkName";
    public static final String rstKey = "rstName";
    public static final String ceKey = "ceName";
    public static final String inKey = "inName";
    public static final String outKey = "outName";
    public static final String bwKey = "bw";
    public static final String inPIPKeyPrefix = "inPIP";
    public static final String outPIPKeyPrefix = "outPIP";


    // INI keys for placementsConfig
    public static final String componentKeyPrefix = "comp";


    private static Design readDcp(String dcpFileName) {
        Design regDesign = Design.readCheckpoint(dcpFileName);
        if (PART_NAME == null)
            PART_NAME = regDesign.getPartName();
        else if (!PART_NAME.equals(regDesign.getPartName())) {
            throw new DesignFailureException("Component DCPs are using different Xilinx parts.");
        }

        return regDesign;
    }

    private static void initComponentsConfig() throws IOException {
        if (componentsConfig == null)
            componentsConfig = new Wini(new File(COMPONENTS_FILE_NAME));
    }

    private static void initPlacementsConfig() throws IOException {
        if (placementsConfig == null)
            placementsConfig = new Wini(new File(PLACEMENTS_FILE_NAME));
    }

    private static void initRoutesConfig() throws IOException {
        if (routesConfig == null)
            routesConfig = new BufferedReader(new FileReader(ROUTES_FILE_NAME));
    }

    public static boolean initConfigs() {
        try {
            initComponentsConfig();
            initPlacementsConfig();
            initRoutesConfig();

        } catch (IOException e) {
            throw new DesignFailureException("Configuration parser initialization failed.\n" + e.getMessage());
        }

        return true;
    }

    private static void loadRegModulesFromConfig() {
        Wini ini = componentsConfig;

        RegisterDefaults.CLK_NAME = ini.get(commonKey, clkKey);
        RegisterDefaults.RST_NAME = ini.get(commonKey, rstKey);
        RegisterDefaults.CE_NAME = ini.get(commonKey, ceKey);

        RegisterDefaults.INPUT_NAME = ini.get(commonKey, inKey);
        RegisterDefaults.OUTPUT_NAME = ini.get(commonKey, outKey);

        Set<String> dcpNameKeys = new HashSet<>(ini.keySet());
        dcpNameKeys.remove(commonKey);


        for (String dcp : dcpNameKeys) {
            int bitWidth = Integer.valueOf(ini.get(dcp, bwKey));

            int inPIPKey = 0;
            ArrayList<String> inPIPNames = new ArrayList<>();
            while (ini.get(dcp).containsKey(inPIPKeyPrefix + inPIPKey)) {
                inPIPNames.add(ini.get(dcp, inPIPKeyPrefix + inPIPKey));
                inPIPKey += 1;
            }

            int outPIPKey = 0;
            ArrayList<String> outPIPNames = new ArrayList<>();
            while (ini.get(dcp).containsKey(outPIPKeyPrefix + outPIPKey)) {
                outPIPNames.add(ini.get(dcp, outPIPKeyPrefix + outPIPKey));
                outPIPKey += 1;
            }

            String fileName = dcp.endsWith(".dcp") ? dcp : dcp + ".dcp";
            dcp = dcp.replace("\\.dcp", "_dcp");

            ComplexRegModule regModule = new ComplexRegModule(dcp, bitWidth, inPIPNames, outPIPNames,
                    readDcp(ResourcesManager.COMPONENTS_DIR + fileName));
            RegisterDefaults.dcpFileToRegModuleMap.put(dcp, regModule);
        }
    }

    public static Design newDesignFromSources(String designName) {
        loadRegModulesFromConfig();
        Design d = new Design(designName, ResourcesManager.PART_NAME);
        d.setAutoIOBuffers(false);

        RouterLog.log("Initiating new design <" + designName + "> for part <" + d.getPartName() + ">.",
                RouterLog.Level.NORMAL);

        for (ComplexRegModule module : RegisterDefaults.dcpFileToRegModuleMap.values()) {
            Design regDesign = module.getSrcDesign();
            for (EDIFCell cell : regDesign.getNetlist().getWorkLibrary().getCells()) {
                cell.rename("__" + module.getParentDcp() + "_" + cell.getName());
                d.getNetlist().getWorkLibrary().addCell(cell);
            }
            EDIFLibrary hdi = d.getNetlist().getHDIPrimitivesLibrary();
            for (EDIFCell cell : regDesign.getNetlist().getHDIPrimitivesLibrary().getCells()) {
                if (!hdi.containsCell(cell)) hdi.addCell(cell);
            }
        }

        return d;
    }

    public static HashMap<String, ComplexRegister> registersFromPlacements(Design d) {
        HashMap<String, ComplexRegister> registersMap = new HashMap<>();

        //int bitWidth = Integer.valueOf(placementsConfig.get(commonKey, bwKey));

        Set<String> regNameKeys = new HashSet<>(placementsConfig.keySet());

        for (String regName : regNameKeys) {
            ArrayList<RegisterComponent> components = new ArrayList<>();

            int compKey = 0;
            while (placementsConfig.get(regName).containsKey(componentKeyPrefix + compKey)) {
                String compInfo = placementsConfig.get(regName).get(componentKeyPrefix + compKey);

                compInfo = compInfo.replaceAll(" ", "");
                String[] compInfoArray = compInfo.split(",");

                String compDcp = compInfoArray[0];
                String siteName = compInfoArray[1];

                components.add(new RegisterComponent(compDcp, siteName));

                compKey += 1;
            }

            registersMap.put(regName, new ComplexRegister(d, regName, components));
        }

        return registersMap;
    }

    private static Matcher extractRegisterInfo(String str) {
        Matcher matcher = Pattern.compile("(.*)\\[(\\d+)\\.\\.(\\d+)]").matcher(str);
        if (matcher.find())
            return matcher;
        return null;
    }

    public static ArrayList<RegisterConnection> connectionsFromRoutes(Design d, HashMap<String,
            ComplexRegister> registersMap) throws IOException {
        ArrayList<RegisterConnection> connections = new ArrayList<>();

        for(String line; (line = routesConfig.readLine()) != null;) {
            line = line.replaceAll(" ", "");
            if (line.isEmpty() || line.startsWith("#"))
                continue;

            String[] elements = line.split("#")[0].split("<=");

            String dest = elements[0];
            Matcher mDest = extractRegisterInfo(dest);

            String src = elements[1];
            Matcher mSrc = extractRegisterInfo(src);

            if (mDest.group(1).equals("out"))
                connections.add(new RegisterConnection(registersMap.get(mSrc.group(1)), null,
                        Integer.valueOf(mSrc.group(3)), Integer.valueOf(mSrc.group(2)), 0, 0));
            else if (mSrc.group(1).equals("in"))
                connections.add(new RegisterConnection(null, registersMap.get(mDest.group(1)),
                        0, 0, Integer.valueOf(mDest.group(3)), Integer.valueOf(mDest.group(2))));
            else
                connections.add(new RegisterConnection(registersMap.get(mSrc.group(1)), registersMap.get(mDest.group(1)),
                        Integer.valueOf(mSrc.group(3)), Integer.valueOf(mSrc.group(2)),
                        Integer.valueOf(mDest.group(3)), Integer.valueOf(mDest.group(2))));
        }

        return connections;
    }

}
