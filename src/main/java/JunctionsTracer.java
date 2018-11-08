import java.util.*;

public class JunctionsTracer {

    private int depth;

    private LinkedList<WireJunction> junctions;

    private JunctionsTracer(WireJunction head) {
        depth = 0;
        junctions = new LinkedList<>();
        junctions.addFirst(head);
    }

    /*
     * Deep copies reference, and appends next as head
     */
    public JunctionsTracer(WireJunction next, JunctionsTracer ref) {
        depth += 1;
        junctions = new LinkedList<>(ref.getJunctions());
        junctions.addFirst(next);
    }

    public int getDepth() {
        return depth;
    }

    public LinkedList<WireJunction> getJunctions() {
        return junctions;
    }

    public void fastForward(WireJunction next) {
        depth += 1;
        junctions.addFirst(next);
    }

    public WireJunction getHead() {
        return junctions.getFirst();
    }

    public String toString() {
        return junctions.getFirst().toString();
    }

    public static JunctionsTracer newHeadTracer(WireJunction head) {
        return new JunctionsTracer(head);
    }
}
