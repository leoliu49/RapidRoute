import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.device.Tile;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class RoutingCalculator {

    /*
     * Collection of verbose functions for utility in routing
     */

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
     * Finds, out of a family of RouteTemplates, which RouteTemplate is colliding with the candidate
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
