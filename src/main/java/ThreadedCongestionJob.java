import com.xilinx.rapidwright.design.Design;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.*;

public class ThreadedCongestionJob extends Thread {

    private static final int SINK_TILE_TRAVERSAL_MAX_DEPTH = 8;
    private static final int TILE_TRAVERSAL_MAX_DEPTH = FabricBrowser.TILE_TRAVERSAL_MAX_DEPTH;

    private long tBegin;
    private long tEnd;

    private Design coreDesign;
    private int jobID;

    private RouterLog.BufferedLog bufferedLog;

    private Set<Triple<RegisterConnection, CustomRoute, TilePath>> tilePaths;
    private ArrayList<Pair<CustomRoute, LinkedList<TilePath>>> allTilePathChoices;
    private HashMap<RegisterConnection, ArrayList<Pair<CustomRoute, LinkedList<TilePath>>>> tilePathChoicesMap;

    private HashMap<CustomRoute, TilePath> successfulConnections;
    private ArrayList<RegisterConnection> failedConnections;

    public ThreadedCongestionJob(Design d, int jobID, Set<Triple<RegisterConnection, CustomRoute, TilePath>> tilePaths) {
        super();

        coreDesign = d;
        this.jobID = jobID;

        bufferedLog = RouterLog.newBufferedLog();

        this.tilePaths = tilePaths;
        allTilePathChoices = new ArrayList<>();
        tilePathChoicesMap = new HashMap<>();

        successfulConnections = new HashMap<>();
        failedConnections = new ArrayList<>();
    }

    private void beginTiming() {
        tBegin = System.currentTimeMillis();
    }

    private void finishTiming() {
        tEnd = System.currentTimeMillis();
    }

    private long getElapsedTime() {
        return tEnd - tBegin;
    }

    public HashMap<CustomRoute, TilePath> getSuccessfulConnections() {
        return successfulConnections;
    }

    public ArrayList<RegisterConnection> getFailedTilePaths() {
        return failedConnections;
    }

    @Override
    public void run() {
        beginTiming();

        for (Triple<RegisterConnection, CustomRoute, TilePath> triple : tilePaths) {
            if (!tilePathChoicesMap.containsKey(triple.getLeft()))
                tilePathChoicesMap.put(triple.getLeft(), new ArrayList<>());

            TilePath path = triple.getRight();
            LinkedList<TilePath> pathChoices;

            if (path.getExitJunction().isSnk())
                pathChoices = new LinkedList<>(FabricBrowser.findTilePaths(coreDesign, SINK_TILE_TRAVERSAL_MAX_DEPTH,
                        path.getEnterJunction(), path.getExitJunction()));
            else
                pathChoices = new LinkedList<>(FabricBrowser.findTilePaths(coreDesign, path.getEnterJunction(),
                        path.getExitJunction()));

            tilePathChoicesMap.get(triple.getLeft()).add(new ImmutablePair<>(triple.getMiddle(), pathChoices));
            allTilePathChoices.add(new ImmutablePair<>(triple.getMiddle(), pathChoices));
        }

        /*
        for (RegisterConnection connection : tilePathChoicesMap.keySet()) {
            bufferedLog.log(connection.toString(), RouterLog.Level.NORMAL);
            bufferedLog.indent();
            for (LinkedList<TilePath> pathChoices : tilePathChoicesMap.get(connection)) {
                bufferedLog.log(pathChoices.get(0).shortHand() + ": " + pathChoices.size(), RouterLog.Level.NORMAL);
            }
            bufferedLog.indent(-1);
        }
        */

        boolean impossibleCongestion = true;
        for (Pair<CustomRoute, LinkedList<TilePath>> pathChoices : allTilePathChoices) {
            if (pathChoices.getRight().size() != 1)
                impossibleCongestion = false;
        }

        // No way out of current congestion
        if (impossibleCongestion) {
            // TODO
            System.out.println("Dont know what to do here yet.");
        }


        boolean congestionSuccess = false;
        while (!congestionSuccess) {

            congestionSuccess = true;

            int liveLockCount = 0;
            int[] deflectionCount = new int[allTilePathChoices.size()];

            Queue<Pair<RegisterConnection, Integer>> pathQueue = new LinkedList<>();
            HashMap<TilePath, Pair<RegisterConnection, Integer>> candidateToConnectionMap = new HashMap<>();

            for (RegisterConnection connection : tilePathChoicesMap.keySet()) {
                for (int i = 0; i < tilePathChoicesMap.get(connection).size(); i++)
                    pathQueue.add(new ImmutablePair<>(connection, i));
            }

            while (!pathQueue.isEmpty() && liveLockCount < 9999) {
                Pair<RegisterConnection, Integer> next = pathQueue.remove();
                liveLockCount += 1;

                RegisterConnection connection = next.getLeft();
                int index = next.getRight();

                TilePath candidatePath = tilePathChoicesMap.get(connection).get(index).getRight().getFirst();
                tilePathChoicesMap.get(connection).get(index).getRight()
                        .addLast(tilePathChoicesMap.get(connection).get(index).getRight().removeFirst());

                for (TilePath conflictingPath : RoutingCalculator.locateTilePathCollisions(candidatePath,
                        candidateToConnectionMap.keySet())) {
                    // Always preempt conflicts

                    pathQueue.add(candidateToConnectionMap.get(conflictingPath));
                    candidateToConnectionMap.remove(conflictingPath);

                    deflectionCount[allTilePathChoices.indexOf(tilePathChoicesMap.get(connection).get(index))] += 1;
                }

                candidateToConnectionMap.put(candidatePath, next);
            }

            if (liveLockCount >= 9999) {

                congestionSuccess = false;

                // Remove conflicting route: most likely the one with the highest deflection count
                int index = 0;
                int highestDeflection = 0;
                for (int i = 0; i < deflectionCount.length; i++) {
                    if (highestDeflection < deflectionCount[i]) {
                        index = i;
                        highestDeflection = deflectionCount[i];
                    }
                }

                // Strip bad connections for rerouting in the future - fix congestion of rest of tile paths for now
                RegisterConnection failedConnection = null;
                for (RegisterConnection connection : tilePathChoicesMap.keySet()) {
                    if (tilePathChoicesMap.get(connection).contains(allTilePathChoices.get(index))) {
                        failedConnection = connection;
                        break;
                    }
                }

                for (Pair<CustomRoute, LinkedList<TilePath>> pathChoices : tilePathChoicesMap.get(failedConnection))
                    allTilePathChoices.remove(pathChoices);
                tilePathChoicesMap.remove(failedConnection);

                failedConnections.add(failedConnection);
            }
            else {
                for (TilePath successfulPath : candidateToConnectionMap.keySet()) {
                    Pair<RegisterConnection, Integer> pair = candidateToConnectionMap.get(successfulPath);

                    tilePathChoicesMap.get(pair.getLeft()).get(pair.getRight()).getLeft().setPath(successfulPath);
                }
            }
        }

        DesignRouter.completeTileConflictJob(jobID, failedConnections);

        //bufferedLog.dumpLog();

        finishTiming();
    }
}
