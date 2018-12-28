package com.uwaterloo.watcag.config;

import com.xilinx.rapidwright.design.ModuleInst;
import com.xilinx.rapidwright.edif.EDIFCellInst;

import java.util.ArrayList;

public class RegisterComponent {

    /*
     * Register confined to a single site, which are building blocks to the com.uwaterloo.watcag.common.ComplexRegister class
     */

    private String name = null;

    private String parentDcp;
    private String siteName;
    private int bitWidth;

    private ModuleInst moduleInstance;

    public RegisterComponent(String name, String parentDcp, String siteName) {
        this.name = name;
        this.siteName = siteName;

        bitWidth = RegisterDefaults.dcpFileToRegModuleMap.get(parentDcp).getBitWidth();
    }

    public RegisterComponent(String parentDcp, String siteName) {
        this.parentDcp = parentDcp.replace("\\.dcp", "_dcp");

        this.siteName = siteName;

        bitWidth = RegisterDefaults.dcpFileToRegModuleMap.get(this.parentDcp).getBitWidth();
    }

    public String getName() {
        return name;
    }

    public boolean hasName() {
        return !(name == null);
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getParentDcp() {
        return parentDcp;
    }

    public String getSiteName() {
        return siteName;
    }

    public int getBitWidth() {
        return bitWidth;
    }

    public ModuleInst getModuleInstance() {
        return moduleInstance;
    }

    public EDIFCellInst getCellInstance() {
        return moduleInstance.getCellInst();
    }

    public void setModuleInstance(ModuleInst moduleInstance) {
        this.moduleInstance = moduleInstance;
    }

    public ArrayList<String> getInPIPNames() {
        return RegisterDefaults.getInPIPNames(parentDcp);
    }

    public String getInPIPName(int index) {
        return RegisterDefaults.getInPIPName(parentDcp, index);
    }

    public ArrayList<String> getOutPIPNames() {
        return RegisterDefaults.getOutPIPNames(parentDcp);
    }

    public String getOutPIPName(int index) {
        return RegisterDefaults.getOutPIPName(parentDcp, index);
    }

    @Override
    public String toString() {
        return name + RegisterDefaults.dcpFileToRegModuleMap.get(parentDcp).toString();
    }
}
