import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.device.Tile;

public class TileJunction {

    /*
     * Represents an entering/exiting node of a tile (i.e. not a bounce-node)
     */

    protected WireDirection direction;

    protected String nodeName;
    protected String tileName;
    protected String wireName;

    protected int wireLength;

    public TileJunction(String tileName, String nodeName, String wireName, int wireLength, WireDirection direction) {
        this.tileName = tileName;
        this.nodeName = nodeName;

        this.wireName = wireName;
        this.wireLength = wireLength;

        this.direction = direction;
    }

    public WireDirection getDirection() {
        return direction;
    }

    public String getNodeName() {
        return nodeName;
    }

    public String getTileName() {
        return tileName;
    }

    public String getWireName() {
        return wireName;
    }

    public int getWireLength() {
        return wireLength;
    }

    @Override
    public String toString() {
        return "<" + nodeName + ">";
    }

    public static TileJunction duplWithShift(Design d, TileJunction ref, int dx, int dy) {
        Tile refTile = d.getDevice().getTile(ref.getTileName());
        Tile resTile = refTile.getTileXYNeighbor(dx, dy);

        TileJunction res = new TileJunction(resTile.getName(), resTile.getName() + "/" + ref.getWireName(),
                 ref.getWireName(), ref.getWireLength(), ref.getDirection());

        return res;
    }

}
