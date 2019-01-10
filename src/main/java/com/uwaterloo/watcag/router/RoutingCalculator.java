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
}
