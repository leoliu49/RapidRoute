import java.util.*;

import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.design.Net;
import com.xilinx.rapidwright.device.PIP;
import com.xilinx.rapidwright.edif.EDIFNetlist;
import com.xilinx.rapidwright.edif.EDIFTools;

public class RouteForge {

    /*
     * Collection of static functions which can route registers
     */

    private static Set<String> nodeLock = new HashSet<>();

    public static void flushNodeLock() {
        nodeLock.clear();
    }

    public static boolean lock(String nodeName) {
        if (nodeLock.contains(nodeName))
            return false;
        nodeLock.add(nodeName);
        return true;
    }

    public static boolean isLocked(String nodeName) {
        return nodeLock.contains(nodeName) || globalNodeFootprint.contains(nodeName);
    }

    public static void unlock(String nodeName) {
        nodeLock.remove(nodeName);
    }

    // ArrayList of all used nodes
    public static Set<String> globalNodeFootprint = new HashSet<>();

    static {
        FabricBrowser.setGlobalNodeFootprint(globalNodeFootprint);
    }

    public static void sanitizeNets(Design d) {
        EDIFNetlist n = d.getNetlist();
        Map<String, String> parentNetMap = n.getParentNetMap();
        for (Net net : new ArrayList<>(d.getNets())) {
            if (net.getPins().size() > 0 && net.getSource() == null) {
                if (net.isStaticNet())
                    continue;
                String parentNet = parentNetMap.get(net.getName());
                if (parentNet.equals(EDIFTools.LOGICAL_VCC_NET_NAME)) {
                    d.movePinsToNewNetDeleteOldNet(net, d.getVccNet(), true);
                    continue;
                }
                else if (parentNet.equals(EDIFTools.LOGICAL_GND_NET_NAME)) {
                    d.movePinsToNewNetDeleteOldNet(net, d.getGndNet(), true);
                    continue;
                }
                Net parent = d.getNet(parentNet);
                if (parent == null) {
                    continue;
                }
                d.movePinsToNewNetDeleteOldNet(net, parent, true);

            }
        }
    }


    public static void findAndRoute(Design d, Net n, String tileName, String startNodeName, String endNodeName) {
        for (PIP pip : d.getDevice().getTile(tileName).getPIPs()) {
            if (RouteUtil.getPIPNodeName(tileName, pip.getStartWireName()).equals(startNodeName)
                    && (RouteUtil.getPIPNodeName(tileName, pip.getEndWireName())).equals(endNodeName)) {
                RouterLog.log("Junction <" + startNodeName + "> ---> <" + endNodeName + ">", RouterLog.Level.INFO);
                n.addPIP(pip);
                return;
            }
        }
        globalNodeFootprint.add(startNodeName);
        globalNodeFootprint.add(endNodeName);
        RouterLog.log("Junction <" + startNodeName + "> ---> <" + endNodeName + "> failed.", RouterLog.Level.ERROR);
    }
}
