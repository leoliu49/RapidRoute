package com.uwaterloo.watcag.router;

import com.uwaterloo.watcag.common.ComplexRegister;
import com.uwaterloo.watcag.common.RegisterConnection;
import com.uwaterloo.watcag.config.RegisterComponent;
import com.uwaterloo.watcag.config.RegisterDefaults;
import com.uwaterloo.watcag.router.elements.CustomRoute;
import com.uwaterloo.watcag.router.elements.RoutingFootprint;
import com.uwaterloo.watcag.router.elements.TilePath;
import com.uwaterloo.watcag.router.elements.WireJunction;
import com.uwaterloo.watcag.util.RouterLog;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.device.Tile;
import com.xilinx.rapidwright.edif.*;
import org.apache.commons.lang3.tuple.Triple;

import java.util.*;

public class DesignRouter {

    /*
     * Top-level router which router the entire design
     */

    private static int THREAD_POOL_SIZE = 4;

    private static Design coreDesign;

    private static final HashSet<RegisterConnection> externalConnectionSet = new HashSet<>();

    private static final HashSet<RegisterConnection> connectionSet = new LinkedHashSet<>();
    private static final HashMap<RegisterConnection, ArrayList<RegisterConnection>> uniqueConnectionsSet = new LinkedHashMap<>();
    private static final HashMap<RegisterConnection, RoutingFootprint> routesMap = new HashMap<>();

    private static final ArrayList<Thread> threadPool = new ArrayList<>();
    private static final LinkedList<Integer> freeThreads = new LinkedList<>();

    private static final Queue<RegisterConnection> routingQueue = new LinkedList<>();
    private static final Queue<Set<Triple<RegisterConnection, CustomRoute, TilePath>>> tileConflictQueue = new LinkedList<>();

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

    private static RoutingFootprint copyFootprintWithOffset(RegisterConnection connection, RoutingFootprint ref, int dx,
                                                            int dy) {
        RoutingFootprint footprint = new RoutingFootprint(connection);

        int bitIndex = 0;
        int routeIndex = 0;

        ComplexRegister srcReg = connection.getSrcReg();
        for (RegisterComponent component : srcReg.getComponents()) {
            for (int i = 0; i < component.getBitWidth(); i++, bitIndex++) {
                if (bitIndex >= connection.getSrcRegLowestBit() && bitIndex <= connection.getSrcRegHighestBit()) {
                    Net net = coreDesign.getNet(srcReg.getName() + "_" + component.getName() + "/"
                            + RegisterDefaults.OUTPUT_NAME + "[" + i + "]");

                    CustomRoute routeCopy = ref.getRoute(routeIndex).copyWithOffset(coreDesign, dx, dy);
                    routeCopy.setBitIndex(bitIndex);

                    footprint.add(routeCopy, net);

                    routeIndex += 1;
                }
            }
        }

        return footprint;
    }

    private static void scheduleNewJob(int jobID, Thread job) {
        synchronized (threadPool) {
            threadPool.set(jobID, job);
        }

        job.start();
    }

    private static int waitForFreeThread() throws InterruptedException {
        int freeThreadID;
        while (true) {
            synchronized (freeThreads) {
                if (!freeThreads.isEmpty()) {
                    freeThreadID = freeThreads.remove();
                    break;
                }
            }
            Thread.sleep(500);
        }

        Thread completedThread;
        synchronized (threadPool) {
            completedThread = threadPool.get(freeThreadID);
            threadPool.set(freeThreadID, null);
        }

        if (completedThread != null)
            completedThread.join();
        return freeThreadID;
    }

    public static void initializeRouter(Design d, int threadPoolSize) {
        reset();
        coreDesign = d;

        for (int i = 0; i < THREAD_POOL_SIZE; i++)
            threadPool.add(null);
        for (int i = 0; i < THREAD_POOL_SIZE; i++)
            freeThreads.add(i);

        THREAD_POOL_SIZE = threadPoolSize;
    }

    public static void reset() {
        externalConnectionSet.clear();
        connectionSet.clear();
        uniqueConnectionsSet.clear();
        routesMap.clear();
        threadPool.clear();
        freeThreads.clear();
        routingQueue.clear();
        tileConflictQueue.clear();

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

    public static void completeRoutingJob(int jobID, RegisterConnection connection, RoutingFootprint footprint) {
        synchronized (routesMap) {
            routesMap.put(connection, footprint);
        }

        // -1 indicates running on main thread
        if (jobID != -1) {
            synchronized (freeThreads) {
                if (!freeThreads.contains(jobID))
                    freeThreads.add(jobID);
            }
        }
    }

    // TODO
    public static void completeTileConflictJob(int jobID, ArrayList<RegisterConnection> failures) {
        if (jobID != -1) {
            synchronized (freeThreads) {
                if (!freeThreads.contains(jobID))
                    freeThreads.add(jobID);

            }
        }

        for (RegisterConnection failure : failures) {
            routesMap.get(failure).clear();
            synchronized (routingQueue) {
                routingQueue.add(failure);
            }
            synchronized (routesMap) {
                if (routesMap.containsKey(failure))
                    routesMap.remove(failure);
            }
        }

    }

    /*
     * Called from main thread, acting as rendezvous point for all threads
     */
    public static void waitForAllThreads() {
        for (int i = 0; i < threadPool.size(); i++) {
            Thread job = threadPool.get(i);
            if (job != null) {
                try {
                    job.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

                threadPool.set(i, null);
            }
        }

        freeThreads.clear();
        for (int i = 0; i < THREAD_POOL_SIZE; i++)
            freeThreads.add(i);
    }

    /*
     * Master function for routing the design
     */
    public static void routeDesign() {
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
        RouterLog.log("Max jobs: " + THREAD_POOL_SIZE, RouterLog.Level.NORMAL);
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

        routingQueue.addAll(uniqueConnectionsSet.keySet());

        while (!routingQueue.isEmpty()) {
            try {
                int freeThreadID = waitForFreeThread();
                scheduleNewJob(freeThreadID, new ThreadedRoutingJob(coreDesign, freeThreadID, routingQueue.remove()));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        waitForAllThreads();

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
        for (RegisterConnection connection : routesMap.keySet()) {
            RoutingFootprint footprint = routesMap.get(connection);

            boolean isConflicted = false;
            for (CustomRoute route : footprint.getRoutes()) {
                for (WireJunction hopJunction : route.getTemplate().getTemplate()) {
                    if (RouteForge.isOccupied(hopJunction.getNodeName())) {
                        isConflicted = true;
                        break;
                    }
                }

                if (isConflicted) {
                    routingQueue.add(connection);
                    break;
                }
            }

            if (!isConflicted) {
                for (CustomRoute route : footprint.getRoutes()) {
                    for (WireJunction hopJunction : route.getTemplate().getTemplate())
                        RouteForge.occupy(hopJunction.getNodeName());
                }
            }
        }

        RouterLog.log(routingQueue.size() + " conflicted routes found.", RouterLog.Level.NORMAL);

        while (!routingQueue.isEmpty()) {
            RegisterConnection connection = routingQueue.remove();
            new ThreadedRoutingJob(coreDesign, -1, connection).run();
            for (CustomRoute route : routesMap.get(connection).getRoutes()) {
                for (WireJunction hopJunction : route.getTemplate().getTemplate())
                    RouteForge.occupy(hopJunction.getNodeName());
            }
        }

        RouterLog.log("All conflicting routes corrected in " + (System.currentTimeMillis() - tStep3Begin) + " ms.",
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

        for (RoutingFootprint footprint : routesMap.values()) {
            for (CustomRoute route : footprint.getRoutes()) {
                for (TilePath path : route.getRoute()) {
                    if (!tileUsageMap.containsKey(path.getTileName())) {
                        tileUsageMap.put(path.getTileName(),
                                new RoutingCalculator.TilePathUsageBundle(path.getTileName()));
                    }

                    tileUsageMap.get(path.getTileName()).addTilePath(footprint.getRegisterConnection(), route, path);
                }
            }
        }

        for (String tileName : tileUsageMap.keySet()) {
            if (tileUsageMap.get(tileName).isConfliced())
                congestedTileMap.put(tileName, tileUsageMap.get(tileName).getRouteSet());
        }

        RouterLog.log(congestedTileMap.size() + " congested tiles found.", RouterLog.Level.NORMAL);

        tileConflictQueue.addAll(congestedTileMap.values());
        while (!tileConflictQueue.isEmpty()) {
            try {
                int freeThreadID = waitForFreeThread();
                scheduleNewJob(freeThreadID,
                        new ThreadedCongestionJob(coreDesign, freeThreadID, tileConflictQueue.remove()));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        waitForAllThreads();

        RouterLog.log("All tile congestions resolved in " + (System.currentTimeMillis() - tStep4Begin) + " ms.",
                RouterLog.Level.NORMAL);
        RouterLog.log("A total of " + routingQueue.size() + " routes will be forcibly rerouted due to congestion.",
                RouterLog.Level.NORMAL);
        RouterLog.indent(-1);


        /*
         * Step 5: Committing existing routes
         */
        long tStep5Begin = System.currentTimeMillis();
        RouterLog.log("5: Committing clean routes.", RouterLog.Level.NORMAL);
        RouterLog.indent();
        for (RegisterConnection connection : routesMap.keySet()) {
            routesMap.get(connection).commit(coreDesign);
        }

        RouterLog.log("All routes committed in " + (System.currentTimeMillis() - tStep5Begin) + " ms.",
                RouterLog.Level.NORMAL);
        RouterLog.indent(-1);

        /*
         * Step 6: Reroute routes where congestions could not be resolved
         *  Routing is done in order
         */
        long tStep6Begin = System.currentTimeMillis();
        RouterLog.log("6: Rerouting conflicting routes.", RouterLog.Level.NORMAL);
        RouterLog.indent();
        while (!routingQueue.isEmpty()) {
            RegisterConnection connection = routingQueue.remove();
            new ThreadedRoutingJob(coreDesign, -1, connection).run();

            routesMap.get(connection).commit(coreDesign);
        }

        RouterLog.log("All conflicting routes rerouted in " + (System.currentTimeMillis() - tStep6Begin) + " ms.",
                RouterLog.Level.NORMAL);
        RouterLog.indent(-1);


        RouterLog.log("Route design completed in " + (System.currentTimeMillis() - tBegin) + " ms.",
                RouterLog.Level.NORMAL);

    }
}
