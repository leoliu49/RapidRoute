import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.device.Tile;

public class EnterWireJunction extends WireJunction {

    private boolean isSrc;
    private ExitWireJunction srcJunction;

    private EnterWireJunction(String tileName, String wireName) {
        super(tileName, wireName);
    }

    public EnterWireJunction(Design d, String tileName, String wireName) {
        super(tileName, wireName);

        wireLength = RouteUtil.extractEnterWireLength(d, tileName, wireName);
        direction = RouteUtil.extractEnterWireDirection(d, tileName, wireName);

        isSrc = false;
        srcJunction = null;
    }

    public EnterWireJunction(String tileName, String wireName, ExitWireJunction src, int wireLength,
                            WireDirection direction) {
        super(tileName, wireName);
        srcJunction = src;
        this.wireLength = wireLength;
        this.direction = direction;
    }

    public boolean isSrc() {
        return isSrc;
    }

    public ExitWireJunction getSrcJunction(Design d) {
        if (srcJunction == null)
            setSrcJunction(d);
        return srcJunction;
    }

    private void setSrcJunction(Design d) {
        Tile baseTile = d.getDevice().getTile(getTileName());
        Tile srcTile = null;
        switch (direction) {
            case NORTH:
                srcTile = baseTile.getTileXYNeighbor(0, -1 * wireLength);
                break;
            case SOUTH:
                srcTile = baseTile.getTileXYNeighbor(0, wireLength);
                break;
            case EAST:
                srcTile = baseTile.getTileXYNeighbor(-1 * wireLength, 0);
                break;
            case WEST:
                srcTile = baseTile.getTileXYNeighbor(wireLength, 0);
                break;
            case SELF:
                srcTile = baseTile;
        }
        String srcTileName = srcTile.getName();
        srcJunction = new ExitWireJunction(srcTileName, RouteUtil.wireBeginTransform(d, srcTileName, wireName), this,
                wireLength, direction);
    }

    @Override
    public String toString() {
        if (isSrc)
            return "<" + nodeName + ">[src]";
        return "<" + nodeName + ">[enter " + RouteUtil.directionToString(direction) + wireLength + "]";
    }

    public static EnterWireJunction newSrcJunction(String tileName, String wireName) {
        EnterWireJunction ej = new EnterWireJunction(tileName, wireName);
        ej.isSrc = true;
        ej.srcJunction = null;

        return ej;
    }

}
