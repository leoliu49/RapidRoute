package com.uwaterloo.watcag.router;

import com.uwaterloo.watcag.common.RegisterConnection;
import com.uwaterloo.watcag.router.*;
import com.uwaterloo.watcag.router.elements.*;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
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

    public static class TemplateCostComparator implements Comparator<RouteTemplate> {

        @Override
        public int compare(RouteTemplate r1, RouteTemplate r2) {
            return r1.getAdjustedCost() - r2.getAdjustedCost();
        }
    }

    /*
     * Checks to see if the family of RouteTemplates is valid
     */
    public static boolean isTemplateConfigurationValid(ArrayList<RouteTemplate> templates) {
        HashSet<String> nodeUsages = new HashSet<>();
        for (RouteTemplate template : templates) {
            for (WireJunction junction : template.getTemplate()) {
                if (nodeUsages.contains(junction.getNodeName()))
                    return false;
                nodeUsages.add(junction.getNodeName());
            }
        }

        return true;
    }

    /*
     * Finds, out of a family of RouteTemplates, which com.uwaterloo.watcag.router.elements.RouteTemplate is colliding with the candidate
     * Returns indexes of collisions
     */
    public static ArrayList<Integer> locateTemplateCollisions(Set<String> candidateSet,
                                                               HashMap<Integer, Set<String>> usages) {
        ArrayList<Integer> results = new ArrayList<>();

        for (int key : usages.keySet()) {
            for (String nodeName : candidateSet) {
                if (usages.get(key).contains(nodeName)) {
                    results.add(key);
                    break;
                }
            }
        }
        return results;
    }

    public static ArrayList<Pair<Integer, Integer>> locateTilePathCollisions(TilePath candidatePath,
                                                                              ArrayList<CustomRoute> routes) {
        ArrayList<Pair<Integer, Integer>> results = new ArrayList<>();

        Set<String> nodes = new HashSet<>(candidatePath.getNodePath());

        for (int i = 0; i < routes.size(); i++) {
            CustomRoute route = routes.get(i);
            for (int j = 0; j < route.getRoute().size(); j++) {
                TilePath path = route.getRoute().get(j);
                if (path == null)
                    continue;
                for (String nodeName : path.getNodePath()) {
                    if (nodes.contains(nodeName)) {
                        results.add(new ImmutablePair<>(i, j));
                        break;
                    }
                }
            }
        }


        return results;
    }

    public static Set<TilePath> locateTilePathCollisions(TilePath candidatePath, Set<TilePath> paths) {
        Set<TilePath> results = new HashSet<>();

        Set<String> nodes = new HashSet<>(candidatePath.getNodePath());

        for (TilePath path : paths) {
            for (String candidateNodeName : candidatePath.getNodePath()) {
                if (path.getNodePath().contains(candidateNodeName)) {
                    results.add(path);
                    break;
                }
            }
        }

        return results;
    }

    public static RouteTemplate findTemplateWithSinkTilePath(TilePath path, ArrayList<RouteTemplate> templates) {
        for (int i = 0; i < templates.size(); i++) {
            if (templates.get(i).getTemplate(-2).equals(path.getEnterJunction())) {
                return templates.get(i);
            }
        }
        return null;
    }

    public static boolean isRoutingFootprintConflicted(RoutingFootprint footprint) {
        for (CustomRoute route : footprint.getRoutes()) {
            for (TilePath path : route.getRoute()) {
                for (String node : path.getNodePath()) {
                    if (RouteForge.isOccupied(node))
                        return true;
                }
            }
        }

        return false;
    }
}
