package com.uwaterloo.watcag.router;

import com.uwaterloo.watcag.common.ComplexRegister;
import com.uwaterloo.watcag.common.RegisterConnection;
import com.uwaterloo.watcag.config.RegisterComponent;
import com.uwaterloo.watcag.config.RegisterDefaults;
import com.uwaterloo.watcag.router.elements.*;
import com.uwaterloo.watcag.util.RouterLog;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.device.Tile;
import com.xilinx.rapidwright.edif.*;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DesignRouter {

    /*
     * Top-level router which router the entire design
     */

    private static Design coreDesign;

    public static ExecutorService executor;

    private static final Set<RegisterConnection> externalConnectionSet = new HashSet<>();

    private static final Set<RegisterConnection> connectionSet = new LinkedHashSet<>();
    private static final HashMap<RegisterConnection, ArrayList<RegisterConnection>> uniqueConnectionsSet = new LinkedHashMap<>();
    private static final HashMap<RegisterConnection, RouteFootprint> routesMap = new HashMap<>();

    private static final Set<Pair<RegisterConnection, CustomRoute>> failedRoutes = new HashSet<>();

    private static int getXOffsetOfCongruentConnection(RegisterConnection ref, RegisterConnection offset) {
        Tile refIntTile = coreDesign.getDevice().getSite(ref.getSrcReg().getComponent(0).getSiteName()).getIntTile();
        Tile offsetIntTile = coreDesign.getDevice().getSite(offset.getSrcReg().getComponent(0).getSiteName()).getIntTile();

        return offsetIntTile.getTileXCoordinate() - refIntTile.getTileXCoordinate();
    }

    private static int getYOffsetOfCongruentConnection(RegisterConnection ref, RegisterConnection offset) {
        Tile refIntTile = coreDesign.getDevice().getSite(ref.getSrcReg().getComponent(0).getSiteName()).getIntTile();
        Tile offsetIntTile = coreDesign.getDevice().getSite(offset.getSrcReg().getComponent(0).getSiteName()).getIntTile();

        return offsetIntTile.getTileYCoordinate() - refIntTile.getTileYCoordinate();
    }

    private static RouteFootprint copyFootprintWithOffset(RegisterConnection connection, RouteFootprint ref, int dx, int dy) {


        RouteFootprint footprint = new RouteFootprint();

        ComplexRegister srcReg = connection.getSrcReg();
        int bitIndex = 0;
        int routeIndex = 0;
        for (RegisterComponent component : srcReg.getComponents()) {
            for (int i = 0; i < component.getBitWidth(); i++, bitIndex++) {
                if (bitIndex >= connection.getSrcRegLowestBit() && bitIndex <= connection.getSrcRegHighestBit()) {
                    Net net = coreDesign.getNet(srcReg.getName() + "_" + component.getName() + "/"
                            + RegisterDefaults.OUTPUT_NAME + "[" + i + "]");

                    CustomRoute routeCopy = ref.getRouteByIndex(routeIndex).copyWithOffset(coreDesign, dx, dy);

                    footprint.addRoute(routeCopy, net);
                    routeIndex += 1;
                }
            }
        }

        return footprint;
    }

    private static RouteFootprint compileFootprint(RegisterConnection connection, ArrayList<CustomRoute> routes) {
        RouteFootprint footprint = new RouteFootprint();

        int bitIndex = 0;
        int routeIndex = 0;
        for (RegisterComponent component : connection.getSrcReg().getComponents()) {
            for (int i = 0; i < component.getBitWidth(); i++, bitIndex++) {
                if (bitIndex >= connection.getSrcRegLowestBit() && bitIndex <= connection.getSrcRegHighestBit()) {
                    Net net = coreDesign.getNet(connection.getSrcReg().getName() + "_" + component.getName() + "/"
                            + RegisterDefaults.OUTPUT_NAME + "[" + i + "]");

                    routes.get(routeIndex).setRouteIndex(routeIndex);
                    footprint.addRoute(routes.get(routeIndex), net);
                    routeIndex += 1;
                }
            }
        }

        return footprint;
    }

    public static void initializeRouter(Design d, int threadPoolSize) {
        reset();
        coreDesign = d;
        executor = Executors.newFixedThreadPool(threadPoolSize);
    }

    public static void reset() {
        externalConnectionSet.clear();
        connectionSet.clear();
        uniqueConnectionsSet.clear();
        routesMap.clear();
        failedRoutes.clear();

        RouteForge.reset();
    }

    public static void prepareNewConnectionForRouting(RegisterConnection connection) {

        if (connection.isInputConnection() || connection.isOutputConnection()) {
            externalConnectionSet.add(connection);
            return;
        }

        connectionSet.add(connection);

        // Check for congruency
        boolean isCongruent = false;
        for (RegisterConnection c : uniqueConnectionsSet.keySet()) {
            if (c.isCongruentWith(coreDesign, connection)) {
                uniqueConnectionsSet.get(c).add(connection);
                isCongruent = true;
                break;
            }
        }

        if (!isCongruent)
            uniqueConnectionsSet.put(connection, new ArrayList<>());

    }

    public static void createNetsForConnections() {

        int inBitWidth = 0;
        int outBitWidth = 0;
        EDIFCell top = coreDesign.getNetlist().getTopCell();

        for (RegisterConnection connection : externalConnectionSet) {
            if (connection.isInputConnection()) {
                inBitWidth += connection.getBitWidth();
            }
            else if (connection.isOutputConnection()) {
                outBitWidth += connection.getBitWidth();
            }
        }
        EDIFPortInst[] srcPortRefs = EDIFTools.createPortInsts(top, "src", EDIFDirection.INPUT, inBitWidth);
        EDIFPortInst[] resPortRefs = EDIFTools.createPortInsts(top, "res", EDIFDirection.OUTPUT, outBitWidth);

        for (RegisterConnection connection : externalConnectionSet) {
            if (connection.isInputConnection()) {
                connection.getSnkReg().createInputEDIFPortRefs(coreDesign, "src", connection.getSnkRegLowestBit(),
                        connection.getSnkRegHighestBit(), connection.getSrcRegLowestBit());
            }
            else if (connection.isOutputConnection()) {
                connection.getSrcReg().createOutputEDIFPortRefs(coreDesign, "res", connection.getSrcRegLowestBit(),
                        connection.getSrcRegHighestBit(), connection.getSnkRegLowestBit());
            }
        }

        for (int i = 0; i < inBitWidth; i++) {
            EDIFNet srcNet = top.getNet("src[" + i + "]");
            srcNet.addPortInst(srcPortRefs[i]);
        }

        for (int i = 0; i < outBitWidth; i++) {
            EDIFNet resNet = top.getNet("res[" + i + "]");
            resNet.addPortInst(resPortRefs[i]);
        }

        int interIndex = 0;
        for (RegisterConnection connection : connectionSet) {
            connection.getSrcReg().createOutputEDIFPortRefs(coreDesign, "inter" + interIndex,
                    connection.getSrcRegLowestBit(), connection.getSrcRegHighestBit(), 0);
            connection.getSnkReg().createInputEDIFPortRefs(coreDesign, "inter" + interIndex,
                    connection.getSnkRegLowestBit(), connection.getSnkRegHighestBit(), 0);
            interIndex += 1;
        }
    }

    /*
     * Master function for routing the design
     */
    public static void routeDesign() throws Exception {
        long tBegin = System.currentTimeMillis();

        int numCloneableRoutes = 0;
        for (ArrayList<RegisterConnection> list : uniqueConnectionsSet.values())
            numCloneableRoutes += list.size();

        RouterLog.log("Performing route design.", RouterLog.Level.NORMAL);
        RouterLog.indent();
        RouterLog.log("Total routes: " + connectionSet.size(), RouterLog.Level.NORMAL);
        RouterLog.indent();
        RouterLog.log("Unique routes: " + uniqueConnectionsSet.size(), RouterLog.Level.NORMAL);
        RouterLog.log("Cloneable routes: " + numCloneableRoutes, RouterLog.Level.NORMAL);
        RouterLog.indent(-1);
        RouterLog.indent(-1);


        /*
         * Step 0: Lock down associated in/out PIP junctions of registers
         */
        long tStep0Begin = System.currentTimeMillis();
        RouterLog.log("0: Locking in/out PIPs of registers.", RouterLog.Level.NORMAL);
        RouterLog.indent();
        for (RegisterConnection connection : connectionSet) {
            for (RegisterComponent component : connection.getSrcReg().getComponents()) {
                String intTileName = coreDesign.getDevice().getSite(component.getSiteName()).getIntTile().getName();
                for (int i = 0; i < component.getBitWidth(); i++) {
                    RouteForge.lock(intTileName + "/" + component.getInPIPName(i));
                    RouteForge.lock(intTileName + "/" + component.getOutPIPName(i));
                }
            }
            for (RegisterComponent component : connection.getSnkReg().getComponents()) {
                String intTileName = coreDesign.getDevice().getSite(component.getSiteName()).getIntTile().getName();
                for (int i = 0; i < component.getBitWidth(); i++) {
                    RouteForge.lock(intTileName + "/" + component.getInPIPName(i));
                    RouteForge.lock(intTileName + "/" + component.getOutPIPName(i));
                }
            }
        }

        RouterLog.log("All PIPs locked in "  + (System.currentTimeMillis() - tStep0Begin) + " ms.",
                RouterLog.Level.NORMAL);
        RouterLog.indent(-1);


        /*
         * Step 1: Route all unique routes on separate threads (max jobs limited)
         */
        long tStep1Begin = System.currentTimeMillis();
        RouterLog.log("1: Routing unique routes.", RouterLog.Level.NORMAL);
        RouterLog.indent();

        HashMap<RegisterConnection, Future<ArrayList<CustomRoute>>> routingJobResults = new HashMap<>();
        for (RegisterConnection connection : uniqueConnectionsSet.keySet()) {
            BusRoutingJob job = new BusRoutingJob(coreDesign, connection);
            routingJobResults.put(connection, executor.submit(job));
        }

        for (RegisterConnection connection : routingJobResults.keySet()) {
            try {
                ArrayList<CustomRoute> busResults = routingJobResults.get(connection).get();
                RouteFootprint footprint = compileFootprint(connection, busResults);
                routesMap.put(connection, footprint);
            } catch (Exception e) {
                e.printStackTrace();
                throw e;
            }
        }

        RouterLog.log("All unique routes routed in " + (System.currentTimeMillis() - tStep1Begin) + " ms.",
                RouterLog.Level.NORMAL);
        RouterLog.indent(-1);


        /*
         * Step 2: Copy unique routes with x/y offset for all cloneable routes
         */
        long tStep2Begin = System.currentTimeMillis();
        RouterLog.log("2: Copying cloneable routes.", RouterLog.Level.NORMAL);
        RouterLog.indent();
        for (RegisterConnection ref : uniqueConnectionsSet.keySet()) {
            for (RegisterConnection copy : uniqueConnectionsSet.get(ref)) {
                routesMap.put(copy, copyFootprintWithOffset(copy, routesMap.get(ref),
                        getXOffsetOfCongruentConnection(ref, copy),
                        getYOffsetOfCongruentConnection(ref, copy)));
            }
        }

        RouterLog.log("All cloneable routes copied in " + (System.currentTimeMillis() - tStep2Begin) + " ms.",
                RouterLog.Level.NORMAL);
        RouterLog.indent(-1);


        /*
         * Step 3: Reroute routes with template conflicts (i.e. hop wires conflicts)
         *  Routes are routed synchronously
         */
        long tStep3Begin = System.currentTimeMillis();
        RouterLog.log("3: Rerouting conflicting routes.", RouterLog.Level.NORMAL);
        RouterLog.indent();

        int rerouteCount = 0;
        for (RegisterConnection connection : routesMap.keySet()) {
            RouteFootprint footprint = routesMap.get(connection);
            Set<CustomRoute> badRoutes = new HashSet<>();

            boolean isConflicted = false;
            for (CustomRoute route : footprint.getRoutes()) {
                for (WireJunction hopJunction : route.getTemplate().getTemplate()) {
                    if (RouteForge.isOccupied(hopJunction.getNodeName())) {
                        isConflicted = true;
                        break;
                    }
                }

                if (isConflicted) {
                    badRoutes.add(route);
                }
                else {
                    for (WireJunction hopJunction : route.getTemplate().getTemplate()) {
                        RouteForge.occupy(hopJunction.getNodeName());
                    }
                }
            }

            rerouteCount += badRoutes.size();
            for (CustomRoute badRoute : badRoutes) {
                SignalRoutingJob job = new SignalRoutingJob(coreDesign, badRoute.getSrc(),
                        (ExitWireJunction) badRoute.getTemplate().getTemplate(-3));
                job.run();

                badRoute.replaceRoute(job.getRoute().getSrc(), job.getRoute().getSnk(), job.getRoute());

                for (WireJunction hopJunction : badRoute.getTemplate().getTemplate())
                    RouteForge.occupy(hopJunction.getNodeName());
            }
        }

        RouterLog.log(rerouteCount + " conflicted routes rerouted in " + (System.currentTimeMillis() - tStep3Begin) + " ms.",
                RouterLog.Level.NORMAL);
        RouterLog.indent(-1);


        /*
         * Step 4: Find and correct congested tiles
         */
        long tStep4Begin = System.currentTimeMillis();
        RouterLog.log("4: Resolving conflicts in congested tiles.", RouterLog.Level.NORMAL);
        RouterLog.indent();
        HashMap<String, RoutingCalculator.TilePathUsageBundle> tileUsageMap = new HashMap<>();
        HashMap<String, Set<Triple<RegisterConnection, CustomRoute, TilePath>>> congestedTileMap = new HashMap<>();

        for (RegisterConnection connection : routesMap.keySet()) {
            RouteFootprint footprint = routesMap.get(connection);

            for (CustomRoute route : footprint.getRoutes()) {
                for (TilePath path : route.getRoute()) {
                    if (!tileUsageMap.containsKey(path.getTileName())) {
                        tileUsageMap.put(path.getTileName(),
                                new RoutingCalculator.TilePathUsageBundle(path.getTileName()));
                    }

                    tileUsageMap.get(path.getTileName()).addTilePath(connection, route, path);
                }
            }
        }

        for (String tileName : tileUsageMap.keySet()) {
            if (tileUsageMap.get(tileName).isConfliced())
                congestedTileMap.put(tileName, tileUsageMap.get(tileName).getRouteSet());
        }

        RouterLog.log(congestedTileMap.size() + " congested tiles found.", RouterLog.Level.NORMAL);

        Set<Future<Set<Pair<RegisterConnection, CustomRoute>>>> congestionJobResults = new HashSet<>();
        for (String tileName : congestedTileMap.keySet()) {
            TileCongestionJob job = new TileCongestionJob(coreDesign, congestedTileMap.get(tileName));
            congestionJobResults.add(executor.submit(job));
        }

        for (Future<Set<Pair<RegisterConnection, CustomRoute>>> future : congestionJobResults) {
            failedRoutes.addAll(future.get());
        }

        RouterLog.log("All tile congestions resolved in " + (System.currentTimeMillis() - tStep4Begin) + " ms.",
                RouterLog.Level.NORMAL);
        RouterLog.log("A total of " + failedRoutes.size() + " routes will be forcibly rerouted due to congestion.",
                RouterLog.Level.NORMAL);
        RouterLog.indent(-1);


        /*
         * Step 5: Committing clean routes
         */
        long tStep5Begin = System.currentTimeMillis();
        RouterLog.log("5: Committing clean routes.", RouterLog.Level.NORMAL);
        RouterLog.indent();

        Set<RegisterConnection> failedConnections = new HashSet<>();
        Set<CustomRoute> failedSignals = new HashSet<>();
        HashMap<CustomRoute, Net> signalNetMap = new HashMap<>();
        for (Pair<RegisterConnection, CustomRoute> failure : failedRoutes) {
            failedConnections.add(failure.getLeft());
            failedSignals.add(failure.getRight());
        }


        for (RegisterConnection connection : routesMap.keySet()) {
            if (failedConnections.contains(connection)) {
                for (CustomRoute failedSignal : failedSignals) {
                    if (routesMap.get(connection).getRoutes().contains(failedSignal)) {
                        signalNetMap.put(failedSignal, routesMap.get(connection).getCorrespondingNet(failedSignal));
                        routesMap.get(connection).removeRoute(failedSignal);
                    }
                }
            }

            routesMap.get(connection).commit(coreDesign);
        }

        RouterLog.log("All clean routes committed in " + (System.currentTimeMillis() - tStep5Begin) + " ms.",
                RouterLog.Level.NORMAL);
        RouterLog.indent(-1);


        /*
         * Step 6: Reroute routes where congestions could not be resolved
         *  Routing is done serially
         */
        long tStep6Begin = System.currentTimeMillis();
        RouterLog.log("6: Rerouting conflicted routes.", RouterLog.Level.NORMAL);
        RouterLog.indent();
        for (Pair<RegisterConnection, CustomRoute> failure : failedRoutes) {
            SignalRoutingJob job = new SignalRoutingJob(coreDesign, failure.getRight().getSrc(),
                    failure.getRight().getSnk());
            job.run();

            job.getRoute().commitToNet(coreDesign, signalNetMap.get(failure.getRight()));
        }
        RouterLog.log("All conflicting routes rerouted in " + (System.currentTimeMillis() - tStep6Begin) + " ms.",
                RouterLog.Level.NORMAL);
        RouterLog.indent(-1);


        RouterLog.log("Route design completed in " + (System.currentTimeMillis() - tBegin) + " ms.",
                RouterLog.Level.NORMAL);

        executor.shutdown();

    }
}
