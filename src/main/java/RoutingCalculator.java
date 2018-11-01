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
     * Finds, out of a family of RouteTemplate's, which RouteTemplate is colliding with the family
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
}
