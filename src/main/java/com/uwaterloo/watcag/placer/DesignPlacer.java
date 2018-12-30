package com.uwaterloo.watcag.placer;

import com.uwaterloo.watcag.DesignFailureException;
import com.uwaterloo.watcag.common.ComplexRegister;
import com.uwaterloo.watcag.config.RegisterComponent;
import com.uwaterloo.watcag.config.RegisterDefaults;
import com.uwaterloo.watcag.util.RouterLog;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.edif.EDIFCell;
import com.xilinx.rapidwright.edif.EDIFDirection;
import com.xilinx.rapidwright.edif.EDIFNet;
import com.xilinx.rapidwright.edif.EDIFPort;

import java.util.HashMap;
import java.util.HashSet;

public class DesignPlacer {

    private static Design coreDesign;

    private static final HashMap<String, ComplexRegister> registersMap = new HashMap<>();
    private static final HashSet<String> pipUsageSet = new HashSet<>();

    public static void initializePlacer(Design d) {
        reset();
        coreDesign = d;
    }

    public static void reset() {
        registersMap.clear();
        pipUsageSet.clear();
    }

    public static void prepareNewRegisterForPlacement(ComplexRegister register) {
        if (registersMap.containsKey(register.getName())) {
            throw new DesignFailureException("A register of the same name has already been declared: <"
                    + register.getName() + ">.");
        }

        for (RegisterComponent component : register.getComponents()) {
            String intTileName = coreDesign.getDevice().getSite(component.getSiteName()).getIntTile().getName();
            for (String pipName : component.getInPIPNames()) {
                if (pipUsageSet.contains(intTileName + "/" + pipName)) {
                    throw new DesignFailureException("Detected duplicated usage of PIP resource <"
                            + intTileName + "/" + pipName + ">.");
                }

                pipUsageSet.add(intTileName + "/" + pipName);
            }

            for (String pipName : component.getOutPIPNames()) {
                if (pipUsageSet.contains(intTileName + "/" + pipName)) {
                    throw new DesignFailureException("Detected duplicated usage of PIP resource <"
                            + intTileName + "/" + pipName + ">.");
                }

                pipUsageSet.add(intTileName + "/" + pipName);
            }
        }

        registersMap.put(register.getName(), register);
    }

    public static void createTopLevelClk() {
        EDIFCell top = coreDesign.getNetlist().getTopCell();
        EDIFPort clkPort = top.createPort(RegisterDefaults.CLK_NAME, EDIFDirection.INPUT, 1);
        EDIFNet clk = top.createNet(RegisterDefaults.CLK_NAME);
        clk.createPortInst(clkPort);
    }

    public static void place() {
        long tBegin = System.currentTimeMillis();

        RouterLog.log("Performing register placement.", RouterLog.Level.NORMAL);
        RouterLog.indent();
        for (ComplexRegister register : registersMap.values()) {
            RouterLog.log("Performing placement for register " + register.getName() + ".", RouterLog.Level.NORMAL);
            RouterLog.indent();
            register.populateAndPlace(coreDesign);
            RouterLog.indent(-1);
        }
        RouterLog.indent(-1);

        RouterLog.log("Placement completed in " + (System.currentTimeMillis() - tBegin) + " ms.",
                RouterLog.Level.NORMAL);
    }

}
