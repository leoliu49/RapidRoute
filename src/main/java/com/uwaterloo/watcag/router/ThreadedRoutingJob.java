package com.uwaterloo.watcag.router;

import com.uwaterloo.watcag.common.ComplexRegister;
import com.uwaterloo.watcag.common.RegisterConnection;
import com.uwaterloo.watcag.config.RegisterComponent;
import com.uwaterloo.watcag.config.RegisterDefaults;
import com.uwaterloo.watcag.router.browser.FabricBrowser;
import com.uwaterloo.watcag.router.browser.JunctionsTracer;
import com.uwaterloo.watcag.router.elements.*;
import com.uwaterloo.watcag.util.RouteUtil;
import com.uwaterloo.watcag.util.RouterLog;
import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.device.Tile;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.MutablePair;
import org.apache.commons.lang3.tuple.Pair;

import java.util.*;

public class ThreadedRoutingJob extends Thread {

    /*
     * Routes a single register connection on a single thread
     */

    private static final int SINK_TILE_TRAVERSAL_MAX_DEPTH = 8;
    private static final int TILE_TRAVERSAL_MAX_DEPTH = FabricBrowser.TILE_TRAVERSAL_MAX_DEPTH;

    private static final int V_LONG_LINE_LENGTH = 12;
    private static final int H_LONG_LINE_LENGTH = 6;

    private long tBegin;
    private long tEnd;

    private Design coreDesign;
    private int jobID;

    private RouterLog.BufferedLog bufferedLog;

    private RegisterConnection connection;
    private int bitwidth;
    private ComplexRegister srcReg, snkReg;

    public ArrayList<EnterWireJunction> srcJunctions;
    public ArrayList<ExitWireJunction> snkJunctions;

    private ArrayList<Pair<Queue<JunctionsTracer>, HashMap<String, Integer>>> searchStates;
    private ArrayList<Set<EnterWireJunction>> snkTileEntrances;

    private ArrayList<ArrayList<RouteTemplate>> templateCandidatesCache;
    private ArrayList<HashMap<EnterWireJunction, ArrayList<TilePath>>> snkTilePathsCache;
    private ArrayList<CustomRoute> routes;

    private RoutingFootprint footprint;

    public ThreadedRoutingJob(Design d, int jobID, RegisterConnection connection) {
        super();

        coreDesign = d;
        this.jobID = jobID;

        bufferedLog = RouterLog.newBufferedLog();

        this.connection = connection;
        bitwidth = connection.getBitWidth();
        srcReg = connection.getSrcReg();
        snkReg = connection.getSnkReg();

        srcJunctions = new ArrayList<>();
        snkJunctions = new ArrayList<>();
        searchStates = new ArrayList<>();
        snkTileEntrances = new ArrayList<>();
        templateCandidatesCache = new ArrayList<>();
        snkTilePathsCache = new ArrayList<>();
        routes = new ArrayList<>();

        for (int i = 0; i < bitwidth; i++) {
            srcJunctions.add(null);
            snkJunctions.add(null);
            searchStates.add(null);
            snkTileEntrances.add(null);
            templateCandidatesCache.add(new ArrayList<>());
            snkTilePathsCache.add(new HashMap<>());
            routes.add(null);
        }

        footprint = new RoutingFootprint(connection);
    }

    /* Accessors and modifiers */
    private Pair<Queue<JunctionsTracer>, HashMap<String, Integer>> getSearchState(int bitIndex) {
        if (searchStates.get(bitIndex) == null) {
            Queue<JunctionsTracer> queue = new LinkedList<>();
            searchStates.set(bitIndex, new MutablePair<>(queue, new HashMap<>()));
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
     */
    private ArrayList<RouteTemplate> findRouteTemplateBatch(Design d, int batchSize, int bitIndex) {
        long tBegin = System.currentTimeMillis();

        EnterWireJunction src = srcJunctions.get(bitIndex);
        ExitWireJunction snk = snkJunctions.get(bitIndex);

        ArrayList<RouteTemplate> results = new ArrayList<>();

        Tile srcIntTile = d.getDevice().getTile(src.getTileName());
        Tile snkIntTile = d.getDevice().getTile(snk.getTileName());

        int snkTileX = snkIntTile.getTileXCoordinate();
        int snkTileY = snkIntTile.getTileYCoordinate();

        int srcTileX = srcIntTile.getTileXCoordinate();
        int srcTileY = srcIntTile.getTileYCoordinate();

        HashMap<String, Integer> footprint = getSearchState(bitIndex).getRight();
        Queue<JunctionsTracer> queue = getSearchState(bitIndex).getLeft();

        // For the first time, load in exits from all directions
        if (queue.isEmpty()) {
            JunctionsTracer srcTracer = JunctionsTracer.newHeadTracer(srcJunctions.get(bitIndex));
            for (ExitWireJunction exit : FabricBrowser.findReachableExits(d, srcJunctions.get(bitIndex))) {
                EnterWireJunction wireDest = exit.getDestJunction(d);
                if (RouteForge.isLocked(wireDest.getNodeName()) || RouteForge.isLocked(exit.getNodeName()))
                    continue;

                queue.add(new JunctionsTracer(wireDest, srcTracer));
            }
        }

        Set<EnterWireJunction> leadIns = getLeadIns(d, bitIndex);

        int templateCount = 0;
        while (templateCount < batchSize) {
            JunctionsTracer trav = queue.remove();
            Tile headTile = d.getDevice().getTile(trav.getHead().getTileName());

            if (trav.getDepth() > 1000)
                break;

            int distX = snkTileX - headTile.getTileXCoordinate();
            int distY = snkTileY - headTile.getTileYCoordinate();

            if (distX == 0 && distY == 0) {
                boolean foundTemplate = false;
                EnterWireJunction validLeadIn = null;
                for (EnterWireJunction leadIn : leadIns) {
                    if (leadIn.equals(trav.getHead())) {

                        RouteTemplate template = new RouteTemplate(d, src, snk);
                        template.setBaseAdjustedCost(leadIn.getTilePathCost());

                        while (trav.getJunctions().size() > 1)
                            template.pushEnterWireJunction(d, (EnterWireJunction) trav.getJunctions().removeFirst());

                        results.add(template);
                        foundTemplate = true;
                        validLeadIn = leadIn;

                        footprint.put(leadIn.getNodeName(),
                                footprint.containsKey(leadIn.getNodeName())
                                        ? footprint.get(leadIn.getNodeName()) + 1 : 1);

                        break;
                    }
                }

                if (foundTemplate) {
                    templateCount += 1;
                    if (footprint.get(validLeadIn.getNodeName()) == 5)
                        leadIns.remove(validLeadIn);
                }

                continue;
            }

            ArrayList<WireDirection> primaryDirs = RouteUtil.primaryDirections(distX, distY);
            Set<ExitWireJunction> fanOut = FabricBrowser.getEntranceFanOut(d, (EnterWireJunction) trav.getHead());
            for (ExitWireJunction exit : fanOut) {
                EnterWireJunction wireDest = exit.getDestJunction(d);

                if (RouteForge.isLocked(wireDest.getNodeName()) || RouteForge.isLocked(exit.getNodeName()))
                    continue;

                if (footprint.containsKey(wireDest.getNodeName()) && footprint.get(wireDest.getNodeName()) > 2)
                    continue;

                if (!primaryDirs.contains(exit.getDirection()))
                    continue;

                // On long lines, we can typically just fast-forward until the end
                if (RouteUtil.isHorizontal(wireDest.getDirection()) && wireDest.getWireLength() == H_LONG_LINE_LENGTH) {

                    JunctionsTracer fastFwd = new JunctionsTracer(wireDest, trav);
                    int dx = distX
                            - (exit.getDirection().equals(WireDirection.EAST)
                            ? exit.getWireLength() : -1 * exit.getWireLength());

                    while (Math.abs(dx) > H_LONG_LINE_LENGTH) {
                        boolean canFastForward = false;
                        for (ExitWireJunction nextHop : FabricBrowser.getEntranceFanOut(d,
                                (EnterWireJunction) fastFwd.getHead())) {
                            if (nextHop.getWireName().equals(exit.getWireName())
                                    && !RouteForge.isLocked(nextHop.getNodeName())
                                    && !RouteForge.isLocked(nextHop.getDestJunction(d).getNodeName())) {

                                fastFwd.fastForward(nextHop.getDestJunction(d));
                                canFastForward = true;

                                dx -= nextHop.getDirection().equals(WireDirection.EAST)
                                        ? nextHop.getWireLength() : -1 * nextHop.getWireLength();

                                footprint.put(nextHop.getDestJunction(d).getNodeName(),
                                        footprint.containsKey(nextHop.getDestJunction(d).getNodeName())
                                                ? footprint.get(nextHop.getDestJunction(d).getNodeName()) + 1 : 1);
                                break;
                            }
                        }

                        if (!canFastForward)
                            break;
                    }

                    queue.add(fastFwd);
                }
                else if (RouteUtil.isVertical(wireDest.getDirection()) && wireDest.getWireLength() == V_LONG_LINE_LENGTH) {

                    JunctionsTracer fastFwd = new JunctionsTracer(wireDest, trav);
                    int dy = distY
                            - (exit.getDirection().equals(WireDirection.NORTH)
                            ? exit.getWireLength() : -1 * exit.getWireLength());

                    while (Math.abs(dy) > V_LONG_LINE_LENGTH) {
                        boolean canFastForward = false;
                        for (ExitWireJunction nextHop : FabricBrowser.getEntranceFanOut(d,
                                (EnterWireJunction) fastFwd.getHead())) {
                            if (nextHop.getWireName().equals(exit.getWireName())
                                    && !RouteForge.isLocked(nextHop.getNodeName())
                                    && !RouteForge.isLocked(nextHop.getDestJunction(d).getNodeName())) {

                                fastFwd.fastForward(nextHop.getDestJunction(d));
                                canFastForward = true;

                                dy -= nextHop.getDirection().equals(WireDirection.NORTH)
                                        ? nextHop.getWireLength() : -1 * nextHop.getWireLength();

                                footprint.put(nextHop.getDestJunction(d).getNodeName(),
                                        footprint.containsKey(nextHop.getDestJunction(d).getNodeName())
                                                ? footprint.get(nextHop.getDestJunction(d).getNodeName()) + 1 : 1);
                                break;
                            }
                        }

                        if (!canFastForward)
                            break;
                    }

                    queue.add(fastFwd);
                }
                else {
                    queue.add(new JunctionsTracer(wireDest, trav));
                    footprint.put(wireDest.getNodeName(),
                            footprint.containsKey(wireDest.getNodeName())
                                    ? footprint.get(wireDest.getNodeName()) + 1 : 1);
                }
            }

        }

        if (results.size() == 0) {

        }
        else if (results.size() < batchSize) {

        }
        return results;
    }

    /*
     * Find any com.uwaterloo.watcag.router.elements.TilePath configuration which has no conflicts
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

    private ArrayList<RouteTemplate> deriveValidTemplateConfiguration(Design d, int depth,
                                                                      ArrayList<RouteTemplate> validTemplatesState,
                                                                      HashSet<String> templateFootprint,
                                                                      ArrayList<HashSet<RouteTemplate>> allTemplates) {
        if (depth == allTemplates.size()) {
            // Once we've found a valid template configuration, see if a sink path configuration is possible
            ArrayList<HashSet<TilePath>> sinkPaths = new ArrayList<>();

            for (int i = 0; i < bitwidth; i++) {
                sinkPaths.add(new HashSet<>());
                sinkPaths.get(i).addAll(findTilePathsToSink(d,
                        (EnterWireJunction) validTemplatesState.get(i).getTemplate(-2), i));
            }

            if (deriveValidTilePaths(sinkPaths) == null) {
                return null;
            }

            return validTemplatesState;
        }

        HashSet<RouteTemplate> templateChoices = allTemplates.get(depth);
        if (templateChoices == null || templateChoices.isEmpty())
            return null;

        for (RouteTemplate candidate : templateChoices) {
            boolean isValid = true;
            for (String nodeName : candidate.getUsage()) {
                if (templateFootprint.contains(nodeName)) {
                    isValid = false;
                    break;
                }
            }

            if (!isValid)
                continue;

            HashSet<String> nextDepthTemplateFootprint = new HashSet<>(templateFootprint);
            nextDepthTemplateFootprint.addAll(candidate.getUsage());

            validTemplatesState.set(depth, candidate);

            ArrayList<RouteTemplate> results = deriveValidTemplateConfiguration(d, depth + 1, validTemplatesState,
                    nextDepthTemplateFootprint, allTemplates);
            if (results != null)
                return results;
        }

        return null;
    }

    private ArrayList<RouteTemplate> deriveBestTemplateConfiguration(Design d,
                                                                     ArrayList<ArrayList<RouteTemplate>> allTemplates) {
        // Highest cost possible
        int threshMax = 0;
        // Lost cost possible (max of min's across each bit)
        int threshMin = 0;

        for (int i = 0; i < bitwidth; i++) {
            ArrayList<RouteTemplate> candidates = allTemplates.get(i);
            int min = 999;
            for (RouteTemplate candidate : candidates) {
                if (candidate.getAdjustedCost() > threshMax)
                    threshMax = candidate.getAdjustedCost();
                if (candidate.getAdjustedCost() < min)
                    min = candidate.getAdjustedCost();
            }

            if (min > threshMin)
                threshMin = min;
        }

        ArrayList<HashSet<RouteTemplate>> candidatePool = new ArrayList<>();
        for (int i = 0; i < bitwidth; i++) {
            candidatePool.add(new HashSet<>());
            for (RouteTemplate template : allTemplates.get(i)) {
                if (template.getAdjustedCost() < threshMin)
                    candidatePool.get(i).add(template);
            }
        }

        for (int threshold = threshMin; threshold <= threshMax; threshold++) {
            ArrayList<HashSet<RouteTemplate>> newCandidates = new ArrayList<>();
            for (int i = 0; i < bitwidth; i++)
                newCandidates.add(new HashSet<>());

            int additionsToCandidatePool = 0;
            for (int i = 0; i < bitwidth; i++) {
                ArrayList<RouteTemplate> candidates = allTemplates.get(i);
                for (RouteTemplate candidate : candidates) {
                    if (candidate.getAdjustedCost() == threshold) {
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

                ArrayList<HashSet<RouteTemplate>> candidates = new ArrayList<>(candidatePool);
                candidates.set(i, newCandidates.get(i));

                ArrayList<RouteTemplate> results = new ArrayList<>();
                for (int j = 0; j < bitwidth; j++)
                    results.add(null);

                results = deriveValidTemplateConfiguration(d, 0, results, new HashSet<>(), candidates);

                if (results != null) {
                    return new ArrayList<>(results);
                }
            }
        }

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

    }

    /*
     * Function for step 2
     * Route a new batch of RouteTemplates for the entire bus
     * Calling this function again will cumulatively add more batches
     * Certain default-templates are purged if they conflict with others in the bus
     */
    private boolean extendTemplateBatchesForBus(Design d, int batchSize) {

        ArrayList<ArrayList<RouteTemplate>> thisBatch = new ArrayList<>();
        for (int i = 0; i < bitwidth; i++) {
            thisBatch.add(findRouteTemplateBatch(d, batchSize, i));
            templateCandidatesCache.get(i).addAll(thisBatch.get(i));
        }

        ArrayList<RouteTemplate> templates = deriveBestTemplateConfiguration(d, templateCandidatesCache);
        if (templates == null) {
            return false;
        }

        for (int i = 0; i < bitwidth; i++)
            routes.set(i, new CustomRoute(templates.get(i)));

        return true;

    }

    /*
     * Function for step 3
     * Try and find the best configuration of RouteTemplates which have the cheapest sink routing configuration
     * With the final RouteTemplates found, create CustomRoutes based on the default-templates
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
     * Function for step 4
     * For each com.uwaterloo.watcag.router.elements.CustomRoute, find possible TilePaths which can connect the hops together
     * These are called pathSubs
     */
    private void populatePathSubs(Design d) {
        long tBegin = System.currentTimeMillis();

        int bitIndex = 0;
        // Calculate tile paths for all except sink tile paths (already done in step 3)
        for (CustomRoute route : routes) {
            RouteTemplate template = route.getTemplate();
            for (int i = 0; i < route.getPathSubs().size() - 1; i++) {
                route.setPathSub(i, FabricBrowser.findTilePaths(d, TILE_TRAVERSAL_MAX_DEPTH,
                        (EnterWireJunction) template.getTemplate(i * 2),
                        (ExitWireJunction) template.getTemplate(i * 2 + 1)));
            }
            /*
            for (int i = 0; i < template.getTemplate().size() / 2 - 1; i++) {
                route.setPathSub(i, com.uwaterloo.watcag.router.browser.FabricBrowser.findTilePaths(d, TILE_TRAVERSAL_MAX_DEPTH,
                        (com.uwaterloo.watcag.router.elements.EnterWireJunction) template.getTemplate(i * 2),
                        (com.uwaterloo.watcag.router.elements.ExitWireJunction) template.getTemplate(i * 2 + 1)));
            }
            */

            route.setPathSub(-1, findTilePathsToSink(d, (EnterWireJunction) route.getTemplate().getTemplate(-2),
                    bitIndex));

            bitIndex += 1;
        }
    }

    /*
     * Function for step 5
     * Systematically determine which com.uwaterloo.watcag.router.elements.TilePath in the pathSubs to connect each hop within the bus
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
            Pair<Integer, Integer> next = routeQueue.remove();
            liveLockCount += 1;

            int bitIndex = next.getLeft();
            int pathIndex = next.getRight();

            TilePath candidatePath = routes.get(bitIndex).getNextPossiblePath(pathIndex);

            // In the case that there are no pathSubs available, break and kill this template
            if (candidatePath == null) {
                deflectionCount[bitIndex] = 10000;
                liveLockCount = 10000;
                break;
            }

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

            // Remove conflicting route: most likely the one with the highest deflection count
            int index = 0;
            int highestDeflection = 0;
            for (int i = 0; i < deflectionCount.length; i++) {
                if (highestDeflection < deflectionCount[i]) {
                    index = i;
                    highestDeflection = deflectionCount[i];
                }
            }
            templateCandidatesCache.get(index).remove(routes.get(index).getTemplate());

            return false;
        }

        return true;
    }

    /*
     * Function for step 6
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
    public void run() {
        beginTiming();

        bufferedLog.log("Performing state-based, batch-based routing on " + connection.toString(),
                RouterLog.Level.NORMAL);

        int lastState = -1;
        int state = 0;

        while (state < 7) {

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
                        batchSize = 8;
                    else
                        batchSize = 3;

                    bufferedLog.log("2: Calculating route default-templates (batch size: " + batchSize + ").",
                            RouterLog.Level.NORMAL);
                    boolean success = extendTemplateBatchesForBus(coreDesign, batchSize);
                    if (success)
                        nextState = state + 1;
                    else {
                        bufferedLog.indent();
                        bufferedLog.log("Unable to find working default-templates. Restarting at step 2.",
                                RouterLog.Level.NORMAL);
                        bufferedLog.indent(-1);
                    }
                    break;
                }
                case 3: {
                    bufferedLog.log("3: Using \"easing\" method to find best sink tile paths.", RouterLog.Level.NORMAL);
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
                case 4: {
                    bufferedLog.log("4: Calculating tile paths for default-templates.", RouterLog.Level.NORMAL);
                    populatePathSubs(coreDesign);
                    nextState = state + 1;
                    break;
                }
                case 5: {
                    bufferedLog.log("5: Performing route contention.", RouterLog.Level.NORMAL);
                    boolean success = routeContention(coreDesign);
                    if (!success) {
                        bufferedLog.indent();
                        bufferedLog.log("Live-lock detected during route contention. Restarting at step 2.",
                                RouterLog.Level.NORMAL);
                        bufferedLog.indent(-1);
                        nextState = 2;
                    }
                    else
                        nextState = state + 1;
                    break;
                }
                case 6:
                    bufferedLog.log("6: Compiling routing footprint.", RouterLog.Level.NORMAL);
                    addRoutesToFootprint(coreDesign);
                    nextState = state + 1;
                    break;
                default:
                    break;
            }

            lastState = state;
            state = nextState;

        }

        DesignRouter.completeRoutingJob(jobID, connection, footprint);

        finishTiming();

        bufferedLog.log("Routing success. Connection routed in " + getElapsedTime() + " ms.", RouterLog.Level.NORMAL);
        bufferedLog.log("Hop summary of connection:", RouterLog.Level.NORMAL);
        bufferedLog.indent();
        for (CustomRoute route : getRoutes())
            bufferedLog.log(route.getTemplate().hopSummary(), RouterLog.Level.NORMAL);
        bufferedLog.indent(-1);

    }
}
