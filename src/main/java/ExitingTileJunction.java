import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.device.Tile;

public class ExitingTileJunction extends TileJunction implements Comparable<ExitingTileJunction> {

    private boolean isSnkJunction;

    public ExitingTileJunction(String tileName, String nodeName, String wireName, int wireLength,
                               WireDirection direction) {
        super(tileName, nodeName, wireName, wireLength, direction);
        this.isSnkJunction = false;
    }

    public ExitingTileJunction(String tileName, String nodeName, String wireName, int wireLength,
                                boolean isSrcJunction, WireDirection direction) {
        super(tileName, nodeName, wireName, wireLength, direction);
        this.isSnkJunction = isSrcJunction;
    }

    public boolean isSnkJunction() {
        return isSnkJunction;
    }

    public String getNodeBeginTransform(Design d) {
        if (isSnkJunction)
            return null;
        return RouteUtil.nodeBeginTransform(d, tileName, wireLength, wireName, direction);
    }

    public EnteringTileJunction getWireDestJunction(Design d) {
        if (isSnkJunction)
            return null;
        Tile baseTile = d.getDevice().getTile(getTileName());
        Tile destTile = null;
        switch (direction) {
            case NORTH:
                destTile = baseTile.getTileXYNeighbor(0, wireLength);
                break;
            case SOUTH:
                destTile = baseTile.getTileXYNeighbor(0, -1 * wireLength);
                break;
            case EAST:
                destTile = baseTile.getTileXYNeighbor(wireLength, 0);
                break;
            case WEST:
                destTile = baseTile.getTileXYNeighbor(0, -1 * wireLength);
                break;
        }
        String srcTileName = destTile.getName();
        EnteringTileJunction enJunc = new EnteringTileJunction(srcTileName,
                RouteUtil.nodeEndTransform(d, tileName, wireLength, wireName, direction),
                RouteUtil.wireEndTransform(d, tileName, wireName), wireLength, direction);
        return enJunc;
    }

    @Override
    public String toString() {
        if (isSnkJunction)
            return "<" + this.getNodeName() + ">[sink]";
        return "<" + this.getNodeName() + ">[exit "
                + RouteUtil.directionToString(direction) + wireLength + "]";
    }

    @Override
    public int compareTo(ExitingTileJunction o) {
        if (!tileName.equals(o.getTileName()) || !direction.equals(o.getDirection()))
            return 0;
        return wireLength - o.getWireLength();
    }

    public boolean equals(ExitingTileJunction o) {
        return nodeName.equals(o.getNodeName());
    }

    public static ExitingTileJunction duplWithShift(Design d, ExitingTileJunction ref, int dx, int dy) {
        Tile refTile = d.getDevice().getTile(ref.getTileName());
        Tile resTile = refTile.getTileXYNeighbor(dx, dy);

        ExitingTileJunction res = new ExitingTileJunction(resTile.getName(),
                resTile.getName() + "/" + ref.getWireName(), ref.getWireName(), ref.getWireLength(),
                ref.isSnkJunction(), ref.getDirection());

        return res;
    }
}
