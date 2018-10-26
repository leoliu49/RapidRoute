import java.util.ArrayList;

public class RoutingErrorSalvage {

    /*
     * Contains error information for routeContention so we can reroute problematic bits
     */

    public static class routeContentionLiveLockReport {
        public static int[] deflectionCount;
        public static ArrayList<String> congestedNodeNames;

        public static void report(int[] deflections, ArrayList<CustomRoute> routes) {
            deflectionCount = deflections;

            int max = 0;
            int maxIndex = 0;
            for (int i = 0; i < deflectionCount.length; i++) {
                if (deflectionCount[i] > max) {
                    max = deflectionCount[i];
                    maxIndex = i;
                }
            }

            congestedNodeNames = new ArrayList<>();
            congestedNodeNames.add(routes.get(maxIndex).getTemplate().getTemplate(-2).getNodeName());
        }

        public static void actOnReport() {
            for (String nodeName : congestedNodeNames)
                CustomRouter.lock(nodeName);
        }
    }
}
