import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.device.*;

public class RouteUtil {

    public static String directionToString(WireDirection dir) {
        if (dir == null)
            return null;
        switch (dir) {
        case NORTH:
            return "N";
        case SOUTH:
            return "S";
        case WEST:
            return "W";
        case EAST:
            return "E";
        case SELF:
            return "∞";
        }
        return null;
    }

    public static WireDirection stringToDirection(String s) {
        if (s.equals("N"))
            return WireDirection.NORTH;
        if (s.equals("S"))
            return WireDirection.SOUTH;
        if (s.equals("W"))
            return WireDirection.WEST;
        if (s.equals("E"))
            return WireDirection.EAST;
        if (s.equals("∞"))
            return WireDirection.SELF;
        return null;
    }

    public static WireDirection reverseDirection(WireDirection dir) {
        if (dir == null)
            return null;
        switch (dir) {
        case NORTH:
            return WireDirection.SOUTH;
        case SOUTH:
            return WireDirection.NORTH;
        case WEST:
            return WireDirection.EAST;
        case EAST:
            return WireDirection.WEST;
        case SELF:
            return WireDirection.SELF;
        }
        return null;
    }

    public static String getPIPNodeName(String tileName, String wireName) {
        return tileName + "/" + wireName;
    }

    public static WireDirection extractExitWireDirection(Design d, String tileName, String exitWireName) {
        Tile baseTile = d.getDevice().getTile(tileName);
        Tile offsetTile = null;
        for (Wire endWire : baseTile.getWireConnections(exitWireName)) {
            if (endWire.getTile().getTileTypeEnum().equals(TileTypeEnum.INT)) {
                offsetTile = endWire.getTile();
                break;
            }
        }

        if (offsetTile == null)
            return null;
        if (offsetTile.equals(baseTile))
            return WireDirection.SELF;
        return baseTile.getTileXCoordinate() == offsetTile.getTileXCoordinate()
                ? (baseTile.getTileYCoordinate() < offsetTile.getTileYCoordinate() ? WireDirection.NORTH : WireDirection.SOUTH)
                : (baseTile.getTileXCoordinate() < offsetTile.getTileYCoordinate() ? WireDirection.EAST : WireDirection.WEST);
    }

    public static int extractExitWireLength(Design d, String tileName, String exitWireName) {
        Tile baseTile = d.getDevice().getTile(tileName);
        Tile offsetTile = null;
        for (Wire endWire : baseTile.getWireConnections(exitWireName)) {
            if (endWire.getTile().getTileTypeEnum().equals(TileTypeEnum.INT)) {
                offsetTile = endWire.getTile();
                break;
            }
        }

        if (offsetTile == null)
            return 0;
        return (baseTile.getTileXCoordinate() == offsetTile.getTileXCoordinate())
                ? Math.abs(baseTile.getTileYCoordinate() - offsetTile.getTileYCoordinate())
                : Math.abs(baseTile.getTileXCoordinate() - offsetTile.getTileXCoordinate());
    }

    public static WireDirection extractEnterWireDirection(Design d, String tileName, String enterWireName) {
        Tile baseTile = d.getDevice().getTile(tileName);
        Tile offsetTile = null;

        Wire wire = new Wire(baseTile, enterWireName);
        offsetTile = wire.getStartWire().getTile();

        if (offsetTile == null)
            return null;
        if (baseTile.getName().equals(offsetTile.getName()))
            return WireDirection.SELF;
        if (baseTile.getTileXCoordinate() == offsetTile.getTileXCoordinate()) {
            return baseTile.getTileYCoordinate() > offsetTile.getTileYCoordinate()
                    ? WireDirection.NORTH : WireDirection.SOUTH;
        }
        else {
            return baseTile.getTileXCoordinate() > offsetTile.getTileXCoordinate()
                    ? WireDirection.EAST : WireDirection.WEST;
        }
    }

    public static int extractEnterWireLength(Design d, String tileName, String enterWireName) {
        Tile baseTile = d.getDevice().getTile(tileName);
        Tile offsetTile = null;

        Wire wire = new Wire(baseTile, enterWireName);
        offsetTile = wire.getStartWire().getTile();

        if (offsetTile == null)
            return 0;
        return baseTile.getTileXCoordinate() == offsetTile.getTileXCoordinate()
                ? Math.abs(baseTile.getTileYCoordinate() - offsetTile.getTileYCoordinate())
                : Math.abs(baseTile.getTileXCoordinate() - offsetTile.getTileXCoordinate());
    }

    public static boolean isClkNode(String nodeName) {
        Matcher matcher = Pattern.compile("GCLK.*").matcher(nodeName);
        if (matcher.find()) return true;

        return false;
    }

    /*
     * Not sure if all buffers look like these, but this is true for part xcku5p-ffvb676-2-e
     */
    public static boolean isNodeBuffer(Design d, String tileName, String nodeName) {
        Matcher matcher = Pattern.compile("INT_NODE_SDQ_\\d+_INT_OUT\\d+").matcher(nodeName);
        if (matcher.find()) return true;

        matcher = Pattern.compile("INT_INT_SDQ_\\d+_INT_OUT\\d+").matcher(nodeName);
        if (matcher.find()) return true;

        matcher = Pattern.compile("INT_NODE_IMUX_\\d+_INT_OUT\\d+").matcher(nodeName);
        if (matcher.find()) return true;

        matcher = Pattern.compile("BYPASS_W\\d+").matcher(nodeName);
        if (matcher.find()) return true;

        // Below is general solution
        boolean hasOutgoingPIPs = false;
        boolean hasIncomingPIPs = false;

        for (PIP pip : FabricBrowser.getTilePIPs(d, tileName)) {
            if (getPIPNodeName(tileName, pip.getStartWireName()).equals(nodeName))
                hasOutgoingPIPs = true;
            if (getPIPNodeName(tileName, pip.getEndWireName()).equals(nodeName))
                hasIncomingPIPs = true;
        }

        return hasIncomingPIPs && hasOutgoingPIPs;
    }

    public static String wireEndTransform(Design d, String baseTileName, String exitWireName) {
        Tile tile = d.getDevice().getTile(baseTileName);
        for (Wire endWire : tile.getWireConnections(exitWireName)) {
            if (endWire.getTile().getTileTypeEnum().equals(TileTypeEnum.INT))
                return endWire.getWireName();
        }

        return null;
    }

    public static String wireBeginTransform(Design d, String baseTileName, String enterWireName) {
        Tile tile = d.getDevice().getTile(baseTileName);
        Wire wire = new Wire(tile, enterWireName);
        return wire.getStartWire().getWireName();
    }

}
