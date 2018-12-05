import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.device.Tile;

import java.util.*;
import java.util.concurrent.Semaphore;

public class DesignRouter {

    /*
     * Top-level router which router the entire design
     */

    private static int THREAD_POOL_SIZE = 4;

    private static Design coreDesign;

    private static final HashSet<RegisterConnection> connectionSet = new LinkedHashSet<>();
    private static final HashMap<RegisterConnection, ArrayList<RegisterConnection>> uniqueConnectionsSet = new LinkedHashMap<>();
    private static final HashMap<RegisterConnection, RoutingFootprint> routesMap = new HashMap<>();

    private static final ArrayList<Thread> threadPool = new ArrayList<>();
    private static final LinkedList<Integer> freeThreads = new LinkedList<>();

    private static final Queue<RegisterConnection> routingQueue = new LinkedList<>();

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
        RoutingFootprint footprint = new RoutingFootprint();

        int bitIndex = 0;
        int routeIndex = 0;

        ComplexRegister srcReg = connection.getSrcReg();
        for (RegisterComponent component : srcReg.getComponents()) {
            for (int i = 0; i < component.getBitWidth(); i++, bitIndex++) {
                if (bitIndex >= connection.getSrcRegLowestBit() && bitIndex <= connection.getSrcRegHighestBit()) {
                    Net net = coreDesign.getNet(srcReg.getName() + "_" + component.getName() + "/"
                            + ComplexRegister.OUTPUT_NAME + "[" + i + "]");

                    CustomRoute routeCopy = ref.getRoute(routeIndex).copyWithOffset(coreDesign, dx, dy);
                    routeCopy.setBitIndex(bitIndex);

                    footprint.add(routeCopy, net);

                    routeIndex += 1;
                }
            }
        }

        return footprint;
    }

    private static void scheduleNewJob(int jobID, ThreadedRoutingJob job) throws InterruptedException {
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
        coreDesign = d;

        for (int i = 0; i < THREAD_POOL_SIZE; i++)
            threadPool.add(null);

        THREAD_POOL_SIZE = threadPoolSize;
    }

    public static void initNewConnectionForRouting(Design d, RegisterConnection connection) {

        connectionSet.add(connection);

        // Check for congruency
        boolean isCongruent = false;
        for (RegisterConnection c : uniqueConnectionsSet.keySet()) {
            if (c.isCongruentWith(d, connection)) {
                uniqueConnectionsSet.get(c).add(connection);
                isCongruent = true;
                break;
            }
        }

        if (!isCongruent)
            uniqueConnectionsSet.put(connection, new ArrayList<>());

    }

    public static void completeRoutingJob(int jobID, RegisterConnection connection, RoutingFootprint footprint) {
        synchronized (routesMap) {
            routesMap.put(connection, footprint);
        }
        synchronized (freeThreads) {
            if (!freeThreads.contains(jobID))
                freeThreads.add(jobID);
        }
    }

    /*
     * Master function for routing the design
     */
    public static void routeDesign() {
        long tBegin = System.currentTimeMillis();

        int numCloneableRoutes = 0;
        for (ArrayList<RegisterConnection> list : uniqueConnectionsSet.values())
            numCloneableRoutes += list.size();

        RouterLog.log("Initiating route design.", RouterLog.Level.NORMAL);
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

        for (int i = 0; i < THREAD_POOL_SIZE; i++)
            freeThreads.add(i);
        routingQueue.addAll(uniqueConnectionsSet.keySet());

        while (!routingQueue.isEmpty()) {
            try {
                int freeThreadID = waitForFreeThread();
                scheduleNewJob(freeThreadID, new ThreadedRoutingJob(coreDesign, freeThreadID, routingQueue.remove()));
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        for (Thread job : threadPool) {
            if (job != null) {
                try {
                    job.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
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

    }
}
