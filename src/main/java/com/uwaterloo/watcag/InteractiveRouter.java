package com.uwaterloo.watcag;

import com.uwaterloo.watcag.CustomDesign;
import com.uwaterloo.watcag.common.ComplexRegister;
import com.uwaterloo.watcag.common.RegisterConnection;
import com.uwaterloo.watcag.config.ComplexRegModule;
import com.uwaterloo.watcag.config.RegisterComponent;
import com.uwaterloo.watcag.config.RegisterDefaults;
import com.uwaterloo.watcag.router.RouteForge;
import com.uwaterloo.watcag.router.SignalRoutingJob;
import com.uwaterloo.watcag.router.TemplateSearchJob;
import com.uwaterloo.watcag.router.browser.FabricBrowser;
import com.uwaterloo.watcag.router.elements.*;
import com.uwaterloo.watcag.util.RouteUtil;
import com.uwaterloo.watcag.util.RouterLog;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.device.IntentCode;
import com.xilinx.rapidwright.device.PIP;
import com.xilinx.rapidwright.device.Tile;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class InteractiveRouter {

    private static class ProgressiveSignalPath {

        private boolean isComplete;

        private EnterWireJunction src;
        private ExitWireJunction snk;

        private LinkedList<LinkedList<String>> nodePaths;

        public ProgressiveSignalPath(EnterWireJunction src, ExitWireJunction snk) {
            isComplete = false;

            this.src = src;
            this.snk = snk;

            nodePaths = new LinkedList<>();
            nodePaths.add(new LinkedList<>());
            nodePaths.getLast().add(src.getNodeName());
        }

        public boolean isComplete() {
            return isComplete;
        }

        public EnterWireJunction getSrc() {
            return src;
        }

        public ExitWireJunction getSnk() {
            return snk;
        }

        public LinkedList<LinkedList<String>> getNodePaths() {
            return nodePaths;
        }

        public String getLatestNode() {
            return nodePaths.getLast().getLast();
        }

        public List<String> getLatestInterconnectPath() {
            return nodePaths.getLast();
        }

        public void addBounceNode(String nodeName) {
            nodePaths.getLast().addLast(nodeName);
        }

        public void addWireNode(ExitWireJunction wireNode) {
            nodePaths.getLast().addLast(wireNode.getNodeName());

            nodePaths.addLast(new LinkedList<>());
            nodePaths.getLast().add(wireNode.getDestJunction(coreDesign).getNodeName());
        }

        public void tieUpRouting() {
            nodePaths.getLast().add(snk.getNodeName());
            isComplete = true;
        }

        public List<String> rollBackOneNode() {
            List<String> dead = new LinkedList<>();

            dead.add(nodePaths.getLast().removeLast());
            if (nodePaths.getLast().size() == 0) {
                nodePaths.removeLast();
                dead.add(nodePaths.getLast().removeLast());
            }

            return dead;
        }

        public List<String> rollBackInterconnectPath() {
            List<String> dead = nodePaths.removeLast();
            dead.add(nodePaths.getLast().removeLast());
            return dead;
        }

        public List<String> rollBackToNode(String newHeadNodeName) {

            List<String> dead = new LinkedList<>();
            while (true) {
                LinkedList<String> latestPath = nodePaths.getLast();

                if (latestPath.getLast().equals(newHeadNodeName))
                    return dead;

                if (latestPath.size() == 1) {
                    if (nodePaths.size() == 1)
                        return dead;
                    if (nodePaths.get(nodePaths.size() - 2).getLast().equals(newHeadNodeName))
                        return dead;
                    else
                        nodePaths.removeLast();
                }
                else
                    dead.add(latestPath.removeLast());

            }
        }
    }

    public static Design coreDesign;

    private static RegisterConnection connection;
    private static ComplexRegister srcRegister;
    private static ComplexRegister snkRegister;

    private static Net currentNet;
    private static int currentBit;
    private static ProgressiveSignalPath route;

    private static <T> T getAny(Set<T> set) {
        for (T t : set) {
            return t;
        }
        return null;
    }

    public static void initializeRouter(Design d, RegisterConnection connection, int bit) {
        coreDesign = d;

        srcRegister = connection.getSrcReg();
        snkRegister = connection.getSnkReg();

        {
            EnterWireJunction src = null;
            int i = 0;
            for (RegisterComponent component : srcRegister.getComponents()) {
                String tileName = coreDesign.getDevice().getSite(component.getSiteName()).getIntTile().getName();
                for (String pipName : component.getOutPIPNames()) {
                    if (i == connection.getSrcRegLowestBit() + bit) {
                        src = EnterWireJunction.newSrcJunction(tileName, pipName);
                        currentNet = coreDesign.getNet(srcRegister.getName() + "_" + component.getName() + "/"
                                + RegisterDefaults.OUTPUT_NAME + "[" + (connection.getSrcRegLowestBit() + bit) + "]");
                    }
                    i += 1;
                }

                if (i > connection.getSrcRegLowestBit() + bit)
                    break;
            }

            ExitWireJunction snk = null;
            i = 0;
            for (RegisterComponent component : snkRegister.getComponents()) {
                String tileName = coreDesign.getDevice().getSite(component.getSiteName()).getIntTile().getName();
                for (String pipName : component.getInPIPNames()) {
                    if (i == connection.getSnkRegLowestBit() + bit)
                        snk = ExitWireJunction.newSnkJunction(tileName, pipName);
                    i += 1;
                }

                if (i > connection.getSnkRegLowestBit() + bit)
                    break;
            }

            currentBit = bit;
            route = new ProgressiveSignalPath(src, snk);

        }

        RouteForge.lock(route.getSrc().getNodeName());
        RouteForge.lock(route.getSnk().getNodeName());

        RouterLog.log("Entering interactive routing for " + connection.toString() + ".", RouterLog.Level.WARNING);
        RouterLog.indent();
        RouterLog.log("Routing bit " + currentBit + " on physical net: <" + currentNet.getName() + ">.",
                RouterLog.Level.NORMAL);
        RouterLog.log("Source: " + route.getSrc().toString() + ".", RouterLog.Level.NORMAL);
        RouterLog.log("Sink: " + route.getSnk().toString() + ".", RouterLog.Level.NORMAL);
        RouterLog.indent(-1);
    }

    public static void printSrc() {
        RouterLog.log(route.getSrc().toString(), RouterLog.Level.NORMAL);
    }

    public static String getSrcName() {
        return route.getSrc().toString();
    }

    public static void printSnk() {
        RouterLog.log(route.getSnk().toString(), RouterLog.Level.NORMAL);
    }

    public static String getSnkName() {
        return route.getSnk().toString();
    }

    public static void printCurrentNet() {
        RouterLog.log(currentNet.getName(), RouterLog.Level.NORMAL);
    }

    public static void printLatestNode() {
        RouterLog.log(route.getLatestNode(), RouterLog.Level.NORMAL);
    }

    public static String getLatestNodeName() {
        return route.getLatestNode();
    }

    public static void printLatestInterconnectPath() {
        RouterLog.log(route.getLatestInterconnectPath().toString(), RouterLog.Level.NORMAL);
    }

    public static String[] getLatestInterconnectPath() {
        String[] path = new String[route.getLatestInterconnectPath().size()];
        for (int i = 0; i < route.getLatestInterconnectPath().size(); i++)
            path[i] = route.getLatestInterconnectPath().get(i);
        return path;
    }

    public static void printAllBounceNodesInTile() {
        for (String nodeName : getAllBounceNodesInTile())
            RouterLog.log(nodeName, RouterLog.Level.NORMAL);
    }

    public static String[] getAllBounceNodesInTile() {
        ArrayList<String> nodeList = new ArrayList<>();

        Tile refTile = coreDesign.getDevice().getTile(route.getSrc().getTileName());
        for (String wireName : refTile.getWireNames()) {
            if (wireName.startsWith("QLND") || wireName.startsWith("SDND"))
                continue;

            if (refTile.getWireIntentCode(refTile.getWireIndex(wireName)).equals(IntentCode.NODE_PINBOUNCE))
                nodeList.add(wireName);
            else if (RouteUtil.isNodeBuffer(coreDesign, refTile.getName(), refTile.getName() + "/" + wireName))
                nodeList.add(wireName);
        }

        String[] results = new String[nodeList.size()];
        results = nodeList.toArray(results);
        return results;
    }

    public static void printAllWiresInTile() {
        for (String wireName : getAllWiresInTile())
            RouterLog.log(wireName, RouterLog.Level.NORMAL);
    }

    public static String[] getAllWiresInTile() {
        ArrayList<String> wireList = new ArrayList<>();

        Tile refTile = coreDesign.getDevice().getTile(route.getSrc().getTileName());
        for (String wireName : refTile.getWireNames()) {
            if (wireName.startsWith("QLND") || wireName.startsWith("SDND"))
                continue;

            WireDirection dir = RouteUtil.extractExitWireDirection(coreDesign, refTile.getName(), wireName);
            int wireLength = RouteUtil.extractExitWireLength(coreDesign, refTile.getName(), wireName);

            if (dir != null && dir!= WireDirection.SELF && wireLength != 0 && !RouteUtil.isClkNode(wireName))
                wireList.add(wireName);
        }

        String[] results = new String[wireList.size()];
        results = wireList.toArray(results);
        return results;
    }

    public static void printCurrentRoute() {

        for (int i = 0; i < route.getNodePaths().size() - 1; i++) {
            RouterLog.log(route.getNodePaths().get(i).get(0), RouterLog.Level.NORMAL);
            RouterLog.indent();
            RouterLog.log(route.getNodePaths().get(i).toString(), RouterLog.Level.NORMAL);
            RouterLog.indent(-1);
            RouterLog.log(route.getNodePaths().get(i).get(route.getNodePaths().get(i).size() - 1), RouterLog.Level.NORMAL);
        }
        RouterLog.log(route.getNodePaths().get(route.getNodePaths().size() - 1).get(0), RouterLog.Level.NORMAL);
        RouterLog.indent();
        RouterLog.log(route.getNodePaths().get(route.getNodePaths().size() - 1).toString(), RouterLog.Level.NORMAL);
        RouterLog.indent(-1);
        if (route.isComplete()) {
            RouterLog.log(route.getNodePaths().get(route.getNodePaths().size() - 1)
                    .get(route.getNodePaths().get(route.getNodePaths().size() - 1).size() - 1), RouterLog.Level.NORMAL);
        }
        else
            RouterLog.log("[stub]", RouterLog.Level.NORMAL);
    }

    public static String[] getRoutingConstraint() {
        ArrayList<String> constraint = new ArrayList<>();

        constraint.addAll(route.getNodePaths().get(0));
        for (int i = 1; i < route.getNodePaths().size(); i++) {
            for (int j = 1; j < route.getNodePaths().get(i).size(); j++)
                constraint.add(route.getNodePaths().get(i).get(j));
        }

        String[] results = new String[constraint.size()];
        results = constraint.toArray(results);

        return results;
    }

    public static String[] getAllNodesInRoute() {
        int totalSize = 0;
        for (LinkedList<String> path : route.getNodePaths())
            totalSize += path.size();

        String[] allNodes = new String[totalSize];
        int i = 0;
        for (LinkedList<String> path : route.getNodePaths()) {
            for (String nodeName : path) {
                allNodes[i] = nodeName;
                i += 1;
            }
        }

        return allNodes;
    }

    public static void printNodeFanOut(String nodeName) {
        if (nodeName.equals(""))
            nodeName = route.getLatestNode();

        String[][] fanOut = getNodeFanOut(nodeName);
        String tileName = RouteUtil.extractNodeTileName(nodeName);

        RouterLog.log("Bounce nodes:", RouterLog.Level.NORMAL);
        RouterLog.indent();
        for (String bounceNodeName : fanOut[0]) {
            RouterLog.log(bounceNodeName, RouterLog.Level.NORMAL);
        }
        RouterLog.indent(-1);

        RouterLog.log("Wire nodes:", RouterLog.Level.NORMAL);
        RouterLog.indent();
        for (String wireNodeName : fanOut[1]) {
            String wireName = RouteUtil.extractNodeWireName(wireNodeName);
            RouterLog.log(new ExitWireJunction(coreDesign, tileName, wireName).toString(), RouterLog.Level.NORMAL);
        }
        RouterLog.indent(-1);
    }

    public static void printTileFanOut(String nodeName) {
        if (nodeName.equals(""))
            nodeName = route.getLatestNode();

        String[] fanOut = getTileFanOut(nodeName);
        String tileName = RouteUtil.extractNodeTileName(nodeName);

        RouterLog.log("Wire nodes:", RouterLog.Level.NORMAL);
        RouterLog.indent();
        for (String wireNodeName : fanOut) {
            String wireName = RouteUtil.extractNodeWireName(wireNodeName);
            RouterLog.log(new ExitWireJunction(coreDesign, tileName, wireName).toString(), RouterLog.Level.NORMAL);
        }
        RouterLog.indent(-1);
    }

    public static void printReachableEntrances(String nodeName) {
        if (nodeName.equals(""))
            nodeName = route.getSnk().getNodeName();

        String[] fanOut = getReachableEntrances(nodeName);
        String tileName = RouteUtil.extractNodeTileName(nodeName);

        RouterLog.log("Wire nodes:", RouterLog.Level.NORMAL);
        RouterLog.indent();
        for (String wireNodeName : fanOut) {
            String wireName = RouteUtil.extractNodeWireName(wireNodeName);
            RouterLog.log(new EnterWireJunction(coreDesign, tileName, wireName).toString(), RouterLog.Level.NORMAL);
        }
        RouterLog.indent(-1);
    }

    public static String[][] getNodeFanOut(String nodeName) {
        String[][] fanOut = new String[2][];
        if (nodeName.equals(""))
            nodeName = route.getLatestNode();

        String tileName = RouteUtil.extractNodeTileName(nodeName);

        HashSet<String> bounces = new HashSet<>();
        HashSet<String> outWires = new HashSet<>();

        for (PIP pip : FabricBrowser.getFwdPIPs(coreDesign, tileName, nodeName)) {
            String nextNodeName = RouteUtil.getPIPNodeName(tileName, pip.getEndWireName());

            if (RouteForge.isLocked(nextNodeName))
                continue;

            WireDirection dir = RouteUtil.extractExitWireDirection(coreDesign, tileName, pip.getEndWireName());
            int wireLength = RouteUtil.extractExitWireLength(coreDesign, tileName, pip.getEndWireName());

            if (dir != null && dir!= WireDirection.SELF && wireLength != 0 && !RouteUtil.isClkNode(nextNodeName))
                outWires.add(pip.getEndWireName());

            if (RouteUtil.isNodeBuffer(coreDesign, tileName, nextNodeName))
                bounces.add(nextNodeName);
        }

        {
            fanOut[0] = new String[bounces.size()];
            int i = 0;
            for (String bounceNodeName : bounces)
                fanOut[0][i++] = bounceNodeName;
        }
        {
            fanOut[1] = new String[outWires.size()];
            int i = 0;
            for (String wireName : outWires)
                fanOut[1][i++] = tileName + "/" + wireName;
        }

        return fanOut;
    }

    public static String[] getTileFanOut(String nodeName) {
        if (nodeName.equals(""))
            nodeName = route.getLatestNode();

        Set<ExitWireJunction> exits = FabricBrowser.findReachableExits(coreDesign, new EnterWireJunction(coreDesign,
                RouteUtil.extractNodeTileName(nodeName), RouteUtil.extractNodeWireName(nodeName)));

        String[] fanOut = new String[exits.size()];
        int i = 0;
        for (ExitWireJunction exit : exits)
            fanOut[i++] = exit.getNodeName();
        return fanOut;
    }

    public static String[] getReachableEntrances(String nodeName) {
        if (nodeName.equals(""))
            nodeName = route.getSnk().getNodeName();

        Set<EnterWireJunction> entrances = FabricBrowser.findReachableEntrances(coreDesign, FabricBrowser.TILE_TRAVERSAL_MAX_DEPTH,
                ExitWireJunction.newSnkJunction(RouteUtil.extractNodeTileName(nodeName), RouteUtil.extractNodeWireName(nodeName)));

        String[] fanOut = new String[entrances.size()];
        int i = 0;
        for (EnterWireJunction entrance : entrances)
            fanOut[i++] = entrance.getNodeName();
        return fanOut;
    }

    public static void addBounceNode(String nodeName) {
        if (nodeName.equals(route.getSnk().getNodeName())) {
            route.tieUpRouting();
            RouterLog.log("Adding sink node <" + route.getSnk().toString() + ">. The route is now complete.",
                    RouterLog.Level.NORMAL);
            return;
        }
        route.addBounceNode(nodeName);
        RouterLog.log("Adding bounce node <" + nodeName + ">.", RouterLog.Level.NORMAL);

        RouteForge.lock(nodeName);
    }

    public static void addWireNode(String nodeName) {
        if (nodeName.equals(route.getSnk().getNodeName())) {
            route.tieUpRouting();
            RouterLog.log("Adding sink node " + route.getSnk().toString() + ". The route is now complete.",
                    RouterLog.Level.NORMAL);
            return;
        }

        ExitWireJunction exit = new ExitWireJunction(coreDesign, RouteUtil.extractNodeTileName(nodeName),
                RouteUtil.extractNodeWireName(nodeName));

        route.addWireNode(exit);
        RouterLog.log("Adding wire node <" + exit.toString() + ">.", RouterLog.Level.NORMAL);
        RouterLog.indent();
        RouterLog.log("Signal is now routed to " + route.getLatestNode(), RouterLog.Level.NORMAL);
        RouterLog.indent(-1);

        RouteForge.lock(nodeName);
        RouteForge.lock(route.getLatestNode());
    }

    public static void rollBackOneNode() {
        for (String revert : route.rollBackOneNode()) {
            RouteForge.free(revert);
        }
        route.isComplete = false;
        RouterLog.log("Rolling route back to <" + route.getLatestNode() + ">.", RouterLog.Level.NORMAL);
    }

    public static void rollBackInterconnectPath() {
        for(String revert : route.rollBackInterconnectPath())
            RouteForge.free(revert);
        route.isComplete = false;
        RouterLog.log("Rolling route back to <" + route.getLatestNode() + ">.", RouterLog.Level.NORMAL);
    }

    public static void rollBackToNode(String nodeName) {
        for (String revert: route.rollBackToNode(nodeName))
            RouteForge.free(revert);
        RouterLog.log("Rolling route back to <" + route.getLatestNode() + ">.", RouterLog.Level.NORMAL);
    }

    public static void autoRouteToNode(String destNodeName) throws Exception {

        RouterLog.log("Auto-routing from <" + route.getLatestNode() + "> to <" + destNodeName + ">.", RouterLog.Level.NORMAL);
        SignalRoutingJob job = new SignalRoutingJob(coreDesign,
                EnterWireJunction.newSrcJunction(RouteUtil.extractNodeTileName(route.getLatestNode()),
                        RouteUtil.extractNodeWireName(route.getLatestNode())),
                ExitWireJunction.newSnkJunction(RouteUtil.extractNodeTileName(destNodeName),
                        RouteUtil.extractNodeWireName(destNodeName)));
        job.run();

        CustomRoute result = job.getRoute();

        if (result.getRoute().size() == 1) {
            for (int i = 1; i < result.getRoute().get(0).getNodePath().size() - 1; i++)
                addBounceNode(result.getRoute().get(0).getNodeName(i));

            String lastNodeName = result.getSnk().getNodeName();
            WireDirection dir = RouteUtil.extractExitWireDirection(coreDesign, result.getSnk().getTileName(),
                    result.getSnk().getWireName());
            int wireLength = RouteUtil.extractExitWireLength(coreDesign, result.getSnk().getTileName(),
                    result.getSnk().getWireName());

            if (dir != null && dir!= WireDirection.SELF && wireLength != 0 && !RouteUtil.isClkNode(lastNodeName))
                addWireNode(lastNodeName);
            else
                addBounceNode(lastNodeName);
        }
        else {
            for (int i = 1; i < result.getRoute().get(0).getNodePath().size() - 1; i++)
                addBounceNode(result.getRoute().get(0).getNodeName(i));
            addWireNode(result.getTemplate().getTemplate(1).getNodeName());
            for (int i = 1; i < result.getRoute().size() - 1; i++) {
                for (int j = 1; j < result.getRoute().get(i).getNodePath().size() - 1; j++)
                    addBounceNode(result.getRoute().get(i).getNodeName(j));
                addWireNode(result.getTemplate().getTemplate(2 * i + 1).getNodeName());
            }
            for (int i = 1; i < result.getRoute().get(result.getRoute().size() - 1).getNodePath().size() - 1; i++)
                addBounceNode(result.getRoute().get(result.getRoute().size() - 1).getNodeName(i));

            String lastNodeName = result.getSnk().getNodeName();
            WireDirection dir = RouteUtil.extractExitWireDirection(coreDesign, result.getSnk().getTileName(),
                    result.getSnk().getWireName());
            int wireLength = RouteUtil.extractExitWireLength(coreDesign, result.getSnk().getTileName(),
                    result.getSnk().getWireName());

            if (dir != null && dir!= WireDirection.SELF && wireLength != 0 && !RouteUtil.isClkNode(lastNodeName))
                addWireNode(lastNodeName);
            else
                addBounceNode(lastNodeName);
        }

        for (TilePath path : result.getRoute()) {
            for (String nodeName : path.getNodePath())
                RouteForge.lock(nodeName);
        }

        RouterLog.log("Updating route:", RouterLog.Level.NORMAL);
        printCurrentRoute();

    }

    /*
    public static void rerouteTemplateManyWays(String runPrefix) throws Exception {
        //SignalRoutingJob job = new SignalRoutingJob(coreDesign, route.getSrc(), route.getSnk());
        //job.run();

        ArrayList<ArrayList<LinkedList<String>>> pathsDB = new ArrayList<>();
        ArrayList<Pair<String, String>> template = new ArrayList<>();

        for (LinkedList<String> path : route.getNodePaths()) {
            template.add(new ImmutablePair<>(path.getFirst(), path.getLast()));
        }

        rollBackToNode(route.getSrc().getNodeName());

        for (Pair<String, String> junctions : template) {
            ArrayList<LinkedList<String>> pathChoices = new ArrayList<>();
            ArrayList<TilePath> paths = FabricBrowser.findTilePaths(coreDesign, 8,
                    new EnterWireJunction(coreDesign,
                            RouteUtil.extractNodeTileName(junctions.getLeft()),
                            RouteUtil.extractNodeWireName(junctions.getLeft())),
                    new ExitWireJunction(coreDesign,
                            RouteUtil.extractNodeTileName(junctions.getRight()),
                            RouteUtil.extractNodeWireName(junctions.getRight())));
            for (TilePath path : paths) {
                pathChoices.add(new LinkedList<>(path.getNodePath()));
            }
            pathsDB.add(pathChoices);
            System.out.println(pathChoices.size());
        }
    }
    */

    public static String[][] findInterconnectPaths(String srcNodeName, String snkNodeName, int maxDepth) {
        String tileName = RouteUtil.extractNodeTileName(srcNodeName);

        String srcWireName = RouteUtil.extractNodeWireName(srcNodeName);
        String snkWireName = RouteUtil.extractNodeWireName(snkNodeName);

        ArrayList<TilePath> paths = FabricBrowser.findTilePaths(coreDesign, maxDepth,
                new EnterWireJunction(coreDesign, tileName, srcWireName),
                new ExitWireJunction(coreDesign, tileName, snkWireName));

        String[][] results = new String[paths.size()][];
        for (int i = 0; i < paths.size(); i++) {
            TilePath path = paths.get(i);
            results[i] = new String[path.getNodePath().size()];
            for (int j = 0; j < path.getNodePath().size(); j++)
                results[i][j] = path.getNodeName(j);
        }
        return results;
    }

    public static void addPIP(String startNode, String endNode) {
        RouteForge.findAndRoute(coreDesign, currentNet, RouteUtil.extractNodeTileName(startNode),
                startNode, endNode);
    }

    public static void commit() {
        RouterLog.log("Routing route on net <" + currentNet.getName() + ">.", RouterLog.Level.NORMAL);
        for (LinkedList<String> path : route.getNodePaths()) {
            for (int i = 0; i < path.size() - 1; i++) {
                RouteForge.findAndRoute(coreDesign, currentNet, RouteUtil.extractNodeTileName(path.getFirst()),
                        path.get(i), path.get(i + 1));
            }
        }
    }

    public static void uncommit() {
        RouterLog.log("Physical net detached.", RouterLog.Level.NORMAL);
        currentNet.unroute();
        route.isComplete = false;
    }

}
