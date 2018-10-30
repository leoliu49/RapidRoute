import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.device.Tile;

public class ExitWireJunction extends WireJunction {

    private boolean isSnk;
    private EnterWireJunction destJunction;

    private ExitWireJunction(String tileName, String wireName) {
        super(tileName, wireName);
    }

    public ExitWireJunction(Design d, String tileName, String wireName) {
        super(tileName, wireName);

        wireLength = RouteUtil.extractExitWireLength(d, tileName, wireName);
        direction = RouteUtil.extractExitWireDirection(d, tileName, wireName);

        isSnk = false;
        destJunction = null;
    }

    public ExitWireJunction(String tileName, String wireName, EnterWireJunction dest, int wireLength,
                            WireDirection direction) {
        super(tileName, wireName);
        destJunction = dest;
        this.wireLength = wireLength;
        this.direction = direction;
    }

    public boolean isSnk() {
        return isSnk;
    }

    public EnterWireJunction getDestJunction(Design d) {
        if (destJunction == null)
            setDestJunction(d);
        return destJunction;
    }

    private void setDestJunction(Design d) {
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
                destTile = baseTile.getTileXYNeighbor(-1 * wireLength, 0);
                break;
            case SELF:
                destTile = baseTile;
        }
        String destTileName = destTile.getName();
        destJunction = new EnterWireJunction(destTileName, RouteUtil.wireEndTransform(d, destTileName, wireName), this,
                wireLength, direction);
    }

    @Override
    public String toString() {
        if (isSnk)
            return "<" + nodeName + ">[snk]";
        return "<" + nodeName + ">[exit " + RouteUtil.directionToString(direction) + wireLength + "]";
    }

    public static ExitWireJunction newSnkJunction(String tileName, String wireName) {
        ExitWireJunction ej = new ExitWireJunction(tileName, wireName);
        ej.isSnk = true;
        ej.destJunction = null;

        return ej;
    }

}
