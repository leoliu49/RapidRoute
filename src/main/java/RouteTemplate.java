import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.device.Tile;

import java.util.*;

public class RouteTemplate {

    /*
     * Wrapper around an ArrayList of WireJunctions, which describe the hops needed to complete a route
     */

    public static class RouteTemplateCostComparator implements Comparator<RouteTemplate> {

        @Override
        public int compare(RouteTemplate o1, RouteTemplate o2) {
            return o1.getCost() - o2.getCost();
        }
    }

    public static class RouteTemplateBitIndexComparator implements Comparator<RouteTemplate> {

        @Override
        public int compare(RouteTemplate o1, RouteTemplate o2) {
            return o1.getBitIndex() - o2.getBitIndex();
        }
    }

    private int cost;

    // Relative bit index of connection
    private int bitIndex;

    private EnterWireJunction src;
    private ExitWireJunction snk;

    private int distanceX;
    private int distanceY;

    private ArrayList<WireJunction> template;

    public RouteTemplate(Design d, EnterWireJunction src, ExitWireJunction snk) {
        cost = 0;

        this.src = src;
        this.snk = snk;

        Tile srcIntTile = d.getDevice().getTile(src.getTileName());
        Tile snkIntTile = d.getDevice().getTile(snk.getTileName());

        distanceX = snkIntTile.getTileXCoordinate() - srcIntTile.getTileXCoordinate();
        distanceY = snkIntTile.getTileYCoordinate() - srcIntTile.getTileYCoordinate();

        template = new ArrayList<>();
        template.add(src);
        template.add(snk);
    }

    public int getCost() {
        return cost;
    }

    public int getBitIndex() {
        return bitIndex;
    }

    public void setBitIndex(int bitIndex) {
        this.bitIndex = bitIndex;
    }

    public EnterWireJunction getSrc() {
        return src;
    }

    public ExitWireJunction getSnk() {
        return snk;
    }

    public int getDistanceX() {
        return distanceX;
    }

    public int getDistanceY() {
        return distanceY;
    }

    public ArrayList<WireJunction> getTemplate() {
        return template;
    }

    public WireJunction getTemplate(int i) {
        if (i < 0)
            i += template.size();
        return template.get(i);
    }

    public boolean isEmpty() {
        return template.isEmpty();
    }

    public void addExitWireJunction(Design d, ExitWireJunction exJunc) {
        template.add(1, exJunc.getDestJunction(d));
        template.add(1, exJunc);
        cost += 1;
    }

    public void pushExitWireJunction(Design d, ExitWireJunction exJunc) {
        template.add(template.size() - 1, exJunc);
        template.add(template.size() - 1, exJunc.getDestJunction(d));
        cost += 1;
    }

    @Override
    public String toString() {
        String repr = "";
        for (int i = 0; i < template.size() - 1; i++)
            repr += template.get(i).toString() + " --> ";
        repr += template.get(template.size() - 1);
        return repr;
    }

    public Set<String> getUsage() {
        Set<String> usage = new HashSet<>();
        for (WireJunction junction : template)
            usage.add(junction.getNodeName());
        return usage;
    }

    public String hopSummary() {
        String repr = " [ ";
        for (int i = 1; i < template.size() - 1; i += 2) {
            repr += RouteUtil.directionToString(template.get(i).getDirection());
            repr += template.get(i).getWireLength();
            repr += " ";
        }
        repr += "]";
        return repr;
    }
}
