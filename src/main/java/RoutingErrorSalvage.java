import java.util.ArrayList;

public class RoutingErrorSalvage {

    /*
     * Contains error information for routeContention and deriveBestSinkPaths so we can reroute problematic bits
     */

    public static class routeContentionLiveLockReport {
        public static int[] deflectionCount;
        public static ArrayList<String> congestedNodeNames = new ArrayList<>();;

        public static void report(int[] deflections, ArrayList<CustomRoute> routes) {
            // Snipe the route that has the most deflections (preempted other routes the most)

            deflectionCount = deflections;

            int max = 0;
            int maxIndex = 0;
            for (int i = 0; i < deflectionCount.length; i++) {
                if (deflectionCount[i] > max) {
                    max = deflectionCount[i];
                    maxIndex = i;
                }
            }

            congestedNodeNames.add(routes.get(maxIndex).getTemplate().getTemplate(-2).getNodeName());
        }

        public static void actOnReport() {
            for (String nodeName : congestedNodeNames)
                CustomRouter.lock(nodeName);
        }
    }

    public static class deriveBestSinkPathsDeadlockReport {
        public static ArrayList<String> congestedNodeNames = new ArrayList<>();;

        public static void report(ArrayList<CustomRoute> routes) {
            // Snipe the cheapest route

            int minCost = 9999;
            int minIndex = 0;
            for (int i = 0; i < routes.size(); i++) {
                if (routes.get(i).getTemplate().getCost() < minCost) {
                    minCost = routes.get(i).getTemplate().getCost();
                    minIndex = i;
                }
            }

            congestedNodeNames.add(routes.get(minIndex).getTemplate().getTemplate(-2).getNodeName());
        }

        public static void actOnReport() {
            for (String nodeName : congestedNodeNames)
                CustomRouter.lock(nodeName);
        }
    }
}
