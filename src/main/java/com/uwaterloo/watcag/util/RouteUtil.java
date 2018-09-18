package com.uwaterloo.watcag.util;

import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.uwaterloo.watcag.router.browser.FabricBrowser;
import com.uwaterloo.watcag.router.elements.WireDirection;
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

    public static boolean isHorizontal(WireDirection dir) {
        return dir.equals(WireDirection.EAST) || dir.equals(WireDirection.WEST);
    }

    public static boolean isVertical(WireDirection dir) {
        return dir.equals(WireDirection.NORTH) || dir.equals(WireDirection.SOUTH);
    }

    public static boolean isParallel(WireDirection dir1, WireDirection dir2) {
        if (dir1 == null || dir2 == null)
            return false;

        if (dir1.equals(WireDirection.NORTH) || dir1.equals(WireDirection.SOUTH))
            return dir2.equals(WireDirection.NORTH) || dir2.equals(WireDirection.SOUTH);
        else if (dir1.equals(WireDirection.EAST) || dir1.equals(WireDirection.WEST))
            return dir2.equals(WireDirection.EAST) || dir2.equals(WireDirection.WEST);
        else
            return false;
    }

    public static boolean isOrthogonal(WireDirection dir1, WireDirection dir2) {
        if (dir1 == null || dir2 == null)
            return false;

        if (dir1.equals(WireDirection.NORTH) || dir1.equals(WireDirection.SOUTH))
            return dir2.equals(WireDirection.EAST) || dir2.equals(WireDirection.WEST);
        else if (dir1.equals(WireDirection.EAST) || dir1.equals(WireDirection.WEST))
            return dir2.equals(WireDirection.NORTH) || dir2.equals(WireDirection.SOUTH);
        else
            return false;
    }

    public static ArrayList<WireDirection> primaryDirections(int dx, int dy) {
        ArrayList<WireDirection> dirs = new ArrayList<>();

        // Case: same tile
        if (dx == 0 && dy == 0) {
            dirs.add(WireDirection.SELF);
            return dirs;
        }

        // Case: 1 direction only
        if (dx == 0) {
            dirs.add(dy > 0 ? WireDirection.NORTH : WireDirection.SOUTH);
            return dirs;
        }
        if (dy == 0) {
            dirs.add(dx > 0 ? WireDirection.EAST : WireDirection.WEST);
            return dirs;
        }

        // Case: 2 orthogonal directions
        if (dx > 0)
            dirs.add(WireDirection.EAST);
        else
            dirs.add(WireDirection.WEST);
        if (dy > 0)
            dirs.add(WireDirection.NORTH);
        else
            dirs.add(WireDirection.SOUTH);

        return dirs;
    }

    public static WireDirection primaryHDirection(int dx) {
        if (dx == 0)
            return null;
        return dx > 0 ? WireDirection.EAST : WireDirection.WEST;
    }

    public static WireDirection primaryVDirection(int dy) {
        if (dy == 0)
            return null;
        return dy > 0 ? WireDirection.NORTH : WireDirection.SOUTH;
    }

    public static String getPIPNodeName(String tileName, String wireName) {
        return tileName + "/" + wireName;
    }

    public static WireDirection extractExitWireDirection(Design d, String tileName, String exitWireName) {
        Tile baseTile = d.getDevice().getTile(tileName);
        Tile destTile = null;
        for (Wire endWire : baseTile.getWireConnections(exitWireName)) {
            if (endWire.getTile().getTileTypeEnum().equals(TileTypeEnum.INT)) {
                destTile = endWire.getTile();
                break;
            }
        }

        if (destTile == null)
            return null;
        if (destTile.equals(baseTile))
            return WireDirection.SELF;
        return baseTile.getTileXCoordinate() == destTile.getTileXCoordinate()
                ? (destTile.getTileYCoordinate() > baseTile.getTileYCoordinate() ? WireDirection.NORTH : WireDirection.SOUTH)
                : (destTile.getTileXCoordinate() > baseTile.getTileXCoordinate() ? WireDirection.EAST : WireDirection.WEST);
    }

    public static int extractExitWireLength(Design d, String tileName, String exitWireName) {
        Tile baseTile = d.getDevice().getTile(tileName);
        Tile destTile = null;
        for (Wire endWire : baseTile.getWireConnections(exitWireName)) {
            if (endWire.getTile().getTileTypeEnum().equals(TileTypeEnum.INT)) {
                destTile = endWire.getTile();
                break;
            }
        }

        if (destTile == null)
            return 0;
        return (baseTile.getTileXCoordinate() == destTile.getTileXCoordinate())
                ? Math.abs(destTile.getTileYCoordinate() - baseTile.getTileYCoordinate())
                : Math.abs(destTile.getTileXCoordinate() - baseTile.getTileXCoordinate());
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

    public static String extractNodeTileName(String nodeName) {
        return nodeName.split("/")[0];
    }

    public static String extractNodeWireName(String nodeName) {
        return nodeName.split("/")[1];
    }

}
