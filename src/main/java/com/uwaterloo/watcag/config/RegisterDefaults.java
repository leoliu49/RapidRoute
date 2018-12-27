package com.uwaterloo.watcag.config;

import java.util.ArrayList;
import java.util.HashMap;

public class RegisterDefaults {

    public static String CLK_NAME;
    public static String RST_NAME;
    public static String CE_NAME;

    public static String INPUT_NAME;
    public static String OUTPUT_NAME;

    public static final HashMap<Integer, ComplexRegModule> typeToRegModuleMap = new HashMap<Integer, ComplexRegModule>();

    public static ArrayList<String> getInPIPNames(int type) {
        return RegisterDefaults.typeToRegModuleMap.get(type).getInPIPNames();
    }

    public static String getInPIPName(int type, int index) {
        return RegisterDefaults.typeToRegModuleMap.get(type).getInPIPName(index);
    }

    public static ArrayList<String> getOutPIPNames(int type) {
        return RegisterDefaults.typeToRegModuleMap.get(type).getOutPIPNames();
    }

    public static String getOutPIPName(int type, int index) {
        return RegisterDefaults.typeToRegModuleMap.get(type).getOutPIPName(index);
    }
}
