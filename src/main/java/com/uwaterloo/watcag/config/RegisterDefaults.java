package com.uwaterloo.watcag.config;

import java.util.ArrayList;
import java.util.HashMap;

public class RegisterDefaults {

    public static String CLK_NAME;
    public static String RST_NAME;
    public static String CE_NAME;

    public static String INPUT_NAME;
    public static String OUTPUT_NAME;

    public static final HashMap<String, ComplexRegModule> dcpFileToRegModuleMap = new HashMap<>();

    public static ArrayList<String> getInPIPNames(String dcp) {
        return RegisterDefaults.dcpFileToRegModuleMap.get(dcp).getInPIPNames();
    }

    public static String getInPIPName(String dcp, int index) {
        return RegisterDefaults.dcpFileToRegModuleMap.get(dcp).getInPIPName(index);
    }

    public static ArrayList<String> getOutPIPNames(String dcp) {
        return RegisterDefaults.dcpFileToRegModuleMap.get(dcp).getOutPIPNames();
    }

    public static String getOutPIPName(String dcp, int index) {
        return RegisterDefaults.dcpFileToRegModuleMap.get(dcp).getOutPIPName(index);
    }
}
