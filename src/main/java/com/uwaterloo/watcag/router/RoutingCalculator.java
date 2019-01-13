package com.uwaterloo.watcag.router;

import com.uwaterloo.watcag.common.RegisterConnection;
import com.uwaterloo.watcag.router.browser.JunctionsTracer;
import com.uwaterloo.watcag.router.elements.*;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;

import java.util.*;

public class RoutingCalculator {

    /*
     * Collection of verbose functions for utility in routing
     */

    public static class TilePathUsageBundle {
        /*
         * Internal class for tracking congested tiles
         */

        private boolean isConfliced;

        private String tileName;
        private Set<Triple<RegisterConnection, CustomRoute, TilePath>> routeSet;
        private Set<String> nodeUsage;

        public TilePathUsageBundle(String tileName) {
            this.tileName = tileName;
            routeSet = new HashSet<>();
            nodeUsage = new HashSet<>();
        }

        public boolean isConfliced() {
            return isConfliced;
        }

        public Set<Triple<RegisterConnection, CustomRoute, TilePath>> getRouteSet() {
            return routeSet;
        }

        public void addTilePath(RegisterConnection connection, CustomRoute route, TilePath path) {
            routeSet.add(new ImmutableTriple<>(connection, route, path));

            for (String nodeName : path.getNodePath()) {
                if (nodeUsage.contains(nodeName)) {
                    isConfliced = true;
                    break;
                }
            }
            nodeUsage.addAll(path.getNodePath());
        }

    }

    public static class JunctionsTracerCostComparator implements Comparator<JunctionsTracer> {

        @Override
        public int compare(JunctionsTracer o1, JunctionsTracer o2) {
            return o1.getEstimatedCost() - o2.getEstimatedCost();
        }
    }

    public static Set<TilePath> locateTilePathCollisions(TilePath candidatePath, Set<TilePath> paths) {
        Set<TilePath> results = new HashSet<>();

        Set<String> nodes = new HashSet<>(candidatePath.getNodePath());

        for (TilePath path : paths) {
            for (String nodeName : path.getNodePath()) {
                if (nodes.contains(nodeName)) {
                    results.add(path);
                    break;
                }
            }
        }

        return results;
    }

    private static ArrayList<TilePath> deriveValidTilePathsRecurse(int depth, ArrayList<TilePath> validPathsState,
                                                            HashSet<String> tilePathFootprint,
                                                            ArrayList<HashSet<TilePath>> allPaths) {
        if (depth == allPaths.size())
            return validPathsState;

        HashSet<TilePath> paths = allPaths.get(depth);
        if (paths == null || paths.isEmpty())
            return null;

        for (TilePath candidate : paths) {
            boolean isValid = true;
            for (String nodeName : candidate.getNodePath()) {
                if (tilePathFootprint.contains(nodeName)) {
                    isValid = false;
                    break;
                }
            }

            if (!isValid)
                continue;

            HashSet<String> nextDepthTilePathFootprint = new HashSet<>(tilePathFootprint);
            nextDepthTilePathFootprint.addAll(candidate.getNodePath());

            validPathsState.set(depth, candidate);

            ArrayList<TilePath> results = deriveValidTilePathsRecurse(depth + 1, validPathsState,
                    nextDepthTilePathFootprint, allPaths);
            if (results != null)
                return results;
        }

        return null;
    }

    public static ArrayList<TilePath> deriveValidTilePaths(ArrayList<HashSet<TilePath>> allPaths) {
        ArrayList<HashSet<String>> exclusives = new ArrayList<>();

        int bitWidth = allPaths.size();

        for (int i = 0; i < bitWidth; i++) {
            exclusives.add(new HashSet<>());

            HashMap<String, Integer> usageCountMap = new HashMap<>();
            HashSet<TilePath> pathChoices = allPaths.get(i);

            for (TilePath pathChoice : pathChoices) {
                for (int j = 0; j < pathChoice.getNodePath().size(); j++) {
                    if (!usageCountMap.containsKey(pathChoice.getNodeName(j)))
                        usageCountMap.put(pathChoice.getNodeName(j), 1);
                    else
                        usageCountMap.put(pathChoice.getNodeName(j), usageCountMap.get(pathChoice.getNodeName(j)) + 1);
                }
            }

            for (String nodeName : usageCountMap.keySet()) {
                if (usageCountMap.get(nodeName) == pathChoices.size())
                    exclusives.get(i).add(nodeName);
            }
        }

        HashSet<String> allExclusives = new HashSet<>();
        for (int i = 0; i < bitWidth; i++) {
            for (String exclusiveNode : exclusives.get(i)) {
                if (allExclusives.contains(exclusiveNode))
                    return null;
                else
                    allExclusives.add(exclusiveNode);
            }
        }

        ArrayList<TilePath> results = new ArrayList<>();
        for (int i = 0; i < bitWidth; i++)
            results.add(null);
        return deriveValidTilePathsRecurse(0, results, new HashSet<>(), allPaths);
    }

    public static ArrayList<TilePath> deriveBestTilePathConfiguration(ArrayList<ArrayList<TilePath>> allPaths) {
        int bitWidth = allPaths.size();

        // Highest cost possible
        int threshMax = 0;
        // Lost cost possible (max of min's across each bit)
        int threshMin = 0;

        for (int i = 0; i < bitWidth; i++) {
            ArrayList<TilePath> pathChoices = allPaths.get(i);
            int min = 999;
            for (TilePath pathChoice : pathChoices) {
                if (pathChoice.getCost() > threshMax)
                    threshMax = pathChoice.getCost();
                if (pathChoice.getCost() < min)
                    min = pathChoice.getCost();
            }

            if (min > threshMin)
                threshMin = min;
        }

        ArrayList<HashSet<TilePath>> candidatePool = new ArrayList<>();
        for (int i = 0; i < bitWidth; i++) {
            candidatePool.add(new HashSet<>());
            for (TilePath path : allPaths.get(i)) {
                if (path.getCost() < threshMin)
                    candidatePool.get(i).add(path);
            }
        }

        for (int threshold = threshMin; threshold <= threshMax; threshold++) {
            ArrayList<HashSet<TilePath>> newCandidates = new ArrayList<>();
            for (int i = 0; i < bitWidth; i++)
                newCandidates.add(new HashSet<>());

            int additionsToCandidatePool = 0;
            for (int i = 0; i < bitWidth; i++) {
                ArrayList<TilePath> candidates = allPaths.get(i);
                for (TilePath candidate : candidates) {
                    if (candidate.getCost() == threshold) {
                        newCandidates.get(i).add(candidate);
                        additionsToCandidatePool += 1;
                    }
                }
                candidatePool.get(i).addAll(newCandidates.get(i));
            }

            // If nothing new was added to the candidate pool this round, simply move on to the next threshold
            if (additionsToCandidatePool == 0)
                continue;

            for (int i = 0; i < bitWidth; i++) {
                if (newCandidates.get(i).isEmpty())
                    continue;

                ArrayList<HashSet<TilePath>> candidates = new ArrayList<>(candidatePool);
                candidates.set(i, newCandidates.get(i));

                ArrayList<TilePath> results = new ArrayList<>();
                for (int j = 0; j < bitWidth; j++)
                    results.add(null);

                results = deriveValidTilePathsRecurse(0, results, new HashSet<>(), candidates);

                if (results != null) {
                    return new ArrayList<>(results);
                }
            }
        }

        return null;
    }
}
