package com.uwaterloo.watcag;

import com.uwaterloo.watcag.CustomDesign;
import com.uwaterloo.watcag.common.ComplexRegister;
import com.uwaterloo.watcag.common.RegisterConnection;
import com.uwaterloo.watcag.config.ComplexRegModule;
import com.uwaterloo.watcag.config.RegisterComponent;
import com.uwaterloo.watcag.config.RegisterDefaults;
import com.uwaterloo.watcag.router.RouteForge;
import com.uwaterloo.watcag.router.SignalRoutingJob;
import com.uwaterloo.watcag.router.browser.FabricBrowser;
import com.uwaterloo.watcag.router.elements.*;
import com.uwaterloo.watcag.util.RouteUtil;
import com.uwaterloo.watcag.util.RouterLog;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.device.PIP;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class InteractiveRouter {

    private static class ProgressiveSignalPath {

        private boolean isComplete;

        private EnterWireJunction src;
        private ExitWireJunction snk;

        private LinkedList<LinkedList<String>> nodePaths;
        private LinkedList<WireJunction> template;

        public ProgressiveSignalPath(EnterWireJunction src, ExitWireJunction snk) {
            isComplete = false;

            this.src = src;
            this.snk = snk;

            nodePaths = new LinkedList<>();
            nodePaths.add(new LinkedList<>());
            nodePaths.getLast().add(src.getNodeName());

            template = new LinkedList<>();
            template.add(src);
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

        public LinkedList<WireJunction> getTemplate() {
            return template;
        }

        public String getLatestNode() {
            return nodePaths.getLast().getLast();
        }

        public List<String> getLatestInterconnectPath() {
            return nodePaths.getLast();
        }

        public String getCurrentRouteTemplateString() {

            if (template.size() == 1)
                return template.get(0).toString() + " --> [stub]";

            String repr = "";
            for (int i = 0; i < template.size(); i++)
                repr += template.get(i).toString() + " --> ";
            repr += template.get(template.size() - 1).toString();

            if (nodePaths.getLast().size() > 1)
                repr += " --> [stub]";

            return repr;
        }

        public void addBounceNode(String nodeName) {
            nodePaths.getLast().addLast(nodeName);
        }

        public void addWireNode(ExitWireJunction wireNode) {
            nodePaths.getLast().addLast(wireNode.getNodeName());

            nodePaths.addLast(new LinkedList<>());
            nodePaths.getLast().add(wireNode.getDestJunction(coreDesign).getNodeName());

            template.addLast(wireNode);
            template.addLast(wireNode.getDestJunction(coreDesign));
        }

        public void tieUpRouting() {
            nodePaths.getLast().add(snk.getNodeName());
            template.add(snk);

            isComplete = true;
        }

        public List<String> rollBackOneNode() {
            List<String> dead = new LinkedList<>();

            dead.add(nodePaths.getLast().removeLast());
            if (nodePaths.getLast().size() == 0) {
                nodePaths.removeLast();
                dead.add(nodePaths.getLast().removeLast());

                template.removeLast();
                template.removeLast();
            }

            return dead;
        }

        public List<String> rollBackInterconnectPath() {
            List<String> dead = nodePaths.removeLast();
            dead.add(nodePaths.getLast().removeLast());

            template.removeLast();
            template.removeLast();

            return dead;
        }

        private Pair<Integer, Integer> getPositionOf(String targetNodeName) {
            int pathIndex = 0;

            for (LinkedList<String> path : nodePaths) {
                pathIndex += 1;
                for (int i = 0; i < path.size(); i++) {
                    if (path.get(i).equals(targetNodeName)) {
                        return new ImmutablePair<>(pathIndex, i);
                    }
                }
            }

            return new ImmutablePair<>(-1, -1);
        }

        public List<String> rollBackToNode(String newHeadNodeName) {
            Pair<Integer, Integer> pos = getPositionOf(newHeadNodeName);
            List<String> dead = new LinkedList<>();

            while (nodePaths.size() - 1 > pos.getLeft()) {
                dead.addAll(nodePaths.removeLast());
                template.removeLast();
                template.removeLast();
            }

            if (pos.getRight() == nodePaths.getLast().size() - 1) {
                nodePaths.getLast().removeLast();
                addWireNode(new ExitWireJunction(coreDesign, RouteUtil.extractNodeTileName(newHeadNodeName),
                        RouteUtil.extractNodeWireName(newHeadNodeName)));
                ((LinkedList<String>) dead).removeLast();
            }
            else {
                while (nodePaths.getLast().size() - 1 > pos.getRight())
                    dead.add(nodePaths.getLast().removeLast());
                if (nodePaths.getLast().size() == 0) {
                    nodePaths.removeLast();
                    dead.add(nodePaths.getLast().removeLast());

                    template.removeLast();
                    template.removeLast();
                }
            }
            return dead;
        }
    }

    private static Design coreDesign;

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
                for (String pipName : component.getOutPIPNames()) {
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

    public static void printSnk() {
        RouterLog.log(route.getSnk().toString(), RouterLog.Level.NORMAL);
    }

    public static void printCurrentNet() {
        RouterLog.log(currentNet.getName(), RouterLog.Level.NORMAL);
    }

    public static void printLatestNode() {
        RouterLog.log(route.getLatestNode(), RouterLog.Level.NORMAL);
    }

    public static void printLatestInterconnectPath() {
        RouterLog.log(route.getLatestInterconnectPath().toString(), RouterLog.Level.NORMAL);
    }

    public static void printCurrentRouteTemplate() {
        RouterLog.log(route.getCurrentRouteTemplateString(), RouterLog.Level.NORMAL);
    }

    public static void printCurrentRoute() {

        for (int i = 0; i + 1 < route.getTemplate().size(); i += 2) {
            RouterLog.log(route.getTemplate().get(i).toString(), RouterLog.Level.NORMAL);
            RouterLog.indent();
            RouterLog.log(route.getNodePaths().get(i / 2).toString(), RouterLog.Level.NORMAL);
            RouterLog.indent(-1);
            RouterLog.log(route.getTemplate().get(i + 1).toString(), RouterLog.Level.NORMAL);
        }
        if (!route.isComplete()) {
            RouterLog.log(route.getTemplate().getLast().toString(), RouterLog.Level.NORMAL);
            RouterLog.indent();
            RouterLog.log(route.getNodePaths().getLast().toString(), RouterLog.Level.NORMAL);
            RouterLog.indent(-1);
            RouterLog.log("[stub]", RouterLog.Level.NORMAL);
        }
    }

    public static void printNodeFanOut(String nodeName) {
        if (nodeName.equals(""))
            nodeName = route.getLatestNode();

        String tileName = RouteUtil.extractNodeTileName(nodeName);

        HashSet<String> outWires = new HashSet<>();

        RouterLog.log("Bounce nodes:", RouterLog.Level.NORMAL);
        RouterLog.indent();
        for (PIP pip : FabricBrowser.getFwdPIPs(coreDesign, tileName, nodeName)) {
            String nextNodeName = RouteUtil.getPIPNodeName(tileName, pip.getEndWireName());

            if (RouteForge.isLocked(nextNodeName))
                continue;

            WireDirection dir = RouteUtil.extractExitWireDirection(coreDesign, tileName, pip.getEndWireName());
            int wireLength = RouteUtil.extractExitWireLength(coreDesign, tileName, pip.getEndWireName());

            if (dir != null && dir!= WireDirection.SELF && wireLength != 0 && !RouteUtil.isClkNode(nextNodeName)) {
                outWires.add(pip.getEndWireName());
            }

            if (RouteUtil.isNodeBuffer(coreDesign, tileName, nextNodeName)) {
                RouterLog.log(nextNodeName, RouterLog.Level.NORMAL);
            }
        }
        RouterLog.indent(-1);

        RouterLog.log("Wire nodes:", RouterLog.Level.NORMAL);
        RouterLog.indent();
        for (String wireName : outWires)
            RouterLog.log(new ExitWireJunction(coreDesign, tileName, wireName).toString(), RouterLog.Level.NORMAL);
        RouterLog.indent(-1);
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
        for (String revert : route.rollBackOneNode())
            RouteForge.unlock(revert);
        RouterLog.log("Rolling route back to <" + route.getLatestNode() + ">.", RouterLog.Level.NORMAL);
    }

    public static void rollBackInterconnectPath() {
        for(String revert : route.rollBackInterconnectPath())
            RouteForge.unlock(revert);
        RouterLog.log("Rolling route back to <" + route.getLatestNode() + ">.", RouterLog.Level.NORMAL);
    }

    public static void rollBackToNode(String nodeName) {
        for (String revert: route.rollBackToNode(nodeName))
            RouteForge.unlock(revert);
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
                route.addBounceNode(result.getRoute().get(0).getNodeName(i));

            String lastNodeName = result.getSnk().getNodeName();
            WireDirection dir = RouteUtil.extractExitWireDirection(coreDesign, result.getSnk().getTileName(),
                    result.getSnk().getWireName());
            int wireLength = RouteUtil.extractExitWireLength(coreDesign, result.getSnk().getTileName(),
                    result.getSnk().getWireName());

            if (dir != null && dir!= WireDirection.SELF && wireLength != 0 && !RouteUtil.isClkNode(lastNodeName))
                route.addWireNode(new ExitWireJunction(coreDesign, result.getSnk().getTileName(), result.getSnk().getWireName()));
            else
                route.addBounceNode(lastNodeName);
        }
        else {
            for (int i = 1; i < result.getRoute().get(0).getNodePath().size() - 1; i++)
                route.addBounceNode(result.getRoute().get(0).getNodeName(i));
            route.addWireNode((ExitWireJunction) result.getTemplate().getTemplate(1));
            for (int i = 1; i < result.getRoute().size() - 1; i++) {
                for (int j = 1; j < result.getRoute().get(i).getNodePath().size() - 1; j++)
                    route.addBounceNode(result.getRoute().get(i).getNodeName(j));
                route.addWireNode((ExitWireJunction) result.getTemplate().getTemplate(2 * i + 1));
            }
            for (int i = 1; i < result.getRoute().get(result.getRoute().size() - 1).getNodePath().size() - 1; i++)
                route.addBounceNode(result.getRoute().get(result.getRoute().size() - 1).getNodeName(i));

            String lastNodeName = result.getSnk().getNodeName();
            WireDirection dir = RouteUtil.extractExitWireDirection(coreDesign, result.getSnk().getTileName(),
                    result.getSnk().getWireName());
            int wireLength = RouteUtil.extractExitWireLength(coreDesign, result.getSnk().getTileName(),
                    result.getSnk().getWireName());

            if (dir != null && dir!= WireDirection.SELF && wireLength != 0 && !RouteUtil.isClkNode(lastNodeName))
                route.addWireNode(new ExitWireJunction(coreDesign, result.getSnk().getTileName(), result.getSnk().getWireName()));
            else
                route.addBounceNode(lastNodeName);
        }

        for (TilePath path : result.getRoute()) {
            for (String nodeName : path.getNodePath())
                RouteForge.lock(nodeName);
        }

        RouterLog.log("Updating route:", RouterLog.Level.NORMAL);
        printCurrentRoute();

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
    }

}
