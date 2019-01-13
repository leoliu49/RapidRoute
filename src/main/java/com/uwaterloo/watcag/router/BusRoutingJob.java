package com.uwaterloo.watcag.router;

import com.uwaterloo.watcag.common.RegisterConnection;
import com.uwaterloo.watcag.config.RegisterComponent;
import com.uwaterloo.watcag.router.browser.FabricBrowser;
import com.uwaterloo.watcag.router.browser.JunctionsTracer;
import com.uwaterloo.watcag.router.elements.*;
import com.uwaterloo.watcag.util.RouterLog;
import com.xilinx.rapidwright.design.Design;

import java.util.*;
import java.util.concurrent.Callable;

public class BusRoutingJob implements Callable<ArrayList<CustomRoute>> {

    /*
     * Completely route an entire bus
     */

    private static final int SINK_TILE_TRAVERSAL_MAX_DEPTH = 8;

    private long tBegin;
    private long tEnd;

    private Design coreDesign;
    private RouterLog.BufferedLog bufferedLog;

    private ArrayList<EnterWireJunction> srcs;
    private ArrayList<ExitWireJunction> snks;
    private int bitWidth;

    private ArrayList<PriorityQueue<JunctionsTracer>> activeSearchQueues;
    private ArrayList<Set<String>> activeSearchFootprints;
    private ArrayList<Set<EnterWireJunction>> snkLeadIns;

    private ArrayList<ArrayList<RouteTemplate>> templatesCache;
    private ArrayList<HashMap<EnterWireJunction, ArrayList<TilePath>>> snkPathsCache;

    private ArrayList<RouteTemplate> templates;
    private ArrayList<CustomRoute> results;

    public BusRoutingJob(Design d, ArrayList<EnterWireJunction> srcs, ArrayList<ExitWireJunction> snks) {
        coreDesign = d;

        bufferedLog = RouterLog.newBufferedLog();

        this.srcs = srcs;
        this.snks = snks;
        bitWidth = srcs.size();

        activeSearchQueues = new ArrayList<>();
        activeSearchFootprints = new ArrayList<>();
        snkLeadIns = new ArrayList<>();
        templatesCache = new ArrayList<>();
        snkPathsCache = new ArrayList<>();
        templates = new ArrayList<>();
        results = new ArrayList<>();

        for (int i = 0; i < bitWidth; i++) {
            activeSearchQueues.add(new PriorityQueue<>(new RoutingCalculator.JunctionsTracerCostComparator()));
            activeSearchFootprints.add(new HashSet<>());
            snkLeadIns.add(new HashSet<>());
            templatesCache.add(new ArrayList<>());
            snkPathsCache.add(new HashMap<>());
            templates.add(null);
            results.add(null);
        }
    }

    public BusRoutingJob(Design d, RegisterConnection connection) {
        coreDesign = d;

        bufferedLog = RouterLog.newBufferedLog();

        srcs = new ArrayList<>();
        snks = new ArrayList<>();
        bitWidth = connection.getBitWidth();

        {
            int regBitIndex = 0;
            for (RegisterComponent component : connection.getSrcReg().getComponents()) {
                String intTileName = coreDesign.getDevice().getSite(component.getSiteName()).getIntTile().getName();
                for (int i = 0; i < component.getBitWidth(); i++) {
                    if (regBitIndex >= connection.getSrcRegLowestBit()
                            && regBitIndex <= connection.getSrcRegHighestBit()) {
                        srcs.add(EnterWireJunction.newSrcJunction(intTileName, component.getOutPIPName(i)));
                    }
                }
            }
        }
        {
            int regBitIndex = 0;
            for (RegisterComponent component : connection.getSnkReg().getComponents()) {
                String intTileName = coreDesign.getDevice().getSite(component.getSiteName()).getIntTile().getName();
                for (int i = 0; i < component.getBitWidth(); i++) {
                    if (regBitIndex >= connection.getSnkRegLowestBit()
                            && regBitIndex <= connection.getSnkRegHighestBit()) {
                        snks.add(ExitWireJunction.newSnkJunction(intTileName, component.getInPIPName(i)));
                    }
                }
            }
        }

        activeSearchQueues = new ArrayList<>();
        activeSearchFootprints = new ArrayList<>();
        snkLeadIns = new ArrayList<>();
        templatesCache = new ArrayList<>();
        snkPathsCache = new ArrayList<>();
        templates = new ArrayList<>();
        results = new ArrayList<>();

        for (int i = 0; i < bitWidth; i++) {
            activeSearchQueues.add(new PriorityQueue<>(new RoutingCalculator.JunctionsTracerCostComparator()));
            activeSearchFootprints.add(new HashSet<>());
            snkLeadIns.add(new HashSet<>());
            templatesCache.add(new ArrayList<>());
            snkPathsCache.add(new HashMap<>());
            templates.add(null);
            results.add(null);
        }
    }


    /*
     * Accessors and modifiers
     */
    private void beginTiming() {
        tBegin = System.currentTimeMillis();
    }

    private void finishTiming() {
        tEnd = System.currentTimeMillis();
    }

    public long getElapsedTime() {
        return tEnd - tBegin;
    }

    public void dumpLog() {
        bufferedLog.dumpLog();
    }

    private PriorityQueue<JunctionsTracer> getActiveSearchQueue(int bitIndex) {
        return activeSearchQueues.get(bitIndex);
    }

    private Set<String> getActiveSearchFootprint(int bitIndex) {
        return activeSearchFootprints.get(bitIndex);
    }

    private Set<EnterWireJunction> getLeadIns(int bitIndex) {
        if (snkLeadIns.get(bitIndex).isEmpty())
            snkLeadIns.set(bitIndex,
                    FabricBrowser.findReachableEntrances(coreDesign, SINK_TILE_TRAVERSAL_MAX_DEPTH, snks.get(bitIndex)));
        return snkLeadIns.get(bitIndex);
    }

    private ArrayList<TilePath> getSinkPaths(EnterWireJunction entrance, int bitIndex) {
        return snkPathsCache.get(bitIndex).get(entrance);
    }

    private void cacheSinkPaths(EnterWireJunction entrance, ArrayList<TilePath> paths, int bitIndex) {
        if (!snkPathsCache.get(bitIndex).containsKey(entrance))
            snkPathsCache.get(bitIndex).put(entrance, paths);
        else
            snkPathsCache.get(bitIndex).get(entrance).addAll(paths);
    }

    public ArrayList<CustomRoute> getResults() {
        return results;
    }



    private boolean findWorkingTemplates(int batchSize) {

        ArrayList<ArrayList<RouteTemplate>> newBatch = new ArrayList<>();
        for (int i = 0; i < bitWidth; i++) {
            TemplateSearchJob job = new TemplateSearchJob(coreDesign, srcs.get(i), snks.get(i));
            job.setBatchSize(batchSize);
            job.setSearchQueue(getActiveSearchQueue(i));
            job.setSearchFootprint(getActiveSearchFootprint(i));
            job.setLeadIns(getLeadIns(i));
            job.run();

            newBatch.add(job.getResults());
            templatesCache.get(i).addAll(job.getResults());
        }

        for (int i = 0; i < bitWidth; i++) {
            ArrayList<EnterWireJunction> newSinkEntrances = new ArrayList<>();
            for (RouteTemplate template : newBatch.get(i))
                newSinkEntrances.add((EnterWireJunction) template.getTemplate(-2));

            ArrayList<ArrayList<TilePath>> newSinkPaths = FabricBrowser.ditherTilePathsFromExit(coreDesign,
                    SINK_TILE_TRAVERSAL_MAX_DEPTH, newSinkEntrances, snks.get(i));
            for (int j = 0; j < newSinkPaths.size(); j++) {
                cacheSinkPaths(newSinkEntrances.get(j), newSinkPaths.get(j), i);
            }
        }

        ArrayList<RouteTemplate> validTemplates = new ArrayList<>();

        // Highest cost possible
        int threshMax = 0;
        // Lost cost possible (max of min's across each bit)
        int threshMin = 0;

        for (int i = 0; i < bitWidth; i++) {
            ArrayList<RouteTemplate> candidates = templatesCache.get(i);
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
        for (int i = 0; i < bitWidth; i++) {
            HashSet<TilePath> pathChoices = new HashSet<>();
            for (RouteTemplate template : templatesCache.get(i)) {
                if (template.getEstimatedCost() < threshMin) {
                    pathChoices.addAll(getSinkPaths((EnterWireJunction) template.getTemplate(-2), i));
                }
            }
            tilePathsPool.add(pathChoices);
        }

        // Iterating up to the highest cost, increase the pool each time until a configuration can be found
        for (int threshold = threshMin; threshold <= threshMax; threshold++) {
            ArrayList<HashSet<TilePath>> newCandidates = new ArrayList<>();
            for (int i = 0; i < bitWidth; i++)
                newCandidates.add(new HashSet<>());

            int additionsToCandidatePool = 0;
            for (int i = 0; i < bitWidth; i++) {
                ArrayList<RouteTemplate> candidates = templatesCache.get(i);
                for (RouteTemplate candidate : candidates) {
                    if (candidate.getEstimatedCost() == threshold) {
                        newCandidates.get(i).addAll(getSinkPaths((EnterWireJunction) candidate.getTemplate(-2), i));

                        additionsToCandidatePool += 1;
                    }
                }
            }

            // If nothing new was added to the candidate pool this round, simply move on to the next threshold
            if (additionsToCandidatePool == 0)
                continue;

            for (int i = 0; i < bitWidth; i++) {
                if (newCandidates.get(i).isEmpty())
                    continue;

                ArrayList<HashSet<TilePath>> candidates = new ArrayList<>(tilePathsPool);
                candidates.set(i, newCandidates.get(i));

                ArrayList<TilePath> results = RoutingCalculator.deriveValidTilePaths(candidates);

                if (results != null) {
                    for (int j = 0; j < bitWidth; j++) {
                        for (RouteTemplate template : templatesCache.get(j)) {
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
            for (int i = 0; i < bitWidth; i++) {
                tilePathsPool.get(i).addAll(newCandidates.get(i));
            }
        }

        // Failure condition: no valid templates found
        if (validTemplates.isEmpty())
            return false;

        for (int i = 0; i < bitWidth; i++)
            templates.set(i, validTemplates.get(i));

        return true;
    }

    private void populateRoutes() {
        ArrayList<ArrayList<TilePath>> allRoutes = new ArrayList<>();
        ArrayList<Integer> bitArray = new ArrayList<>();
        ArrayList<Integer> junctionIndexes = new ArrayList<>();
        HashSet<String> committedNodes = new HashSet<>();
        ArrayList<HashSet<String>> banList = new ArrayList<>();
        for (int i = 0; i < bitWidth; i++) {
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
                    || (!previousHop.isSrc() && committedNodes.contains(previousHop.getSrcJunction(coreDesign).getNodeName()))) {
                // Conflict found in hops
                isValid = false;

                banList.get(bitIndex).add(previousHop.getNodeName());
                banList.get(bitIndex).add(previousHop.getSrcJunction(coreDesign).getNodeName());
            }
            else {
                TilePath path = FabricBrowser.findClosestTilePath(coreDesign, previousHop, thisHop, committedNodes);

                if (path == null) {
                    // Conflict / un-routable found in tile paths
                    isValid = false;

                    banList.get(bitIndex).add(previousHop.getNodeName());
                    if (!previousHop.isSrc())
                        banList.get(bitIndex).add(previousHop.getSrcJunction(coreDesign).getNodeName());
                }
                else {
                    // No conflict
                    isValid = true;
                    allRoutes.get(bitIndex).add(0, path);

                    committedNodes.addAll(path.getNodePath());

                    if (!previousHop.isSrc())
                        committedNodes.add(previousHop.getSrcJunction(coreDesign).getNodeName());
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

                TemplateSearchJob job = new TemplateSearchJob(coreDesign, srcs.get(bitIndex), detourSnk);
                job.setBatchSize(1);
                job.setBanList(nodesToAvoid);

                Set<EnterWireJunction> leadIns = new HashSet<>();
                for (EnterWireJunction junction : FabricBrowser.findReachableEntrances(coreDesign, detourSnk)) {
                    if (!nodesToAvoid.contains(junction.getNodeName()))
                        leadIns.add(junction);
                }
                job.setLeadIns(leadIns);

                job.run();
                templates.get(bitIndex).replaceTemplate(job.getSrc(), job.getSnk(), job.getResults().get(0));
            }
            else {
                junctionIndexes.set(bitIndex, junctionIndex + 2);
                if (junctionIndexes.get(bitIndex) >= templates.get(bitIndex).getTemplate().size())
                    bitArray.remove(Integer.valueOf(bitIndex));
            }

        }

        for (int i = 0; i < bitWidth; i++) {
            results.set(i, new CustomRoute(templates.get(i)));
            results.get(i).setRoute(allRoutes.get(i));

            // For sink path, later on
            results.get(i).getRoute().add(null);
        }
    }

    private void adjustSinkPaths() {

        ArrayList<ArrayList<TilePath>> allEndPathChoices = new ArrayList<>();
        for (int i = 0; i < bitWidth; i++) {
            allEndPathChoices.add(getSinkPaths((EnterWireJunction) templates.get(i).getTemplate(-2), i));
        }

        ArrayList<TilePath> endPaths = RoutingCalculator.deriveBestTilePathConfiguration(allEndPathChoices);
        for (int i = 0; i < bitWidth; i++) {
            results.get(i).setPath(-1, endPaths.get(i));
        }
    }

    private void compileRoutes() {
        for (int i = 0; i < bitWidth; i++) {
            results.get(i).setRouteIndex(i);
        }
    }

    @Override
    public ArrayList<CustomRoute> call() {
        beginTiming();

        int lastState = -1;
        int state = 0;

        while (state < 4) {
            int nextState = -1;

            switch (state) {
                case 0: {
                    int batchSize;
                    if (lastState == -1)
                        batchSize = bitWidth;
                    else
                        batchSize = 3;

                    bufferedLog.log("0: Finding working templates and route hops (batch size: " + batchSize + ").",
                            RouterLog.Level.NORMAL);
                    long tB = System.currentTimeMillis();
                    bufferedLog.indent();

                    boolean success = findWorkingTemplates(batchSize);
                    if (success)
                        nextState = state + 1;
                    else
                        nextState = 0;

                    bufferedLog.log("Templates found in " + (System.currentTimeMillis() - tB) + " ms.",
                            RouterLog.Level.NORMAL);
                    bufferedLog.indent(-1);
                    break;
                }
                case 1: {
                    bufferedLog.log("1: Populating routes.", RouterLog.Level.NORMAL);
                    long tB = System.currentTimeMillis();
                    bufferedLog.indent();

                    populateRoutes();
                    nextState = state + 1;

                    bufferedLog.log("Routes populated in " + (System.currentTimeMillis() - tB) + " ms.",
                            RouterLog.Level.NORMAL);
                    bufferedLog.indent(-1);
                    break;
                }
                case 2: {
                    bufferedLog.log("2: Adjusting sink paths.", RouterLog.Level.NORMAL);
                    long tB = System.currentTimeMillis();
                    bufferedLog.indent();

                    adjustSinkPaths();
                    nextState = state + 1;

                    bufferedLog.log("Sink paths adjusted in " + (System.currentTimeMillis() - tB) + " ms.",
                            RouterLog.Level.NORMAL);
                    bufferedLog.indent(-1);
                    break;
                }
                case 3: {
                    bufferedLog.log("3: Compiling routes.", RouterLog.Level.NORMAL);
                    long tB = System.currentTimeMillis();
                    bufferedLog.indent();

                    compileRoutes();
                    nextState = state + 1;

                    bufferedLog.log("Routes compiled in " + (System.currentTimeMillis() - tB) + " ms.",
                            RouterLog.Level.NORMAL);
                    bufferedLog.indent(-1);
                    break;
                }
            }

            lastState = state;
            state = nextState;

        }

        finishTiming();

        bufferedLog.log("Bus routed in " + getElapsedTime() + " ms.", RouterLog.Level.NORMAL);
        bufferedLog.dumpLog();

        return results;
    }
}
