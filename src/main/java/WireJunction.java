import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.device.Tile;

public class WireJunction {


    protected WireDirection direction;
    protected String nodeName;
    protected String tileName;

    protected String wireName;
    protected int wireLength;

    public WireJunction(String tileName, String wireName) {
        this.tileName = tileName;
        this.wireName = wireName;

        this.nodeName = tileName + "/" + wireName;
    }

    public WireJunction copyWithOffset(Design d, int dx, int dy) {
        Tile offsetTile = d.getDevice().getTile(tileName).getTileXYNeighbor(dx, dy);

        WireJunction copy = new WireJunction(offsetTile.getName(), wireName);
        copy.direction = direction;
        copy.wireLength = wireLength;

        return copy;
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

    public boolean equals(WireJunction o) {
        return nodeName.equals(o.getNodeName());
    }

    @Override
    public String toString() {
        return "<" + nodeName + ">";
    }
}
