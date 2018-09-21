import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.device.Tile;

public class EnteringTileJunction extends TileJunction {

    private boolean isSrcJunction;

    // Speeds match WireDirection ordinal number
    private int[] highestSpeeds = {0, 0, 0, 0};

    public EnteringTileJunction(String tileName, String nodeName, String wireName, int wireLength,
                                WireDirection direction) {
        super(tileName, nodeName, wireName, wireLength, direction);
        this.isSrcJunction = false;
    }

    public EnteringTileJunction(String tileName, String nodeName, String wireName, int wireLength,
                                boolean isSrcJunction, WireDirection direction) {
        super(tileName, nodeName, wireName, wireLength, direction);
        this.isSrcJunction = isSrcJunction;
    }

    public boolean isSrcJunction() {
        return isSrcJunction;
    }

    public String getNodeEndTransform(Design d) {
        if (isSrcJunction)
            return null;
        return RouteUtil.nodeEndTransform(d, tileName, wireLength, wireName, direction);
    }

    public int[] getHighestSpeeds() {
        return highestSpeeds;
    }

    public int getHighestSpeed(int i) {
        return highestSpeeds[i];
    }

    public void setHighestSpeeds(int[] highestSpeeds) {
        this.highestSpeeds = highestSpeeds;
    }

    public void setHighestSpeed(int speed, int i) {
        this.highestSpeeds[i] = speed;
    }

    public ExitingTileJunction getWireSourceJunction(Design d) {
        if (isSrcJunction)
            return null;
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
                srcTile = baseTile.getTileXYNeighbor(0, wireLength);
                break;
        }
        String srcTileName = srcTile.getName();
        return new ExitingTileJunction(srcTileName,
                RouteUtil.nodeBeginTransform(d, tileName, wireLength, wireName, direction),
                RouteUtil.wireBeginTransform(d, tileName, wireName), wireLength, direction);
    }

    @Override
    public String toString() {
        if (isSrcJunction)
            return "<" + this.getNodeName() + ">[source]";
        return "<" + this.getNodeName() + ">[enter " + RouteUtil.directionToString(direction) + wireLength + "]";
    }

    public static EnteringTileJunction duplWithShift(Design d, EnteringTileJunction ref, int dx, int dy) {
        Tile refTile = d.getDevice().getTile(ref.getTileName());
        Tile resTile = refTile.getTileXYNeighbor(dx, dy);

        EnteringTileJunction res = new EnteringTileJunction(resTile.getName(),
                resTile.getName() + "/" + ref.getWireName(), ref.getWireName(), ref.getWireLength(),
                ref.isSrcJunction(), ref.getDirection());


        return res;
    }

}
