import com.xilinx.rapidwright.design.Design;
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

    private Design srcDesign = null;
    private Module module = null;


    public ComplexRegModule(int type, int bitWidth, ArrayList<String> inPIPNames, ArrayList<String> outPIPNames,
                            Design srcDesign) {
        this.type = type;
        this.bitWidth = bitWidth;
        this.inPIPNames = inPIPNames;
        this.outPIPNames = outPIPNames;

        this.srcDesign = srcDesign;
        module = new Module(srcDesign);
        module.setNetlist(srcDesign.getNetlist());

        RouterLog.log("Initialized register module anchored at <"
                + module.getAnchor().getSiteName() + ">.", RouterLog.Level.VERBOSE);
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

    public Design getSrcDesign() {
        return srcDesign;
    }

    public Module getModule() {
        return module;
    }

    @Override
    public String toString() {
        return "<type" + type + ">[" + bitWidth + "b]";
    }
}
