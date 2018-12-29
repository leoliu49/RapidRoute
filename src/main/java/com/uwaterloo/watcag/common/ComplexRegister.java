package com.uwaterloo.watcag.common;

import com.uwaterloo.watcag.config.ComplexRegModule;
import com.uwaterloo.watcag.config.RegisterComponent;
import com.uwaterloo.watcag.config.RegisterDefaults;
import com.uwaterloo.watcag.util.RouterLog;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.ModuleInst;

import com.xilinx.rapidwright.device.Site;
import com.xilinx.rapidwright.edif.*;

import java.util.ArrayList;

public class ComplexRegister {

    private String name;

    private ArrayList<RegisterComponent> components;
    private int componentSize;

    private int bitWidth;

    public ComplexRegister(Design d, String name, ArrayList<RegisterComponent> components) {

        this.name = name;

        this.components = components;
        componentSize = components.size();

        bitWidth = 0;

        int i = 0;
        for (RegisterComponent component : components) {
            ComplexRegModule regModule = RegisterDefaults.dcpFileToRegModuleMap.get(component.getParentDcp());

            if (!component.hasName())
                component.setName("component" + i++);

            bitWidth += regModule.getBitWidth();
        }
    }

    /*
     * Permanently associates register with given design
     */
    public void populateAndPlace(Design d) {

        EDIFCell top = d.getNetlist().getTopCell();

        for (RegisterComponent component : components) {
            ComplexRegModule regModule = RegisterDefaults.dcpFileToRegModuleMap.get(component.getParentDcp());

            EDIFCellInst ci = top.createChildCellInst(name + "_" + component.getName(),
                    regModule.getModule().getNetlist().getTopCell());
            ModuleInst mi = d.createModuleInst(name + "_" + component.getName(), regModule.getModule());
            mi.setCellInst(ci);

            Site anchorSite = d.getDevice().getSite(component.getSiteName());
            mi.place(anchorSite);

            component.setModuleInstance(mi);

            top.getNet(RegisterDefaults.CLK_NAME).createPortInst(RegisterDefaults.CLK_NAME, ci);

            RouterLog.log("Placed component " + component.toString() + " for <" + name + "> at site <"
                    + component.getSiteName() + ">.", RouterLog.Level.INFO);
        }
    }

    public String getName() {
        return name;
    }

    public ArrayList<RegisterComponent> getComponents() {
        return components;
    }

    public RegisterComponent getComponent(int index) {
        return components.get(index);
    }

    public int getComponentSize() {
        return componentSize;
    }

    public int getBitWidth() {
        return bitWidth;
    }

    public void createInputEDIFPortRefs(Design d, String netPrefix) {

        EDIFCell top = d.getNetlist().getTopCell();

        int i = 0;
        for (RegisterComponent component : components) {
            for (int j = 0; j < component.getBitWidth(); j++, i++) {
                EDIFNet net = top.getNet(netPrefix + "[" + i + "]");
                if (net == null)
                    net = top.createNet(netPrefix + "[" + i + "]");
                net.createPortInst(RegisterDefaults.INPUT_NAME, j, component.getCellInstance());
            }
        }
    }

    public void createInputEDIFPortRefs(Design d, String netPrefix, int startBit, int endBit, int indexOffset) {

        EDIFCell top = d.getNetlist().getTopCell();

        int i = 0;
        for (RegisterComponent component : components) {
            for (int j = 0; j < component.getBitWidth(); j++, i++) {
                if (i >= startBit && i <= endBit) {
                    EDIFNet net = top.getNet(netPrefix + "[" + (i + indexOffset) + "]");
                    if (net == null)
                        net = top.createNet(netPrefix + "[" + (i + indexOffset) + "]");
                    net.createPortInst(RegisterDefaults.INPUT_NAME, j, component.getCellInstance());
                }
            }
        }
    }

    public void createOutputEDIFPortRefs(Design d, String netPrefix) {

        EDIFCell top = d.getNetlist().getTopCell();

        int i = 0;
        for (RegisterComponent component : components) {
            for (int j = 0; j < component.getBitWidth(); j++, i++) {
                EDIFNet net = top.getNet(netPrefix + "[" + i + "]");
                if (net == null)
                    net = top.createNet(netPrefix + "[" + i + "]");
                net.createPortInst(RegisterDefaults.OUTPUT_NAME, j, component.getCellInstance());
            }
        }

    }

    public void createOutputEDIFPortRefs(Design d, String netPrefix, int startBit, int endBit, int indexOffset) {

        EDIFCell top = d.getNetlist().getTopCell();

        int i = 0;
        for (RegisterComponent component : components) {
            for (int j = 0; j < component.getBitWidth(); j++, i++) {
                if (i >= startBit && i <= endBit) {
                    EDIFNet net = top.getNet(netPrefix + "[" + (i + indexOffset) + "]");
                    if (net == null)
                        net = top.createNet(netPrefix + "[" + (i + indexOffset) + "]");
                    net.createPortInst(RegisterDefaults.OUTPUT_NAME, j, component.getCellInstance());
                }
            }
        }

    }

    @Override
    public String toString() {
        return "<" + name + ">[" + bitWidth + "b]";
    }
}
