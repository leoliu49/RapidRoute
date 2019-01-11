package com.uwaterloo.watcag.router;

import com.uwaterloo.watcag.common.ComplexRegister;
import com.uwaterloo.watcag.common.RegisterConnection;
import com.uwaterloo.watcag.config.RegisterComponent;
import com.uwaterloo.watcag.config.RegisterDefaults;
import com.uwaterloo.watcag.router.browser.FabricBrowser;
import com.uwaterloo.watcag.router.browser.JunctionsTracer;
import com.uwaterloo.watcag.router.elements.*;
import com.uwaterloo.watcag.util.RouterLog;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Net;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;

public class ThreadedRoutingJob implements Callable<RoutingFootprint> {

    /*
     * Routes a single register connection on a single thread
     */

    private static final int SINK_TILE_TRAVERSAL_MAX_DEPTH = 8;
    private static final int TILE_TRAVERSAL_MAX_DEPTH = FabricBrowser.TILE_TRAVERSAL_MAX_DEPTH;

    private long tBegin;
    private long tEnd;

    private Design coreDesign;

    private RouterLog.BufferedLog bufferedLog;

    private RegisterConnection connection;
    private int bitwidth;
    private ComplexRegister srcReg, snkReg;

    private ArrayList<EnterWireJunction> srcJunctions;
    private ArrayList<ExitWireJunction> snkJunctions;

    private ArrayList<Pair<PriorityQueue<JunctionsTracer>, Set<String>>> activeSearchStates;
    private ArrayList<Set<EnterWireJunction>> snkTileEntrances;

    private ArrayList<ArrayList<RouteTemplate>> templateCandidatesCache;
    private ArrayList<HashMap<EnterWireJunction, ArrayList<TilePath>>> snkTilePathsCache;

    private ArrayList<RouteTemplate> routeTemplates;
    private ArrayList<CustomRoute> routes;

    private RoutingFootprint footprint;

    public ThreadedRoutingJob(Design d, RegisterConnection connection) {
        super();

        coreDesign = d;

        bufferedLog = RouterLog.newBufferedLog();

        this.connection = connection;
        bitwidth = connection.getBitWidth();
        srcReg = connection.getSrcReg();
        snkReg = connection.getSnkReg();

        srcJunctions = new ArrayList<>();
        snkJunctions = new ArrayList<>();
        activeSearchStates = new ArrayList<>();
        snkTileEntrances = new ArrayList<>();
        templateCandidatesCache = new ArrayList<>();
        snkTilePathsCache = new ArrayList<>();
        routeTemplates = new ArrayList<>();
        routes = new ArrayList<>();

        for (int i = 0; i < bitwidth; i++) {
            srcJunctions.add(null);
            snkJunctions.add(null);
            activeSearchStates.add(null);
            snkTileEntrances.add(null);
            templateCandidatesCache.add(new ArrayList<>());
            snkTilePathsCache.add(new HashMap<>());
            routeTemplates.add(null);
            routes.add(null);
        }

        footprint = new RoutingFootprint(connection);
    }

    /* Accessors and modifiers */
    private PriorityQueue<JunctionsTracer> getActiveSearchQueue(int bitIndex) {
        if (activeSearchStates.get(bitIndex) == null)
            activeSearchStates.set(bitIndex, new ImmutablePair<>(new PriorityQueue<>(
                    new RoutingCalculator.JunctionsTracerCostComparator()), new HashSet<>()));
        return activeSearchStates.get(bitIndex).getLeft();
    }

    private Set<String> getActiveSearchFootprint(int bitIndex) {
        if (activeSearchStates.get(bitIndex) == null)
            activeSearchStates.set(bitIndex, new ImmutablePair<>(new PriorityQueue<>(), new HashSet<>()));
        return activeSearchStates.get(bitIndex).getRight();
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
     * Find any TilePath configuration which has no conflicts
     * Results are not optimized for cost
     */
    private ArrayList<TilePath> deriveValidTilePathsRecurse(int depth, ArrayList<TilePath> validPathsState,
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

    /*
     * Relatively fast function for determining if a tile path configuration is valid
     */
    private ArrayList<TilePath> deriveValidTilePaths(ArrayList<HashSet<TilePath>> allPaths) {
        ArrayList<HashSet<String>> exclusives = new ArrayList<>();

        for (int i = 0; i < bitwidth; i++) {
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
        for (int i = 0; i < bitwidth; i++) {
            for (String exclusiveNode : exclusives.get(i)) {
                if (allExclusives.contains(exclusiveNode))
                    return null;
                else
                    allExclusives.add(exclusiveNode);
            }
        }

        ArrayList<TilePath> results = new ArrayList<>();
        for (int i = 0; i < bitwidth; i++)
            results.add(null);
        return deriveValidTilePathsRecurse(0, results, new HashSet<>(), allPaths);

    }

    /*
     * Determines the best cost-wise configuration of tile paths
     */
    private ArrayList<TilePath> deriveBestTilePathConfiguration(ArrayList<ArrayList<TilePath>> allPaths) {
        // Highest cost possible
        int threshMax = 0;
        // Lost cost possible (max of min's across each bit)
        int threshMin = 0;

        for (int i = 0; i < bitwidth; i++) {
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
        for (int i = 0; i < bitwidth; i++) {
            candidatePool.add(new HashSet<>());
            for (TilePath path : allPaths.get(i)) {
                if (path.getCost() < threshMin)
                    candidatePool.get(i).add(path);
            }
        }

        for (int threshold = threshMin; threshold <= threshMax; threshold++) {
            ArrayList<HashSet<TilePath>> newCandidates = new ArrayList<>();
            for (int i = 0; i < bitwidth; i++)
                newCandidates.add(new HashSet<>());

            int additionsToCandidatePool = 0;
            for (int i = 0; i < bitwidth; i++) {
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

            for (int i = 0; i < bitwidth; i++) {
                if (newCandidates.get(i).isEmpty())
                    continue;

                ArrayList<HashSet<TilePath>> candidates = new ArrayList<>(candidatePool);
                candidates.set(i, newCandidates.get(i));

                ArrayList<TilePath> results = new ArrayList<>();
                for (int j = 0; j < bitwidth; j++)
                    results.add(null);

                results = deriveValidTilePathsRecurse(0, results, new HashSet<>(), candidates);

                if (results != null) {
                    return new ArrayList<>(results);
                }
            }
        }

        return null;
    }

    private ArrayList<CustomRoute> deriveRoutesFromTemplate(Design d, ArrayList<RouteTemplate> templates) throws Exception {

        ArrayList<ArrayList<TilePath>> allRoutes = new ArrayList<>();
        ArrayList<Integer> bitArray = new ArrayList<>();
        ArrayList<Integer> junctionIndexes = new ArrayList<>();
        HashSet<String> committedNodes = new HashSet<>();
        ArrayList<HashSet<String>> banList = new ArrayList<>();
        for (int i = 0; i < bitwidth; i++) {
            allRoutes.add(new ArrayList<>());
            bitArray.add(i);

            // Ignore sink path, for now
            junctionIndexes.add(2);
            banList.add(new HashSet<>());
        }

        while(!bitArray.isEmpty()) {

            // Iterative order prioritizes consolidating routes with less progress, then cost
            int bitIndex = bitArray.get(0);
            for (int b : bitArray) {
                if (junctionIndexes.get(b) < junctionIndexes.get(bitIndex)) {
                    bitIndex = b;
                }
                else if (junctionIndexes.get(b).equals(junctionIndexes.get(bitIndex))
                        && templates.get(b).getEstimatedCost() > templates.get(bitIndex).getEstimatedCost()) {
                    bitIndex = b;
                }
            }

            ArrayList<WireJunction> junctions = new ArrayList<>(templates.get(bitIndex).getTemplate());
            Collections.reverse(junctions);

            int junctionIndex = junctionIndexes.get(bitIndex);

            ExitWireJunction thisHop = (ExitWireJunction) junctions.get(junctionIndex);
            EnterWireJunction previousHop = (EnterWireJunction) junctions.get(junctionIndex + 1);

            boolean isValid;
            if (committedNodes.contains(previousHop.getNodeName())
                    || (!previousHop.isSrc() && committedNodes.contains(previousHop.getSrcJunction(d).getNodeName()))) {
                // Conflict found in hops
                isValid = false;

                banList.get(bitIndex).add(previousHop.getNodeName());
                banList.get(bitIndex).add(previousHop.getSrcJunction(d).getNodeName());
            }
            else {
                TilePath path = FabricBrowser.findClosestTilePath(d, previousHop, thisHop, committedNodes);

                if (path == null) {
                    // Conflict / un-routable found in tile paths
                    isValid = false;

                    banList.get(bitIndex).add(previousHop.getNodeName());
                    if (!previousHop.isSrc())
                        banList.get(bitIndex).add(previousHop.getSrcJunction(d).getNodeName());
                }
                else {
                    // No conflict
                    isValid = true;
                    allRoutes.get(bitIndex).add(0, path);

                    committedNodes.addAll(path.getNodePath());

                    if (!previousHop.isSrc())
                        committedNodes.add(previousHop.getSrcJunction(d).getNodeName());
                }
            }

            if (!isValid) {
                // Rip-up and reroute

                for (TilePath path : allRoutes.get(bitIndex)) {
                    committedNodes.removeAll(path.getNodePath());
                }
                allRoutes.get(bitIndex).clear();
                junctionIndexes.set(bitIndex, 2);

                Set<String> nodesToAvoid = new HashSet<>(committedNodes);
                nodesToAvoid.addAll(banList.get(bitIndex));

                ExitWireJunction detourSnk = (ExitWireJunction) junctions.get(2);

                ThreadedSearchJob newSearchJob = new ThreadedSearchJob(coreDesign, templates.get(bitIndex).getSrc(),
                        detourSnk);
                newSearchJob.setBatchSize(1);
                newSearchJob.setBanList(nodesToAvoid);

                Set<EnterWireJunction> leadIns = new HashSet<>();
                for (EnterWireJunction junction : FabricBrowser.findReachableEntrances(d, detourSnk)) {
                    if (!nodesToAvoid.contains(junction.getNodeName()))
                        leadIns.add(junction);
                }
                newSearchJob.setLeadIns(leadIns);

                templates.get(bitIndex).replaceTemplate(d, newSearchJob.getSrc(), newSearchJob.getSnk(),
                        newSearchJob.call().get(0));
            }
            else {
                junctionIndexes.set(bitIndex, junctionIndex + 2);
                if (junctionIndexes.get(bitIndex) >= templates.get(bitIndex).getTemplate().size())
                    bitArray.remove(Integer.valueOf(bitIndex));
            }

        }

        ArrayList<CustomRoute> results = new ArrayList<>();
        for (int i = 0; i < bitwidth; i++) {
            CustomRoute route = new CustomRoute(templates.get(i));
            route.setRoute(allRoutes.get(i));

            // For sink path, later on
            route.getRoute().add(null);
            results.add(route);
        }

        return results;
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

    }

    /*
     * Function for step 2
     * Route a new batch of RouteTemplates for the entire bus
     * Calling this function again will cumulatively add more batches
     * Certain templates are purged if they conflict with others in the bus
     */
    private boolean extendTemplateBatchesForBus(Design d, int batchSize) throws Exception {

        ArrayList<Future<ArrayList<RouteTemplate>>> searchJobResults = new ArrayList<>();
        for (int i = 0; i < bitwidth; i++) {
            ThreadedSearchJob job = new ThreadedSearchJob(d, srcJunctions.get(i), snkJunctions.get(i));
            job.setBatchSize(batchSize);
            job.setSearchQueue(getActiveSearchQueue(i));
            job.setSearchFootprint(getActiveSearchFootprint(i));
            job.setLeadIns(getLeadIns(d, i));

            searchJobResults.add(DesignRouter.executor.submit(job));
        }

        for (int i = 0; i < bitwidth; i++)
            templateCandidatesCache.get(i).addAll(searchJobResults.get(i).get());


        // Determine a set of templates which have working leadIns at the sink tile, prioritizing low costs
        ArrayList<RouteTemplate> validTemplates = new ArrayList<>();

        // Highest cost possible
        int threshMax = 0;
        // Lost cost possible (max of min's across each bit)
        int threshMin = 0;

        for (int i = 0; i < bitwidth; i++) {
            ArrayList<RouteTemplate> candidates = templateCandidatesCache.get(i);
            int min = 999;
            for (RouteTemplate candidate : candidates) {
                if (candidate.getEstimatedCost() > threshMax)
                    threshMax = candidate.getEstimatedCost();
                if (candidate.getEstimatedCost() < min)
                    min = candidate.getEstimatedCost();
            }

            if (min > threshMin)
                threshMin = min;
        }

        ArrayList<HashSet<TilePath>> tilePathsPool = new ArrayList<>();
        for (int i = 0; i < bitwidth; i++) {
            HashSet<TilePath> pathChoices = new HashSet<>();
            for (RouteTemplate template : templateCandidatesCache.get(i)) {
                if (template.getEstimatedCost() < threshMin) {
                    pathChoices.addAll(findTilePathsToSink(d, (EnterWireJunction) template.getTemplate(-2), i));
                }
            }
            tilePathsPool.add(pathChoices);
        }

        // Iterating up to the highest cost, increase the pool each time until a configuration can be found
        for (int threshold = threshMin; threshold <= threshMax; threshold++) {
            ArrayList<HashSet<TilePath>> newCandidates = new ArrayList<>();
            for (int i = 0; i < bitwidth; i++)
                newCandidates.add(new HashSet<>());

            int additionsToCandidatePool = 0;
            for (int i = 0; i < bitwidth; i++) {
                ArrayList<RouteTemplate> candidates = templateCandidatesCache.get(i);
                for (RouteTemplate candidate : candidates) {
                    if (candidate.getEstimatedCost() == threshold) {
                        newCandidates.get(i).addAll(findTilePathsToSink(d,
                                (EnterWireJunction) candidate.getTemplate(-2), i));

                        additionsToCandidatePool += 1;
                    }
                }
            }

            // If nothing new was added to the candidate pool this round, simply move on to the next threshold
            if (additionsToCandidatePool == 0)
                continue;

            for (int i = 0; i < bitwidth; i++) {
                if (newCandidates.get(i).isEmpty())
                    continue;

                ArrayList<HashSet<TilePath>> candidates = new ArrayList<>(tilePathsPool);
                candidates.set(i, newCandidates.get(i));

                ArrayList<TilePath> results = deriveValidTilePaths(candidates);

                if (results != null) {
                    for (int j = 0; j < bitwidth; j++) {
                        for (RouteTemplate template : templateCandidatesCache.get(j)) {
                            if (template.getTemplate(-2).equals(results.get(j).getEnterJunction())) {
                                validTemplates.add(template);
                                break;
                            }
                        }
                    }

                    break;
                }
            }

            if (!validTemplates.isEmpty())
                break;

            // Increment new candidates into pool for next round
            for (int i = 0; i < bitwidth; i++) {
                tilePathsPool.get(i).addAll(newCandidates.get(i));
            }
        }

        // Failure condition: no valid templates found
        if (validTemplates.isEmpty())
            return false;

        for (int i = 0; i < bitwidth; i++)
            routeTemplates.set(i, validTemplates.get(i));

        return true;
    }

    /*
     * Function for step 3
     * Turn templates into actual routes (populate tile paths) by doing iterative conflict resolution
     */
    private void consolidateRoutes(Design d) throws Exception {
        long tBegin = System.currentTimeMillis();

        ArrayList<CustomRoute> consolidatedRoutes = deriveRoutesFromTemplate(d, routeTemplates);

        for (int i = 0; i < bitwidth; i++) {
            routes.set(i, consolidatedRoutes.get(i));
        }
    }

    /*
     * Function for step 4
     * Try and find the best configuration of RouteTemplates which have the cheapest sink routing configuration
     * With the final RouteTemplates found, create CustomRoutes based on the templates
     * If no configuration is possible, return false
     */
    private boolean deriveBestSinkPathConfiguration(Design d) {
        long tBegin = System.currentTimeMillis();

        ArrayList<ArrayList<TilePath>> allEndPathChoices = new ArrayList<>();
        for (int i = 0; i < bitwidth; i++) {
            allEndPathChoices.add(findTilePathsToSink(d,
                    (EnterWireJunction) routes.get(i).getTemplate().getTemplate(-2), i));
        }

        ArrayList<TilePath> endPaths = deriveBestTilePathConfiguration(allEndPathChoices);
        if (endPaths == null) {
            return false;
        }

        for (int i = 0; i < bitwidth; i++) {
            routes.get(i).setPath(-1, endPaths.get(i));
        }

        return true;
    }

    /*
     * Function for step 5
     * After routeContention, the CustomRoutes are fully ready to be routed
     * They are added the the RouteFootprint, which associates each com.uwaterloo.watcag.router.elements.CustomRoute to a physical net
     */
    private void addRoutesToFootprint(Design d) {
        int bitIndex = 0;
        int routeIndex = 0;
        for (RegisterComponent component : srcReg.getComponents()) {
            for (int i = 0; i < component.getBitWidth(); i++, bitIndex++) {
                if (bitIndex >= connection.getSrcRegLowestBit() && bitIndex <= connection.getSrcRegHighestBit()) {
                    Net net = d.getNet(srcReg.getName() + "_" + component.getName() + "/"
                            + RegisterDefaults.OUTPUT_NAME + "[" + i + "]");

                    // This is the route's true bit index
                    routes.get(routeIndex).setBitIndex(bitIndex);
                    routes.get(routeIndex).setRouteIndex(routeIndex);
                    footprint.add(routes.get(routeIndex), net);
                    routeIndex += 1;
                }
            }
        }
    }

    @Override
    public RoutingFootprint call() throws Exception {
        beginTiming();

        bufferedLog.log("Performing state-based, batch-based routing on " + connection.toString(),
                RouterLog.Level.NORMAL);

        int lastState = -1;
        int state = 0;

        while (state < 6) {

            int nextState = -1;

            switch (state) {

                case 0: {
                    bufferedLog.log("0: Locking in/out PIPs of registers.", RouterLog.Level.NORMAL);
                    lockJunctionPIPs(coreDesign);
                    nextState = state + 1;
                    break;
                }
                case 1: {
                    bufferedLog.log("1: Finding corresponding src/snk junctions.", RouterLog.Level.NORMAL);
                    populateJunctionPairs(coreDesign);
                    nextState = state + 1;
                    break;
                }
                case 2: {
                    int batchSize;
                    if (lastState == 1)
                        batchSize = bitwidth;
                    else
                        batchSize = 3;

                    bufferedLog.log("2: Calculating route templates (batch size: " + batchSize + ").",
                            RouterLog.Level.NORMAL);
                    boolean success = false;
                    try {
                        success = extendTemplateBatchesForBus(coreDesign, batchSize);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    if (success)
                        nextState = state + 1;
                    else {
                        bufferedLog.indent();
                        bufferedLog.log("Unable to find working templates. Restarting at step 2.",
                                RouterLog.Level.NORMAL);
                        bufferedLog.indent(-1);
                    }
                    break;
                }
                case 3: {
                    bufferedLog.log("3: Consolidating routes.", RouterLog.Level.NORMAL);
                    consolidateRoutes(coreDesign);
                    nextState = state + 1;
                    break;
                }
                case 4: {
                    bufferedLog.log("4: Using \"easing\" method to find best sink tile paths.", RouterLog.Level.NORMAL);
                    boolean success = deriveBestSinkPathConfiguration(coreDesign);
                    if (!success) {
                        bufferedLog.indent();
                        bufferedLog.log("Failed to find best sink tile paths. Restarting at step 2.",
                                RouterLog.Level.NORMAL);
                        bufferedLog.indent(-1);
                        nextState = 2;
                    }
                    else
                        nextState = state + 1;
                    break;
                }
                case 5: {
                    bufferedLog.log("5: Compiling routing footprint.", RouterLog.Level.NORMAL);
                    addRoutesToFootprint(coreDesign);
                    nextState = state + 1;
                    break;
                }
                default:
                    break;
            }

            lastState = state;
            state = nextState;

        }

        finishTiming();


        //DesignRouter.completeRoutingJob(connection, footprint);

        bufferedLog.log("Routing success. Connection routed in " + getElapsedTime() + " ms.", RouterLog.Level.NORMAL);
        bufferedLog.log("Hop summary of connection:", RouterLog.Level.NORMAL);
        bufferedLog.indent();
        for (CustomRoute route : getRoutes())
            bufferedLog.log(route.getTemplate().hopSummary(), RouterLog.Level.NORMAL);
        bufferedLog.indent(-1);

        bufferedLog.dumpLog();

        return footprint;
    }
}
