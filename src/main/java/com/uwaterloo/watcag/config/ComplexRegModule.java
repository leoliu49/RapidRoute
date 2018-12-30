package com.uwaterloo.watcag.config;

import com.uwaterloo.watcag.DesignFailureException;
import com.uwaterloo.watcag.util.RouterLog;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Module;
import com.xilinx.rapidwright.device.Site;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

public class ComplexRegModule {

    /*
     * Wrapper around a single register module
     * How they're used is up to the com.uwaterloo.watcag.common.ComplexRegister
     */

    private String parentDcp;
    private int bitWidth;

    // Little-endian (well it doesn't really matter)
    private ArrayList<String> inPIPNames;
    private ArrayList<String> outPIPNames;

    private Design srcDesign = null;
    private Module module = null;


    public ComplexRegModule(String parentDcp, int bitWidth, ArrayList<String> inPIPNames, ArrayList<String> outPIPNames,
                            Design srcDesign) {
        this.parentDcp = parentDcp;
        this.bitWidth = bitWidth;
        this.inPIPNames = inPIPNames;
        this.outPIPNames = outPIPNames;

        this.srcDesign = srcDesign;
        module = new Module(srcDesign);
        module.setNetlist(srcDesign.getNetlist());

        if (inPIPNames.size() != bitWidth || outPIPNames.size() != bitWidth)
            throw new DesignFailureException("PIP names for imported module <" + parentDcp + ".dcp> have incompatible sizes.");

        RouterLog.log("Initialized register module anchored at <"
                + module.getAnchor().getSiteName() + ">.", RouterLog.Level.VERBOSE);
    }

    public ComplexRegModule(String parentDcp, int bitWidth, String[] inPIPNames, String[] outPIPNames,
                            Design srcDesign) {
        this(parentDcp, bitWidth, new ArrayList<>(Arrays.asList(inPIPNames)),
                new ArrayList<>(Arrays.asList(outPIPNames)), srcDesign);
    }

    public String getParentDcp() {
        return parentDcp;
    }

    public int getBitWidth() {
        return bitWidth;
    }

    public ArrayList<String> getInPIPNames() {
        return inPIPNames;
    }

    public String getInPIPName(int i) {
        if (i < 0)
            return inPIPNames.get(bitWidth + i);
        return inPIPNames.get(i);
    }

    public ArrayList<String> getOutPIPNames() {
        return outPIPNames;
    }

    public String getOutPIPName(int i) {
        if (i < 0)
            return outPIPNames.get(bitWidth + i);
        return outPIPNames.get(i);
    }

    public Design getSrcDesign() {
        return srcDesign;
    }

    public Module getModule() {
        return module;
    }

    public ArrayList<String> getAllValidSitePlacements() {
        ArrayList<String> sites = new ArrayList<>();
        for (Site site : module.getAllValidPlacements())
            sites.add(site.getName());

        return sites;
    }

    @Override
    public String toString() {
        return "<" + parentDcp + ">[" + bitWidth + "b]";
    }
}
