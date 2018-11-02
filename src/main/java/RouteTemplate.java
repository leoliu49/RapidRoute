import com.xilinx.rapidwright.design.Design;
import com.xilinx.rapidwright.device.Tile;

import java.util.*;

public class RouteTemplate {

    /*
     * Wrapper around an ArrayList of WireJunctions, which describe the hops needed to complete a route
     */

    // True for all Ultrascale+; 16 for Ultrascale
    private static final int LONG_LINE_LENGTH = 12;

    // Unique score documenting how 'expensive' these hops are:
    // 1. Parallel redirection (i.e. E <--> W, N <--> S): +2
    // 2. Orthogonal redirection (i.e. E/W <--> N/S): +6
    // 3. Excessive use of small hops (i.e. for each usage of small hops): +2
    private int adjustedCost;
    private WireDirection lastDirection;

    // Relative bit index of connection
    private int bitIndex;

    private EnterWireJunction src;
    private ExitWireJunction snk;

    private int distanceX;
    private int distanceY;

    // Inclusive of src and snk
    private ArrayList<WireJunction> template;

    public RouteTemplate(Design d, EnterWireJunction src, ExitWireJunction snk) {
        adjustedCost = 0;
        lastDirection = null;

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

    // Gets cost factoring readjustment score
    public int getAdjustedCost() {
        return adjustedCost;
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

    public void pushEnterWireJunction(Design d, EnterWireJunction enJunc) {
        template.add(1, enJunc);
        template.add(1, enJunc.getSrcJunction(d));
        adjustedCost += 2;

        if (enJunc.getWireLength() < 12)
            adjustedCost += 2;

        if (enJunc.getDirection().equals(lastDirection))
            return;
        if (RouteUtil.isParallel(enJunc.getDirection(), lastDirection))
            adjustedCost += 2;
        else if (RouteUtil.isOrthogonal(enJunc.getDirection(), lastDirection))
            adjustedCost += 6;

        lastDirection = enJunc.getDirection();
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
        String repr = "<";
        for (int i = 1; i < template.size() - 3; i += 2) {
            repr += RouteUtil.directionToString(template.get(i).getDirection());
            repr += template.get(i).getWireLength();
            repr += " ";
        }
        repr += RouteUtil.directionToString(template.get(template.size() - 2).getDirection());
        repr += template.get(template.size() - 2).getWireLength();
        repr += ">[" + adjustedCost + "]";
        return repr;
    }
}
