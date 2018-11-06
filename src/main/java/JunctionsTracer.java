import java.util.ArrayList;
import java.util.HashSet;

public class JunctionsTracer {

    private int depth;

    private JunctionsTracer parent;
    private WireJunction junction;

    private HashSet<WireDirection> deviationDirections;

    public JunctionsTracer(WireJunction junction, JunctionsTracer parent, int depth) {
        this.junction = junction;
        this.parent = parent;
        this.depth = depth;

        deviationDirections = new HashSet<>(parent.getDeviationDirections());
    }

    public JunctionsTracer(WireJunction junction, int depth) {
        this.junction = junction;
        this.parent = null;
        this.depth = depth;

        deviationDirections = new HashSet<>();
    }

    public int getDepth() {
        return depth;
    }

    public JunctionsTracer getParent() {
        return parent;
    }

    public WireJunction getJunction() {
        return junction;
    }

    public HashSet<WireDirection> getDeviationDirections() {
        return deviationDirections;
    }

    public boolean isAlignedWithDeviationDirection(WireDirection dir) {
        return deviationDirections.contains(dir);
    }

    public void addDeviation(WireDirection dir) {
        deviationDirections.add(dir);
    }
}
