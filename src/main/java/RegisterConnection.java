public class RegisterConnection {

    /*
     * Class describing a set of connections between 2 registers as described by the routes config
     */

    private int bitWidth;

    private boolean isInputConnection = false;
    private boolean isOutputConnection = false;

    private ComplexRegister srcReg;
    private int srcRegLowestBit;
    private int srcRegHighestBit;

    private ComplexRegister snkReg;
    private int snkRegLowestBit;
    private int snkRegHighestBit;

    public RegisterConnection(ComplexRegister srcReg, ComplexRegister snkReg, int srcRegLowestBit, int srcRegHighestBit,
                              int snkRegLowestBit, int snkRegHighestBit) {
        this.srcReg = srcReg;
        if (srcReg == null)
            isInputConnection = true;
        else {
            this.srcRegLowestBit = srcRegLowestBit;
            this.srcRegHighestBit = srcRegHighestBit;
        }

        this.snkReg = snkReg;
        if (snkReg == null)
            isOutputConnection = true;
        else {
            this.snkRegLowestBit = snkRegLowestBit;
            this.snkRegHighestBit = snkRegHighestBit;
        }

        this.bitWidth = Math.max(srcRegHighestBit - srcRegLowestBit + 1, snkRegHighestBit - snkRegLowestBit + 1);
    }

    public int getBitWidth() {
        return bitWidth;
    }

    public boolean isInputConnection() {
        return isInputConnection;
    }

    public boolean isOutputConnection() {
        return isOutputConnection;
    }

    public ComplexRegister getSrcReg() {
        return srcReg;
    }

    public int getSrcRegLowestBit() {
        return srcRegLowestBit;
    }

    public int getSrcRegHighestBit() {
        return srcRegHighestBit;
    }

    public ComplexRegister getSnkReg() {
        return snkReg;
    }

    public int getSnkRegLowestBit() {
        return snkRegLowestBit;
    }

    public int getSnkRegHighestBit() {
        return snkRegHighestBit;
    }

    @Override
    public String toString() {
        if (isInputConnection)
            return "<INPUT> --> " + "<" + snkReg.getName() + "_b" + snkRegLowestBit + "_b" + snkRegHighestBit + ">";
        if (isOutputConnection)
            return "<" + srcReg.getName() + "_b" + srcRegLowestBit + "_b" + srcRegHighestBit + ">" + " --> <OUTPUT>";
        String repr = "<" + srcReg.getName() + "_b" + srcRegLowestBit + "_b" + srcRegHighestBit + "> --> ";
        repr += "<" + snkReg.getName() + "_b" + snkRegLowestBit + "_b" + snkRegHighestBit + ">";

        return repr;
    }
}
