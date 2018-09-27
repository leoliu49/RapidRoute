import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.PIP;
import com.xilinx.rapidwright.device.Tile;

import java.lang.reflect.Array;
import java.util.*;

public class TileBrowser {

    /*
     * Collection of functions to browsing INT tiles
     */

    private static class NodeDepthPair {
        /*
         * Internal class used to track depth of BFS searches
         */
        private String nodeName;
        private int depth;

        public NodeDepthPair(String nodeName) {
            this.nodeName = nodeName;
            depth = 0;
        }

        public NodeDepthPair(String nodeName, int depth) {
            this.nodeName = nodeName;
            this.depth = depth;
        }

        public String getNodeName() {
            return nodeName;
        }

        public int getDepth() {
            return depth;
        }
    }

    public static final int MAX_BFS_DEPTH = 8;
    public static Set<String> globalNodeFootprint;

    public static final int LONG_LINE_Y = 12;
    public static final int LONG_LINE_X = 6;

    static {
        if (globalNodeFootprint == null)
            globalNodeFootprint = new HashSet<>();
    }

    public static void setGlobalNodeFootprint(Set<String> footprint) {
        globalNodeFootprint = footprint;
    }

    /*
     * Returns all end node PIP junctions corresponding to the given endpoint node
     * If fwd is true, endpoint is the PIP start node, and end node for false
     */
    public static ArrayList<PIP> browsePIPs(Design d, String tileName, String endPointNodeName, boolean fwd) {
        ArrayList<PIP> endpoints = new ArrayList<PIP>();
        for (PIP pip : d.getDevice().getTile(tileName).getPIPs()) {
            String pipEndpointName = (fwd) ? pip.getStartNode().getName() : pip.getEndNode().getName();
            if (pipEndpointName.equals(endPointNodeName)) {
                endpoints.add(pip);
            }
        }
        return endpoints;
    }

    public static ArrayList<ExitingTileJunction> findReachableExits(Design d, String tileName,
                                                                    EnteringTileJunction enJunc) {
        ArrayList<ExitingTileJunction> results = new ArrayList<ExitingTileJunction>();

        Queue<NodeDepthPair> nodeQueue = new LinkedList<NodeDepthPair>();
        nodeQueue.add(new NodeDepthPair(enJunc.getNodeName()));

        ArrayList<String> footprint = new ArrayList<String>();

        while (!nodeQueue.isEmpty()) {
            NodeDepthPair trav = nodeQueue.remove();
            if (trav.getDepth() >= TileBrowser.MAX_BFS_DEPTH)
                continue;
            for (PIP pip : browsePIPs(d, tileName, trav.getNodeName(), true)) {
                String nextNodeName = pip.getEndNode().getName();

                WireDirection dir = RouteUtil.extractExitWirePIPDirection(d, tileName, pip.getEndWireName());
                int length = RouteUtil.extractPIPExitWireLength(d, tileName, pip.getEndWireName());

                if (globalNodeFootprint.contains(nextNodeName))
                    continue;
                if (dir != null && length != 0 && !footprint.contains(nextNodeName)) {
                    ExitingTileJunction exJunc = new ExitingTileJunction(tileName, nextNodeName, pip.getEndWireName(),
                            length, dir);
                    if (!RouteUtil.isIgnorable(exJunc))
                        results.add(exJunc);
                }
                if (RouteUtil.isNodeBuffer(d, tileName, nextNodeName) && !footprint.contains(nextNodeName))
                        nodeQueue.add(new NodeDepthPair(nextNodeName, trav.getDepth() + 1));

                footprint.add(nextNodeName);
            }
        }

        return results;
    }

    public static ArrayList<ExitingTileJunction> findReachableExits(Design d, String tileName,
                                                                    EnteringTileJunction enJunc, WireDirection dir) {
        ArrayList<ExitingTileJunction> results = new ArrayList<ExitingTileJunction>();
        ArrayList<ExitingTileJunction> exJuncs = findReachableExits(d, tileName, enJunc);

        for (ExitingTileJunction exJunc : exJuncs) {
            if (exJunc.getDirection().equals(dir))
                results.add(exJunc);
        }

        return results;
    }

    public static ArrayList<ExitingTileJunction> findReachableExits(Design d, String tileName,
                                                                    EnteringTileJunction enJunc, int minWireLength,
                                                                    int maxWireLength, WireDirection dir) {
        ArrayList<ExitingTileJunction> results = new ArrayList<ExitingTileJunction>();
        ArrayList<ExitingTileJunction> exJuncs = findReachableExits(d, tileName, enJunc, dir);

        for (ExitingTileJunction exJunc : exJuncs) {
            if (exJunc.getWireLength() >= minWireLength && exJunc.getWireLength() <= maxWireLength)
                results.add(exJunc);
        }

        return results;
    }

    public static ArrayList<EnteringTileJunction> findReachableEntrances(Design d, String tileName,
                                                                         ExitingTileJunction exJunc) {
        ArrayList<EnteringTileJunction> results = new ArrayList<EnteringTileJunction>();

        Queue<NodeDepthPair> nodeQueue = new LinkedList<NodeDepthPair>();
        nodeQueue.add(new NodeDepthPair(exJunc.getNodeName()));

        ArrayList<String> footprint = new ArrayList<String>();

        while (!nodeQueue.isEmpty()) {
            NodeDepthPair trav = nodeQueue.remove();
            if (trav.getDepth() >= TileBrowser.MAX_BFS_DEPTH)
                continue;
            for (PIP pip : browsePIPs(d, tileName, trav.getNodeName(), false)) {
                String nextNodeName = pip.getStartNode().getName();

                WireDirection dir = RouteUtil.extractEnterWirePIPDirection(d, tileName, pip.getStartWireName());
                int length = RouteUtil.extractPIPEnterWireLength(d, tileName, pip.getStartWireName());

                if (globalNodeFootprint.contains(nextNodeName))
                    continue;
                if (dir != null && length != 0 && !footprint.contains(nextNodeName)) {
                    EnteringTileJunction enJunc = new EnteringTileJunction(tileName, nextNodeName,
                            pip.getStartWireName(), length, dir);
                    if (!RouteUtil.isIgnorable(enJunc))
                        results.add(enJunc);
                }
                if (RouteUtil.isNodeBuffer(d, tileName, nextNodeName)) {
                    if (!footprint.contains(nextNodeName))
                        nodeQueue.add(new NodeDepthPair(nextNodeName, trav.getDepth() + 1));
                }
                footprint.add(nextNodeName);
            }
        }

        return results;
    }

    public static ArrayList<EnteringTileJunction> findReachableEntrances(Design d, String tileName,
                                                                         ExitingTileJunction exJunc,
                                                                         WireDirection dir) {
        ArrayList<EnteringTileJunction> results = new ArrayList<EnteringTileJunction>();
        ArrayList<EnteringTileJunction> enJuncs = findReachableEntrances(d, tileName, exJunc);

        for (EnteringTileJunction enJunc : enJuncs) {
            if (enJunc.getDirection().equals(dir))
                results.add(enJunc);
        }

        return results;
    }

    public static ArrayList<EnteringTileJunction> findLongLineEntrances(Design d, String tileName,
                                                                        ExitingTileJunction exJunc,
                                                                        WireDirection dir) {
        ArrayList<EnteringTileJunction> results = new ArrayList<EnteringTileJunction>();
        ArrayList<EnteringTileJunction> enJuncs = findReachableEntrances(d, tileName, exJunc, dir);

        int longLine = RouteUtil.isVertical(dir) ? LONG_LINE_Y : LONG_LINE_X;
        for (EnteringTileJunction enJunc : enJuncs) {
            if (enJunc.getWireLength() == longLine)
                results.add(enJunc);
        }

        return results;
    }

    public static boolean isJunctionRepeatable(Design d, EnteringTileJunction enJunc) {

        ExitingTileJunction beg = enJunc.getWireSourceJunction(d);

        Tile currTile = d.getDevice().getTile(enJunc.getTileName());
        Tile begTile = d.getDevice().getTile(beg.getTileName());

        int dx = begTile.getTileXCoordinate() - currTile.getTileXCoordinate();
        int dy  = begTile.getTileYCoordinate() - currTile.getTileYCoordinate();
        EnteringTileJunction enJuncDupl = EnteringTileJunction.duplWithShift(d, enJunc, dx, dy);

        return isJunctionReachable(d, enJuncDupl, beg);
    }

    public static boolean isJunctionRepeatable(Design d, ExitingTileJunction exJunc) {

        EnteringTileJunction end = exJunc.getWireDestJunction(d);

        Tile currTile = d.getDevice().getTile(exJunc.getTileName());
        Tile endTile = d.getDevice().getTile(end.getTileName());

        int dx = endTile.getTileXCoordinate() - currTile.getTileXCoordinate();
        int dy  = endTile.getTileYCoordinate() - currTile.getTileYCoordinate();
        ExitingTileJunction exJuncDupl = ExitingTileJunction.duplWithShift(d, exJunc, dx, dy);

        return isJunctionReachable(d, end, exJuncDupl);

    }

    public static boolean isJunctionReachable(Design d, EnteringTileJunction enJunc, ExitingTileJunction exJunc) {
        if (!enJunc.getTileName().equals(exJunc.getTileName()))
            return false;
        String tileName = enJunc.getTileName();
        Queue<NodeDepthPair> nodeQueue = new LinkedList<NodeDepthPair>();
        nodeQueue.add(new NodeDepthPair(enJunc.getNodeName()));

        ArrayList<String> footprint = new ArrayList<String>();

        while (!nodeQueue.isEmpty()) {
            NodeDepthPair trav = nodeQueue.remove();
            if (trav.getDepth() >= TileBrowser.MAX_BFS_DEPTH)
                continue;
            for (PIP pip : browsePIPs(d, tileName, trav.getNodeName(), true)) {
                String nextNodeName = pip.getEndNode().getName();

                if (nextNodeName.equals(exJunc.getNodeName()))
                    return true;
                if (RouteUtil.isNodeBuffer(d, tileName, nextNodeName) && !footprint.contains(nextNodeName)
                        && !globalNodeFootprint.contains(nextNodeName))
                    nodeQueue.add(new NodeDepthPair(nextNodeName, trav.getDepth() + 1));

                footprint.add(nextNodeName);
            }
        }

        return false;
    }

    public static ArrayList<TileIntPath> findIntPaths(Design d, String tileName, EnteringTileJunction enJunc,
                                                      ExitingTileJunction exJunc) {
        ArrayList<TileIntPath> results = new ArrayList<TileIntPath>();
        Queue<TileIntPath> queue = new LinkedList<TileIntPath>();
        queue.add(new TileIntPath(enJunc, enJunc.getDirection()));

        String endNodeName = exJunc.getNodeName();

        while(!queue.isEmpty()) {
            TileIntPath trav = queue.remove();
            if (trav.getCost() >= TileBrowser.MAX_BFS_DEPTH)
                continue;
            for (PIP pip : browsePIPs(d, tileName, trav.getLastNode(), true)) {
                String nextNodeName = pip.getEndNode().getName();

                // Queue up copies of in-progress route as it expands into multiple potential routes
                TileIntPath travCopy = new TileIntPath(trav);

                if (nextNodeName.equals(endNodeName)) {
                    travCopy.setExitingJunction(exJunc);
                    results.add(travCopy);
                }
                if (RouteUtil.isNodeBuffer(d, tileName, nextNodeName)
                        && !globalNodeFootprint.contains(nextNodeName)) {
                    // To prevent live-locking in buffer traversal cycles, don't add previously traversed buffers
                    if (travCopy.addNode(nextNodeName))
                        queue.add(travCopy);
                }
            }
        }

        return results;
    }
}
