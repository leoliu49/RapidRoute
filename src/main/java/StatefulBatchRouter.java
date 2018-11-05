import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.device.Tile;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class StatefulBatchRouter {

    /*
     * Stateful router which has more advanced error correction / congestion relief techniques
     */

    private static final int SINK_TILE_TRAVERSAL_MAX_DEPTH = 8;
    private static final int TILE_TRAVERSAL_MAX_DEPTH = FabricBrowser.TILE_TRAVERSAL_MAX_DEPTH;
    //private static final int TEMPLATE_COST_TOLERANCE = 8;

    private long tBegin;
    private long tEnd;

    private RegisterConnection connection;
    private int bitwidth;
    private ComplexRegister srcReg, snkReg;

    public ArrayList<EnterWireJunction> srcJunctions;
    public ArrayList<ExitWireJunction> snkJunctions;


    private int cumulativeBatchSize;
    private ArrayList<Pair<Queue<JunctionsTracer>, HashSet<String>>> searchStates;
    private ArrayList<Set<EnterWireJunction>> snkTileEntrances;

    private ArrayList<ArrayList<RouteTemplate>> templateCandidatesCache;
    private ArrayList<HashMap<EnterWireJunction, ArrayList<TilePath>>> snkTilePathsCache;
    private ArrayList<ArrayList<RouteTemplate>> allTemplateCandidates;
    private ArrayList<CustomRoute> routes;

    private RoutingFootprint footprint;

    private StatefulBatchRouter(RegisterConnection connection) {
        this.connection = connection;
        bitwidth = connection.getBitWidth();
        srcReg = connection.getSrcReg();
        snkReg = connection.getSnkReg();

        cumulativeBatchSize = 0;

        srcJunctions = new ArrayList<>();
        snkJunctions = new ArrayList<>();
        searchStates = new ArrayList<>();
        snkTileEntrances = new ArrayList<>();
        templateCandidatesCache = new ArrayList<>();
        snkTilePathsCache = new ArrayList<>();
        allTemplateCandidates = new ArrayList<>();
        routes = new ArrayList<>();

        for (int i = 0; i < bitwidth; i++) {
            srcJunctions.add(null);
            snkJunctions.add(null);
            searchStates.add(null);
            snkTileEntrances.add(null);
            templateCandidatesCache.add(new ArrayList<>());
            snkTilePathsCache.add(new HashMap<>());
            allTemplateCandidates.add(null);
            routes.add(null);
        }

        footprint = new RoutingFootprint();
    }

    /* Accessors and modifiers */
    private Pair<Queue<JunctionsTracer>, HashSet<String>> getSearchState(int bitIndex) {
        if (searchStates.get(bitIndex) == null) {
            Queue<JunctionsTracer> queue = new LinkedList<>();
            queue.add(new JunctionsTracer(srcJunctions.get(bitIndex), 0));
            searchStates.set(bitIndex, new MutablePair<>(queue, new HashSet<>()));
        }
        return searchStates.get(bitIndex);
    }

    private Set<EnterWireJunction> getLeadIns(Design d, int bitIndex) {
        if (snkTileEntrances.get(bitIndex) == null) {
            snkTileEntrances.set(bitIndex, FabricBrowser.findReachableEntrances(d, SINK_TILE_TRAVERSAL_MAX_DEPTH,
                    snkJunctions.get(bitIndex)));
        }
        return snkTileEntrances.get(bitIndex);
    }

    private ArrayList<TilePath> findTilePathsToSink(Design d, EnterWireJunction entrance, int bitIndex) {
        if (!snkTilePathsCache.get(bitIndex).containsKey(entrance)) {
            snkTilePathsCache.get(bitIndex).put(entrance, FabricBrowser.findTilePaths(d, SINK_TILE_TRAVERSAL_MAX_DEPTH,
                    entrance, snkJunctions.get(bitIndex)));
        }
        return snkTilePathsCache.get(bitIndex).get(entrance);
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

    private ArrayList<CustomRoute> getRoutes() {
        return routes;
    }

    private RoutingFootprint getFootprint() {
        return footprint;
    }

    /* Utility functions */
    /*
     * Find a batch of RouteTemplates (list of hop wires) which all connect source to sink
     * The nature of the search enforces different sink entrances, but does not care about overlaps in hops
     */
    private ArrayList<RouteTemplate> findRouteTemplateBatch(Design d, int batchSize, int bitIndex) {
        long tBegin = System.currentTimeMillis();

        EnterWireJunction src = srcJunctions.get(bitIndex);
        ExitWireJunction snk = snkJunctions.get(bitIndex);

        RouterLog.log("Routing template for " + src + " --> " + snk + " (batch size: " + batchSize + ").",
                RouterLog.Level.INFO);

        ArrayList<RouteTemplate> results = new ArrayList<>();

        Tile srcIntTile = d.getDevice().getTile(src.getTileName());
        Tile snkIntTile = d.getDevice().getTile(snk.getTileName());

        int snkTileX = snkIntTile.getTileXCoordinate();
        int snkTileY = snkIntTile.getTileYCoordinate();

        int srcTileX = srcIntTile.getTileXCoordinate();
        int srcTileY = srcIntTile.getTileYCoordinate();

        ArrayList<WireDirection> primaryDirs = RouteUtil.primaryDirections(snkTileX - srcTileX, snkTileY - srcTileY);

        HashSet<String> footprint = getSearchState(bitIndex).getRight();
        Queue<JunctionsTracer> queue = getSearchState(bitIndex).getLeft();

        Set<EnterWireJunction> leadIns = getLeadIns(d, bitIndex);

        int templateCount = 0;
        while (templateCount < batchSize) {
            JunctionsTracer head = queue.remove();
            Tile headTile = d.getDevice().getTile(head.getJunction().getTileName());

            if (head.getDepth() > 1000)
                break;

            int distX = headTile.getTileXCoordinate() - snkTileX;
            int distY = headTile.getTileYCoordinate() - snkTileY;

            if (distX == 0 && distY == 0) {
                boolean foundTemplate = false;
                EnterWireJunction validEntrance = null;
                for (EnterWireJunction leadIn : leadIns) {
                    if (leadIn.equals(head.getJunction())) {

                        RouteTemplate template = new RouteTemplate(d, src, snk);
                        JunctionsTracer trav = head;
                        while (trav.getParent() != null) {
                            template.pushEnterWireJunction(d, (EnterWireJunction) trav.getJunction());
                            trav = trav.getParent();
                        }

                        results.add(template);
                        foundTemplate = true;
                        validEntrance = leadIn;
                        break;
                    }
                }

                if (foundTemplate) {
                    templateCount += 1;
                    leadIns.remove(validEntrance);
                }

                continue;
            }

            Set<ExitWireJunction> fanOut = ((EnterWireJunction) head.getJunction()).isSrc()
                    ? FabricBrowser.findReachableExits(d, (EnterWireJunction) head.getJunction())
                    : FabricBrowser.getEntranceFanOut(d, (EnterWireJunction) head.getJunction());
            for (ExitWireJunction exit : fanOut) {
                EnterWireJunction wireDest = exit.getDestJunction(d);

                if (FabricBrowser.globalNodeFootprint.contains(wireDest.getNodeName())
                        || footprint.contains(wireDest.getNodeName()) || RouteForge.isLocked(wireDest.getNodeName())
                        || FabricBrowser.globalNodeFootprint.contains(exit.getNodeName())
                        || footprint.contains(exit.getNodeName()) || RouteForge.isLocked(exit.getNodeName()))
                    continue;

                // Check to see if search has veered far too off track
                if (head.isAlignedWithDeviationDirection(exit.getDirection()))
                    continue;

                // Set deviation from primary directions
                JunctionsTracer nextTracer = new JunctionsTracer(wireDest, head, head.getDepth() + 1);
                if (!primaryDirs.contains(exit.getDirection()))
                    nextTracer.addDeviation(exit.getDirection());

                queue.add(nextTracer);
                footprint.add(wireDest.getNodeName());
            }

        }

        if (results.size() == 0)
            RouterLog.log("Failed to determine routing templates.", RouterLog.Level.ERROR);
        else if (results.size() < batchSize)
            RouterLog.log("Failed to determine proper number of routing templates. Optimization will not be effective.",
                    RouterLog.Level.WARNING);
        else {
            RouterLog.log("Found " + results.size() + " templates:", RouterLog.Level.INFO);
            RouterLog.indent();
            for (RouteTemplate result : results)
                RouterLog.log(result.hopSummary(), RouterLog.Level.INFO);
            RouterLog.indent(-1);
            RouterLog.log("BFS search took " + (System.currentTimeMillis() - tBegin) + " ms.",
                    RouterLog.Level.VERBOSE);
        }

        return results;
    }

    /*
     * Unique cost calculation for deriving best tile paths
     */
    private int getAdjustedCost(TilePath path, RouteTemplate template) {
        return path.getCost() + template.getAdjustedCost();
    }

    private int getAdjustedCost(TilePath path, ArrayList<RouteTemplate> templates, HashMap<TilePath, Integer> cache) {
        if (!cache.containsKey(path))
            cache.put(path, getAdjustedCost(path, RoutingCalculator.findTemplateWithSinkTilePath(path, templates)));
        return cache.get(path);
    }

    /*
     * Find any TilePath configuration which has no conflicts
     * Results are not optimized for cost
     */
    private ArrayList<TilePath> deriveValidTilePaths(int depth, ArrayList<TilePath> validPathsState,
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

            /*
             * Remove for now: makes things too slow
            Set<String> templateUsage = RoutingCalculator.findTemplateWithSinkTilePath(candidate,
                    allTemplateCandidates.get(depth)).getUsage();
            for (String junctionName : templateUsage) {
                if (templateFootprint.contains(junctionName)) {
                    isValid = false;
                    break;
                }
            }

            if (!isValid)
                continue;
            */

            HashSet<String> nextDepthTilePathFootprint = new HashSet<>(tilePathFootprint);
            nextDepthTilePathFootprint.addAll(candidate.getNodePath());

            /*
             * Remove for now: makes things too slow
            HashSet<String> nextDepthTemplateFootprint = new HashSet<>(templateFootprint);
            nextDepthTemplateFootprint.addAll(templateUsage);
            */

            validPathsState.set(depth, candidate);

            ArrayList<TilePath> results = deriveValidTilePaths(depth + 1, validPathsState, nextDepthTilePathFootprint,
                    allPaths);
            if (results != null)
                return results;
        }

        return null;
    }

    /*
     * Uses an "easing" method to find the best TilePath configuration
     */
    private ArrayList<TilePath> deriveBestTilePathConfiguration(ArrayList<HashSet<TilePath>> allPaths) {

        // Given that finding adjusted cost is pretty expensive, cache results
        HashMap<TilePath, Integer> adjustedCostMap = new HashMap<>();

        // Highest cost possible
        int threshMax = 0;
        // Lost cost possible (max of min's across each bit)
        int threshMin = 0;

        for (int i = 0; i < bitwidth; i++) {
            HashSet<TilePath> paths = allPaths.get(i);
            int min = 99;
            for (TilePath path : paths) {
                if (getAdjustedCost(path, allTemplateCandidates.get(i), adjustedCostMap) > threshMax)
                    threshMax = getAdjustedCost(path, allTemplateCandidates.get(i), adjustedCostMap);
                if (getAdjustedCost(path, allTemplateCandidates.get(i), adjustedCostMap) < min)
                    min = getAdjustedCost(path, allTemplateCandidates.get(i), adjustedCostMap);
            }

            if (min > threshMin)
                threshMin = min;
        }

        ArrayList<HashSet<TilePath>> candidatePool = new ArrayList<>();
        for (int i = 0; i < bitwidth; i++)
            candidatePool.add(new HashSet<>());

        for (int threshold = threshMin; threshold <= threshMax; threshold++) {

            int additionsToCandidatePool = 0;
            for (int i = 0; i < bitwidth; i++) {
                HashSet<TilePath> pathChoices = allPaths.get(i);
                HashSet<TilePath> bitCandidates = new HashSet<>();
                for (TilePath path : pathChoices) {
                    if (getAdjustedCost(path, allTemplateCandidates.get(i), adjustedCostMap) == threshold) {
                        bitCandidates.add(path);
                        additionsToCandidatePool += 1;
                    }
                }
                candidatePool.get(i).addAll(bitCandidates);
            }

            // If nothing new was added to the candidate pool this round, simply move on to the next threshold
            if (additionsToCandidatePool == 0)
                continue;

            for (int i = 0; i < bitwidth; i++) {

                ArrayList<HashSet<TilePath>> candidates = new ArrayList<>(candidatePool);

                HashSet<TilePath> threshSet = new HashSet<>();
                for (TilePath path : candidates.get(i)) {
                    if (getAdjustedCost(path, allTemplateCandidates.get(i), adjustedCostMap) <= threshold)
                        threshSet.add(path);
                }
                candidates.set(i, threshSet);

                ArrayList<TilePath> results = new ArrayList<>();
                for (int j = 0; j < allPaths.size(); j++)
                    results.add(null);
                results = deriveValidTilePaths(0, results, new HashSet<>(), candidates);

                if (results != null) {
                    RouterLog.log("Sink paths found at a worst-case adjusted cost of " + threshold + ".",
                            RouterLog.Level.INFO);
                    return new ArrayList<>(results);
                }
            }
        }

        RouterLog.log("Deadlock detected. No tile paths configuration is possible.", RouterLog.Level.INFO);
        return null;
    }

    /* Functions for actual steps in routing */
    /*
     * Function for step 0
     * Locks in/out PIPs of source and sink junctions, since they are must-have for each bit line
     */
    private void lockJunctionPIPs(Design d) {
        for (RegisterComponent component : srcReg.getComponents()) {
            String intTileName = d.getDevice().getSite(component.getSiteName()).getIntTile().getName();
            for (int i = 0; i < component.getBitWidth(); i++) {
                RouteForge.lock(intTileName + "/" + component.getInPIPName(i));
                RouteForge.lock(intTileName + "/" + component.getOutPIPName(i));
            }
        }

        for (RegisterComponent component : snkReg.getComponents()) {
            String intTileName = d.getDevice().getSite(component.getSiteName()).getIntTile().getName();
            for (int i = 0; i < component.getBitWidth(); i++) {
                RouteForge.lock(intTileName + "/" + component.getInPIPName(i));
                RouteForge.lock(intTileName + "/" + component.getOutPIPName(i));
            }
        }
    }

    /*
     * Function for step 1
     * Group corresponding sources and sinks together for routing
     */
    private void populateJunctionPairs(Design d) {
        {
            int bitIndex = 0;
            int bitIndexWithOffset = 0;
            for (RegisterComponent component : srcReg.getComponents()) {
                String intTileName = d.getDevice().getSite(component.getSiteName()).getIntTile().getName();
                for (int i = 0; i < component.getBitWidth(); i++, bitIndex++) {
                    if (bitIndex >= connection.getSrcRegLowestBit() && bitIndex <= connection.getSrcRegHighestBit()) {
                        srcJunctions.set(bitIndexWithOffset,
                                EnterWireJunction.newSrcJunction(intTileName, component.getOutPIPName(i)));
                        bitIndexWithOffset += 1;
                    }
                }
            }
        }
        {
            int bitIndex = 0;
            int bitIndexWithOffset = 0;
            for (RegisterComponent component : snkReg.getComponents()) {
                String intTileName = d.getDevice().getSite(component.getSiteName()).getIntTile().getName();
                for (int i = 0; i < component.getBitWidth(); i++, bitIndex++) {
                    if (bitIndex >= connection.getSnkRegLowestBit() && bitIndex <= connection.getSnkRegHighestBit()) {
                        snkJunctions.set(bitIndexWithOffset,
                                ExitWireJunction.newSnkJunction(intTileName, component.getInPIPName(i)));
                        bitIndexWithOffset += 1;
                    }
                }
            }
        }

        for (int i = 0; i < bitwidth; i++)
            RouterLog.log(srcJunctions.get(i) + " --> " + snkJunctions.get(i), RouterLog.Level.VERBOSE);
    }

    /*
     * Function for step 2
     * Route a new batch of RouteTemplates for the entire bus
     * Calling this function again will cumulatively add more batches
     * Certain templates are purged if they conflict with others in the bus
     */
    private void extendTemplateBatchesForBus(Design d, int batchSize) {
        long tBegin = System.currentTimeMillis();

        int costPurgeCount = 0;
        int conflictPurgeCount = 0;
        HashMap<Integer, Set<String>> allNodeUsages = new HashMap<>();

        for (int i = 0; i < bitwidth; i++) {
            templateCandidatesCache.get(i).addAll(findRouteTemplateBatch(d, batchSize, i));

            HashSet<String> usages = new HashSet<>();
            for (RouteTemplate template : templateCandidatesCache.get(i)) {
                usages.addAll(template.getUsage());
            }
            allNodeUsages.put(i, usages);
        }

        // Copy cache contents so purge doesn't destroy cache
        for (int i = 0; i < bitwidth; i++)
            allTemplateCandidates.set(i, new ArrayList<>(templateCandidatesCache.get(i)));

        // Purge results of any unreasonable costly templates
        /*
        for (int i = 0; i < bitwidth; i++) {
            ArrayList<RouteTemplate> templateCandidates = allTemplateCandidates.get(i);
            int minCost = 99;
            for (RouteTemplate template : templateCandidates) {
                if (template.getAdjustedCost() < minCost)
                    minCost = template.getAdjustedCost();
            }
            for (int j = 0; j < templateCandidates.size();) {
                if (templateCandidates.get(j).getAdjustedCost() - minCost > TEMPLATE_COST_TOLERANCE) {
                    templateCandidates.remove(j);
                    costPurgeCount += 1;
                }
                else
                    j += 1;
            }
        }
        */

        // Purge results of any conflicting templates
        // Favor based on remaining number of choices
        for (int i = 0; i < bitwidth; i++) {
            ArrayList<RouteTemplate> templateCandidates = allTemplateCandidates.get(i);
            for (int j = 0; j < templateCandidates.size();) {

                HashMap<Integer, Set<String>> remainingUsages = new HashMap<>(allNodeUsages);
                remainingUsages.remove(i);

                Set<String> nodeUsage = templateCandidates.get(j).getUsage();

                ArrayList<Integer> conflictedBits = RoutingCalculator.locateTemplateCollisions(nodeUsage, remainingUsages);
                if (!conflictedBits.isEmpty()) {

                    boolean shouldPreempt;

                    int minTemplateChoices = 99;
                    for (int b : conflictedBits) {
                        if (allTemplateCandidates.get(b).size() < minTemplateChoices)
                            minTemplateChoices = allTemplateCandidates.get(b).size();
                    }

                    /*
                     * Make a decision based on how many template choices we have and adjusted cost:
                     * 1. Conflicted templates have less choices: remove this template candidate
                     * 2. Conflicted templates have more choices: remove conflicting bit template candidates
                     * 3. Conflicted templates have more or less the same choices: remove more expensive candidates
                     */
                    if (minTemplateChoices < templateCandidates.size() - 1)
                        shouldPreempt = false;
                    else if (minTemplateChoices > templateCandidates.size() + 1)
                        shouldPreempt = true;
                    else {
                        int minCost = 99;
                        for (int b : conflictedBits) {
                            for (int k = 0; k < allTemplateCandidates.get(b).size(); k++) {
                                for (WireJunction junction : allTemplateCandidates.get(b).get(k).getTemplate()) {
                                    if (nodeUsage.contains(junction.getNodeName())
                                            && allTemplateCandidates.get(b).get(k).getAdjustedCost() < minCost) {
                                        minCost = allTemplateCandidates.get(b).get(k).getAdjustedCost();
                                        break;
                                    }
                                }
                            }
                        }

                        if (minCost <= templateCandidates.get(j).getAdjustedCost())
                            shouldPreempt = false;
                        else
                            shouldPreempt = true;
                    }

                    if (shouldPreempt) {
                        // Remove conflicted bits' route templates
                        for (int b : conflictedBits) {
                            for (int k = 0; k < allTemplateCandidates.get(b).size();) {

                                boolean isConflicted = false;
                                for (WireJunction junction : allTemplateCandidates.get(b).get(k).getTemplate()) {
                                    if (nodeUsage.contains(junction.getNodeName())) {
                                        isConflicted = true;
                                        break;
                                    }
                                }

                                if (isConflicted) {
                                    allTemplateCandidates.get(b).remove(k);
                                    conflictPurgeCount += 1;
                                } else
                                    k += 1;
                            }
                        }
                    }
                    else {
                        templateCandidates.remove(j);
                        conflictPurgeCount += 1;
                    }
                }
                j++;
            }
        }

        cumulativeBatchSize += batchSize;

        ArrayList<Integer> sizes = new ArrayList<>();
        for (ArrayList<RouteTemplate> templateCandidates : allTemplateCandidates)
            sizes.add(templateCandidates.size());
        RouterLog.log("Updated template choices: " + sizes + ".", RouterLog.Level.INFO);
        RouterLog.log("A total of " + (costPurgeCount + conflictPurgeCount)
                + " templates were purged. " + conflictPurgeCount + " were purged due to conflicts.",
                RouterLog.Level.INFO);
        RouterLog.log("Batch search took " + (System.currentTimeMillis() - tBegin) + " ms.",
                RouterLog.Level.NORMAL);
    }

    /*
     * Function for step 3
     * Try and find the best configuration of RouteTemplates which have the cheapest sink routing configuration
     * With the final RouteTemplates found, create CustomRoutes based on the templates
     * If no configuration is possible, return false
     */
    private boolean deriveBestSinkPathConfiguration(Design d) {
        long tBegin = System.currentTimeMillis();

        ArrayList<HashSet<TilePath>> allEndPathChoices = new ArrayList<>();
        for (int i = 0; i < bitwidth; i++) {
            ArrayList<RouteTemplate> templateChoices = allTemplateCandidates.get(i);
            HashSet<TilePath> pathChoices = new HashSet<>();
            for (RouteTemplate template : templateChoices) {
                pathChoices.addAll(findTilePathsToSink(d, (EnterWireJunction) template.getTemplate(-2), i));
            }
            allEndPathChoices.add(pathChoices);
        }

        ArrayList<TilePath> endPaths = deriveBestTilePathConfiguration(allEndPathChoices);
        if (endPaths == null) {
            RouterLog.log("Failed to find sink tile paths.", RouterLog.Level.NORMAL);
            return false;
        }

        RouterLog.log("Sink paths found in " + (System.currentTimeMillis() - tBegin) + " ms.",
                RouterLog.Level.NORMAL);

        for (int i = 0; i < bitwidth; i++) {
            RouteTemplate template = RoutingCalculator.findTemplateWithSinkTilePath(endPaths.get(i),
                    allTemplateCandidates.get(i));
            CustomRoute route = new CustomRoute(template);
            route.setPath(-1, endPaths.get(i));
            routes.set(i, route);
        }
        return true;
    }

    /*
     * Function for step 4
     * For each CustomRoute, find possible TilePaths which can connect the hops together
     * These are called pathSubs
     */
    private void populatePathSubs(Design d) {
        long tBegin = System.currentTimeMillis();

        // Calculate tile paths for all except sink tile paths (already done in step 3)
        for (CustomRoute route : routes) {
            RouteTemplate template = route.getTemplate();
            for (int i = 0; i < template.getTemplate().size() / 2 - 1; i++) {
                route.setPathSub(i, FabricBrowser.findTilePaths(d, TILE_TRAVERSAL_MAX_DEPTH,
                        (EnterWireJunction) template.getTemplate(i * 2),
                        (ExitWireJunction) template.getTemplate(i * 2 + 1)));
            }
        }
        RouterLog.log("All tile paths found in " + (System.currentTimeMillis() - tBegin) + " ms.",
                RouterLog.Level.NORMAL);
    }

    /*
     * Function for step 5
     * Systematically determine which TilePath in the pathSubs to connect each hop within the bus
     * If there is a live lock, return false
     */
    private boolean routeContention(Design d) {
        long tBegin = System.currentTimeMillis();

        int preemptCount = 0;
        int liveLockCount = 0;

        int[] deflectionCount = new int[routes.size()];
        Queue<Pair<Integer, Integer>> routeQueue = new LinkedList<>();

        for (int i = 0; i < routes.size(); i++) {
            for (int j = 0; j < routes.get(i).getRoute().size() - 1; j++) {
                routeQueue.add(new ImmutablePair<>(i, j));
            }
        }

        // TODO: Doesn't produce absolutely optimal solutions
        while (!routeQueue.isEmpty() && liveLockCount < 9999) {
            Pair next = routeQueue.remove();
            liveLockCount += 1;

            int bitIndex = (int) next.getLeft();
            int pathIndex = (int) next.getRight();

            TilePath candidatePath = routes.get(bitIndex).getNextPossiblePath(pathIndex);

            for (Pair<Integer, Integer> conflict : RoutingCalculator.locateTilePathCollisions(candidatePath, routes)) {
                // During route contention, always preempt conflicts
                CustomRoute conflictedRoute = routes.get(conflict.getLeft());
                conflictedRoute.removePath(conflict.getRight());

                preemptCount += 1;
                deflectionCount[bitIndex] += 1;
                routeQueue.add(conflict);
            }

            routes.get(bitIndex).setPath(pathIndex, candidatePath);
        }

        if (liveLockCount >= 9999) {
            RouterLog.log("Route contention aborted (live lock detected).", RouterLog.Level.NORMAL);

            // Remove conflicting route: most likely the one with the highest deflection count
            int index = 0;
            int highestDeflection = 0;
            for (int i = 0; i < deflectionCount.length; i++) {
                if (highestDeflection < deflectionCount[i]) {
                    index = i;
                    highestDeflection = deflectionCount[i];
                }
            }
            templateCandidatesCache.remove(routes.get(index).getTemplate());

            return false;
        }

        RouterLog.log(preemptCount + " tile paths were rerouted due to contention.", RouterLog.Level.INFO);
        RouterLog.log("Route contention completed in " + (System.currentTimeMillis() - tBegin) + " ms.",
                RouterLog.Level.NORMAL);

        return true;
    }

    /*
     * Function for step 6
     * After routeContention, the CustomRoutes are fully ready to be routed
     * They are added the the RouteFootprint, which associates each CustomRoute to a physical net
     */
    private void addRoutesToFootprint(Design d) {
        int bitIndex = 0;
        int routeIndex = 0;
        for (RegisterComponent component : srcReg.getComponents()) {
            for (int i = 0; i < component.getBitWidth(); i++, bitIndex++) {
                if (bitIndex >= connection.getSrcRegLowestBit() && bitIndex <= connection.getSrcRegHighestBit()) {
                    Net net = d.getNet(srcReg.getName() + "_" + component.getName() + "/"
                            + ComplexRegister.OUTPUT_NAME + "[" + i + "]");

                    // This is the route's true bit index
                    routes.get(routeIndex).setBitIndex(bitIndex);
                    footprint.add(routes.get(routeIndex), net);
                    routeIndex += 1;
                }
            }
        }
    }

    /*
     * Core function for router
     */
    public static RoutingFootprint routeConnection(Design d, RegisterConnection connection) {

        RouterLog.log("Performing state-based, batch-based routing on " + connection.toString(), RouterLog.Level.NORMAL);
        RouterLog.indent();

        int lastState = -1;
        int state = 0;

        StatefulBatchRouter router = new StatefulBatchRouter(connection);
        router.beginTiming();

        while (state < 7) {

            int nextState = -1;

            switch (state) {

                case 0: {
                    RouterLog.log("0: Locking in/out PIPs of registers.", RouterLog.Level.NORMAL);

                    RouterLog.indent();
                    router.lockJunctionPIPs(d);
                    RouterLog.indent(-1);

                    nextState = state + 1;
                    break;
                }
                case 1: {
                    RouterLog.log("1: Finding corresponding src/snk junctions.", RouterLog.Level.NORMAL);

                    RouterLog.indent();
                    router.populateJunctionPairs(d);
                    RouterLog.indent(-1);

                    nextState = state + 1;
                    break;
                }
                case 2: {
                    int batchSize;
                    if (lastState == 1)
                        batchSize = 5;
                    else
                        batchSize = 3;

                    RouterLog.log("2: Calculating route templates (batch size: " + batchSize + ").", RouterLog.Level.NORMAL);

                    RouterLog.indent();
                    router.extendTemplateBatchesForBus(d, batchSize);
                    RouterLog.indent(-1);

                    nextState = state + 1;
                    break;
                }
                case 3: {
                    RouterLog.log("3: Using \"easing\" method to find best sink tile paths.", RouterLog.Level.NORMAL);

                    RouterLog.indent();
                    boolean success = router.deriveBestSinkPathConfiguration(d);
                    RouterLog.indent(-1);

                    if (!success) {
                        RouterLog.indent();
                        RouterLog.log("Restarting at step 2.", RouterLog.Level.NORMAL);
                        RouterLog.indent(-1);
                        nextState = 2;
                        break;
                    }

                    nextState = state + 1;
                    break;
                }
                case 4: {
                    RouterLog.log("4: Calculating tile paths for templates.", RouterLog.Level.NORMAL);

                    RouterLog.indent();
                    router.populatePathSubs(d);
                    RouterLog.indent(-1);

                    nextState = state + 1;
                    break;
                }
                case 5: {
                    RouterLog.log("5: Performing route contention.", RouterLog.Level.NORMAL);

                    RouterLog.indent();
                    boolean success = router.routeContention(d);
                    RouterLog.indent(-1);
                    if (!success) {
                        RouterLog.indent();
                        RouterLog.log("Restarting at step 2.", RouterLog.Level.NORMAL);
                        RouterLog.indent(-1);
                        nextState = 2;
                        break;
                    }

                    nextState = state + 1;
                    break;
                }
                case 6:
                    RouterLog.log("6: Compiling routing footprint.", RouterLog.Level.NORMAL);

                    RouterLog.indent();
                    router.addRoutesToFootprint(d);
                    RouterLog.indent(-1);

                    nextState = state + 1;
                    break;
                default:
                    break;
            }

            lastState = state;
            state = nextState;

        }

        router.finishTiming();

        RouterLog.indent(-1);
        RouterLog.log("Routing success. Connection routed in " + router.getElapsedTime() + " ms.", RouterLog.Level.NORMAL);

        RouterLog.log("Hop summary of connection:", RouterLog.Level.NORMAL);
        RouterLog.indent();
        for (CustomRoute route : router.getRoutes())
            RouterLog.log(route.getTemplate().hopSummary(), RouterLog.Level.NORMAL);
        RouterLog.indent(-1);


        RouteForge.flushNodeLock();

        return router.getFootprint();


    }

}
