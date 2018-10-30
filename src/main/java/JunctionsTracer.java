public class JunctionsTracer {

    private int depth;

    private JunctionsTracer parent;
    private WireJunction junction;

    public JunctionsTracer(WireJunction junction, JunctionsTracer parent, int depth) {
        this.junction = junction;
        this.parent = parent;
        this.depth = depth;
    }

    public JunctionsTracer(WireJunction junction, int depth) {
        this.junction = junction;
        this.parent = null;
        this.depth = depth;
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
}
