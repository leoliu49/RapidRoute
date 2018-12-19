import org.apache.commons.lang3.tuple.ImmutablePair;

import java.util.HashMap;
import java.util.LinkedList;

public class RouterLog {

    public enum Level {
        VERBOSE, INFO, NORMAL, WARNING, ERROR
    }

    private static HashMap<Integer, String> prefixMap = null;
    private static int indentLevel = 0;
    private static Level logLevel = Level.VERBOSE;

    private static String indentString = "    ";

    public static void init(Level level) {
        logLevel = level;

        prefixMap = new HashMap<Integer, String>();
        prefixMap.put(Level.VERBOSE.ordinal(), "VERBOSE\t");
        prefixMap.put(Level.INFO.ordinal(), "INFO\t");
        prefixMap.put(Level.NORMAL.ordinal(), "NORMAL\t");
        prefixMap.put(Level.WARNING.ordinal(), "WARNING\t");
        prefixMap.put(Level.ERROR.ordinal(), "ERROR\t");
    }

    public static void log(String msg, Level level) {
        if (prefixMap == null)
            init(level);
        if (level.ordinal() >= logLevel.ordinal()) {
            msg = msg.replace("\n", "\n" + prefixMap.get(level.ordinal()));
            System.out.println(prefixMap.get(level.ordinal()) + msg);
        }
    }

    public static void debug(String msg) {
        msg = msg.replace("\n", "\nDEBUG\t");
        System.out.println("DEBUG\t" + msg);
    }

    public static void setLevel(Level level) {
        RouterLog.logLevel = level;
    }

    public static void indent() {
        indentLevel += 1;
        for (int key : prefixMap.keySet()) {
            prefixMap.replace(key, prefixMap.get(key) + indentString);
        }
    }

    public static void indent(int delta) {
        indentLevel += delta;
        String indents = "";
        for (int i = 0; i < indentLevel; i++)
            indents += indentString;

        prefixMap.put(Level.VERBOSE.ordinal(), "VERBOSE\t" + indents);
        prefixMap.put(Level.INFO.ordinal(), "INFO\t" + indents);
        prefixMap.put(Level.NORMAL.ordinal(), "NORMAL\t" + indents);
        prefixMap.put(Level.WARNING.ordinal(), "WARNING\t" + indents);
        prefixMap.put(Level.ERROR.ordinal(), "ERROR\t" + indents);
    }


    public static class BufferedLog {
        private int indentLevel = 0;
        private LinkedList<ImmutablePair<Level, String>> buffer = new LinkedList<>();

        public void indent() {
            indentLevel += 1;
        }

        public void indent(int delta) {
            indentLevel += delta;
        }

        public void log(String msg, Level level) {
            if (level.ordinal() >= logLevel.ordinal()) {
                String prefixIndent = "";
                for (int i = 0; i < indentLevel; i++)
                    prefixIndent += "\t";

                for (String s : msg.split("\n"))
                    buffer.addLast(new ImmutablePair<>(level, prefixIndent + s));
            }
        }

        public void dumpLog() {
            RouterLog.log("Log dump:", Level.NORMAL);
            for (ImmutablePair<Level, String> msg : buffer) {
                RouterLog.log(msg.getRight(), msg.getLeft());
            }
        }
    }

    public static BufferedLog newBufferedLog() {
        return new BufferedLog();
    }

}
