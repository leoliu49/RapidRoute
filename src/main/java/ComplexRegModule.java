import com.xilinx.rapidwright.design.Module;

import java.util.ArrayList;

public class ComplexRegModule {

    /*
     * Wrapper around a single register module
     * How they're used is up to the ComplexRegister
     */

    private int type;
    private int bitWidth;

    // Little-endian (well it doesn't really matter)
    private ArrayList<String> inPIPNames;
    private ArrayList<String> outPIPNames;

    private Module module = null;


    public ComplexRegModule(int type, int bitWidth, ArrayList<String> inPIPNames, ArrayList<String> outPIPNames) {
        this.type = type;
        this.bitWidth = bitWidth;
        this.inPIPNames = inPIPNames;
        this.outPIPNames = outPIPNames;
    }

    public int getType() {
        return type;
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

    public Module getModule() {
        return module;
    }

    public boolean isModuleSet() {
        return (module == null);
    }

    public void setModule(Module module) {
        this.module = module;
    }
}
