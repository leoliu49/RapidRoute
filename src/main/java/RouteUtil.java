import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.PIP;
import com.xilinx.rapidwright.device.Site;
import com.xilinx.rapidwright.device.Tile;
import com.xilinx.rapidwright.device.Wire;

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
        }
        return null;
    }

    public static WireDirection primaryHDirection(int dx) {
        if (dx == 0)
            return null;
        else if (dx > 0)
            return WireDirection.EAST;
        else
            return WireDirection.WEST;
    }

    public static WireDirection primaryVDirection(int dy) {
        if (dy == 0)
            return null;
        else if (dy > 0)
            return WireDirection.NORTH;
        else
            return WireDirection.SOUTH;
    }

    public static boolean isVertical(WireDirection dir) {
        return (dir.equals(WireDirection.NORTH) || dir.equals(WireDirection.SOUTH));
    }

    public static WireDirection extractExitWirePIPDirection(Design d, String tileName, String exitWireName) {
        Tile baseTile = d.getDevice().getTile(tileName);
        Tile offsetTile = null;
        for (PIP pip : baseTile.getPIPs()) {
            Wire wire = new Wire(baseTile, pip.getStartWireName());
            if (wire.getStartWire().getWireName().equals(exitWireName)) {
                offsetTile = wire.getStartWire().getTile();
                break;
            }
        }
        if (offsetTile == null)
            return null;
        if (baseTile.getName().equals(offsetTile.getName()))
            return null;
        if (baseTile.getTileXCoordinate() == offsetTile.getTileXCoordinate()) {
            return (baseTile.getTileYCoordinate() > offsetTile.getTileYCoordinate()) ? WireDirection.NORTH : WireDirection.SOUTH;
        }
        else {
            return (baseTile.getTileXCoordinate() > offsetTile.getTileXCoordinate()) ? WireDirection.EAST : WireDirection.WEST;
        }
    }

    public static WireDirection extractEnterWirePIPDirection(Design d, String tileName, String enterWireName) {
        Tile baseTile = d.getDevice().getTile(tileName);
        Tile offsetTile = null;

        Wire wire = new Wire(baseTile, enterWireName);
        offsetTile = wire.getStartWire().getTile();

        if (offsetTile == null)
            return null;
        if (baseTile.getName().equals(offsetTile.getName()))
            return null;
        if (baseTile.getTileXCoordinate() == offsetTile.getTileXCoordinate()) {
            return (baseTile.getTileYCoordinate() > offsetTile.getTileYCoordinate()) ? WireDirection.NORTH : WireDirection.SOUTH;
        }
        else {
            return (baseTile.getTileXCoordinate() > offsetTile.getTileXCoordinate()) ? WireDirection.EAST : WireDirection.WEST;
        }
    }

    public static int extractPIPExitWireLength(Design d, String tileName, String exitWireName) {
        Tile baseTile = d.getDevice().getTile(tileName);
        Tile offsetTile = null;
        for (PIP pip : baseTile.getPIPs()) {
            Wire wire = new Wire(baseTile, pip.getStartWireName());
            if (wire.getStartWire().getWireName().equals(exitWireName)) {
                offsetTile = wire.getStartWire().getTile();
                break;
            }
        }
        if (offsetTile == null)
            return 0;
        return (baseTile.getTileXCoordinate() == offsetTile.getTileXCoordinate()) ? Math.abs(baseTile.getTileYCoordinate() - offsetTile.getTileYCoordinate()) :
            Math.abs(baseTile.getTileXCoordinate() - offsetTile.getTileXCoordinate());
    }

    public static int extractPIPEnterWireLength(Design d, String tileName, String enterWireName) {
        Tile baseTile = d.getDevice().getTile(tileName);
        Tile offsetTile = null;

        Wire wire = new Wire(baseTile, enterWireName);
        offsetTile = wire.getStartWire().getTile();

        if (offsetTile == null)
            return 0;
        return (baseTile.getTileXCoordinate() == offsetTile.getTileXCoordinate()) ? Math.abs(baseTile.getTileYCoordinate() - offsetTile.getTileYCoordinate()) :
                Math.abs(baseTile.getTileXCoordinate() - offsetTile.getTileXCoordinate());
    }

    /*
     * No clue what the function of these are, but they clutter the BFS searches
     */
    public static boolean isIgnorable(ExitingTileJunction exJunc) {
        String nodeName = exJunc.getNodeName();
        Matcher matcher = Pattern.compile("INODE_._\\d+_FT\\d+").matcher(nodeName);
        if (matcher.find()) return true;

        matcher = Pattern.compile("BOUNCE_._\\d+_FT\\d+").matcher(nodeName);
        if (matcher.find()) return true;

        return false;
    }

    /*
     * No clue what the function of these are, but they clutter the BFS searches
     */
    public static boolean isIgnorable(EnteringTileJunction enJunc) {
        String nodeName = enJunc.getNodeName();

        Matcher matcher = Pattern.compile("INODE_._BLN_\\d+_FT\\d+").matcher(nodeName);
        if (matcher.find()) return true;

        matcher = Pattern.compile("BOUNCE_._BLN_._FT\\d+").matcher(nodeName);
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
        Tile tile = d.getDevice().getTile(tileName);
        for (PIP pip : tile.getPIPs()) {
            if (pip.getStartNode().getName().equals(nodeName))
                hasOutgoingPIPs = true;
            if (pip.getEndNode().getName().equals(nodeName))
                hasIncomingPIPs = true;
        }

        return hasIncomingPIPs && hasIncomingPIPs;
    }

    public static String nodeEndTransform(Design d, String baseTileName, int distance, String exitWireName,
                                          WireDirection dir) {
        Tile tile = d.getDevice().getTile(baseTileName);
        Tile nextTile = null;
        switch (dir) {
        case NORTH:
            nextTile = tile.getTileXYNeighbor(0, distance);
            break;
        case SOUTH:
            nextTile = tile.getTileXYNeighbor(0, -1 * distance);
            break;
        case EAST:
            nextTile = tile.getTileXYNeighbor(distance, 0);
            break;
        case WEST:
            nextTile = tile.getTileXYNeighbor(-1 * distance, 0);
            break;
        }

        for (PIP pip : nextTile.getPIPs()) {
            Wire wire = new Wire(tile, pip.getStartWireName());
            if (wire.getStartWire().getWireName().equals(exitWireName)) {
                return nextTile.getName() + "/" + wire.getWireName();
            }
        }

        return null;
    }

    public static String wireEndTransform(Design d, String baseTileName, String exitWireName) {
        Tile tile = d.getDevice().getTile(baseTileName);
        for (PIP pip : tile.getPIPs()) {
            Wire wire = new Wire(tile, pip.getStartWireName());
            if (wire.getStartWire().getWireName().equals(exitWireName)) {
                return wire.getWireName();
            }
        }
        return null;
    }

    public static String nodeBeginTransform(Design d, String baseTileName, int distance, String enterWireName,
                                            WireDirection dir) {
        Tile tile = d.getDevice().getTile(baseTileName);
        Tile prevTile = null;
        switch (dir) {
            case NORTH:
                prevTile = tile.getTileXYNeighbor(0, -1 * distance);
                break;
            case SOUTH:
                prevTile = tile.getTileXYNeighbor(0, distance);
                break;
            case EAST:
                prevTile = tile.getTileXYNeighbor(-1 * distance, 0);
                break;
            case WEST:
                prevTile = tile.getTileXYNeighbor(distance, 0);
                break;
        }

        Wire wire = new Wire(tile, enterWireName);
        return prevTile.getName() + "/" + wire.getStartWire().getWireName();
    }

    public static String wireBeginTransform(Design d, String baseTileName, String enterWireName) {
        Tile tile = d.getDevice().getTile(baseTileName);
        Wire wire = new Wire(tile, enterWireName);
        return wire.getStartWire().getWireName();
    }

}
