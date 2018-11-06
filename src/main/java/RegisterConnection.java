import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.device.Tile;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

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

    public boolean isCongruentWith(Design d, RegisterConnection connection) {
        if (bitWidth != connection.getBitWidth())
            return false;

        // No congruency for input and output connections
        if (isInputConnection || connection.isInputConnection())
            return false;
        if (isOutputConnection || connection.isOutputConnection())
            return false;

        Pair<Tile , String>[] srcPIPNames = new Pair[bitWidth];
        Pair<Tile , String>[] snkPIPNames = new Pair[bitWidth];
        {
            int bitIndex = 0;
            for (RegisterComponent component : srcReg.getComponents()) {
                Tile intTile = d.getDevice().getSite(component.getSiteName()).getIntTile();
                for (int i = 0; i < component.getBitWidth(); i++, bitIndex++) {
                    if (bitIndex >= srcRegLowestBit && bitIndex <= srcRegHighestBit)
                        srcPIPNames[bitIndex - srcRegLowestBit] = new ImmutablePair<>(intTile,
                                component.getOutPIPName(i));
                }
            }
        }
        {
            int bitIndex = 0;
            for (RegisterComponent component : snkReg.getComponents()) {
                Tile intTile = d.getDevice().getSite(component.getSiteName()).getIntTile();
                for (int i = 0; i < component.getBitWidth(); i++, bitIndex++) {
                    if (bitIndex >= snkRegLowestBit && bitIndex <= snkRegHighestBit)
                        snkPIPNames[bitIndex - snkRegLowestBit] = new ImmutablePair<>(intTile,
                                component.getInPIPName(i));
                }
            }
        }

        Pair<Tile , String>[] offsetSrcPIPNames = new Pair[bitWidth];
        Pair<Tile , String>[] offsetSnkPIPNames = new Pair[bitWidth];
        {
            int bitIndex = 0;
            for (RegisterComponent component : connection.getSrcReg().getComponents()) {
                Tile intTile = d.getDevice().getSite(component.getSiteName()).getIntTile();
                for (int i = 0; i < component.getBitWidth(); i++, bitIndex++) {
                    if (bitIndex >= connection.getSrcRegLowestBit() && bitIndex <= connection.getSrcRegHighestBit())
                        offsetSrcPIPNames[bitIndex - connection.getSrcRegLowestBit()] = new ImmutablePair<>(intTile,
                                component.getOutPIPName(i));
                }
            }
        }
        {
            int bitIndex = 0;
            for (RegisterComponent component : connection.getSnkReg().getComponents()) {
                Tile intTile = d.getDevice().getSite(component.getSiteName()).getIntTile();
                for (int i = 0; i < component.getBitWidth(); i++, bitIndex++) {
                    if (bitIndex >= connection.getSnkRegLowestBit() && bitIndex <= connection.getSnkRegHighestBit())
                        offsetSnkPIPNames[bitIndex - connection.getSnkRegLowestBit()] = new ImmutablePair<>(intTile,
                                component.getInPIPName(i));
                }
            }
        }

        // Ensure PIP names are identical and tile offset is constant
        int dx = srcPIPNames[0].getLeft().getTileXCoordinate() - offsetSrcPIPNames[0].getLeft().getTileXCoordinate();
        int dy = srcPIPNames[0].getLeft().getTileYCoordinate() - offsetSrcPIPNames[0].getLeft().getTileYCoordinate();
        for (int i = 0; i < bitWidth; i++) {
            if (!srcPIPNames[i].getRight().equals(offsetSrcPIPNames[i].getRight()))
                return false;
            if (!snkPIPNames[i].getRight().equals(offsetSnkPIPNames[i].getRight()))
                return false;

            if (srcPIPNames[i].getLeft().getTileXCoordinate() - offsetSrcPIPNames[i].getLeft().getTileXCoordinate() != dx)
                return false;
            if (srcPIPNames[i].getLeft().getTileYCoordinate() - offsetSrcPIPNames[i].getLeft().getTileYCoordinate() != dy)
                return false;

            if (snkPIPNames[i].getLeft().getTileXCoordinate() - offsetSnkPIPNames[i].getLeft().getTileXCoordinate() != dx)
                return false;
            if (snkPIPNames[i].getLeft().getTileYCoordinate() - offsetSnkPIPNames[i].getLeft().getTileYCoordinate() != dy)
                return false;

        }


        return true;
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
            return "<INPUT> --> " + "<" + snkReg.getName() + "[" + snkRegHighestBit + ".." + snkRegLowestBit + "]>";
        if (isOutputConnection)
            return "<" + srcReg.getName() + "[" + srcRegHighestBit + ".." + srcRegLowestBit + "]>" + " --> <OUTPUT>";
        String repr = "<" + srcReg.getName() + "[" + srcRegHighestBit + ".." + srcRegLowestBit + "]> --> ";
        repr += "<" + snkReg.getName() + "[" + snkRegHighestBit + ".." + snkRegLowestBit + "]>";

        return repr;
    }
}
