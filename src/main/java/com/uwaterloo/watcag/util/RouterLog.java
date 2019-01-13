package com.uwaterloo.watcag.util;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.commons.lang3.tuple.Triple;

import java.util.HashMap;
import java.util.LinkedList;

public class RouterLog {

    public enum Level {
        VERBOSE, INFO, NORMAL, WARNING
    }

    private static HashMap<Integer, LinkedList<String>> prefixMap = null;
    private static Level logLevel = Level.VERBOSE;

    private static String indentString = "    ";

    public static void init(Level level) {
        logLevel = level;

        prefixMap = new HashMap<>();

        for (Level l : Level.values()) {
            LinkedList<String> prefix = new LinkedList<>();
            prefix.addLast(l.toString());
            prefix.addLast("\t");
            prefixMap.put(l.ordinal(), prefix);
        }
    }

    public static void log(String msg, Level level) {
        if (prefixMap == null)
            init(level);

        if (level.ordinal() >= logLevel.ordinal()) {
            for (String s : prefixMap.get(level.ordinal()))
                System.out.print(s);
            System.out.println(msg);
        }
    }

    public static void error(String msg) {
        msg = msg.replace("\n", "\nERROR\t");
        System.out.println("ERROR\t" + msg);
    }

    public static void debug(String msg) {
        msg = msg.replace("\n", "\nDEBUG\t");
        System.out.println("DEBUG\t" + msg);
    }

    public static void setLevel(Level level) {
        RouterLog.logLevel = level;
    }

    public static void indent() {
        for (int key : prefixMap.keySet()) {
            prefixMap.get(key).addLast(indentString);
        }
    }

    public static void indent(int delta) {
        if (delta > 0) {
            for (int i = 0; i < delta; i++) {
                for (int key : prefixMap.keySet())
                    prefixMap.get(key).addLast(indentString);
            }
        }
        else if (delta < 0) {
            for (int i = 0; i > delta; i--) {
                for (int key : prefixMap.keySet())
                    prefixMap.get(key).removeLast();
            }
        }
    }


    public static class BufferedLog {
        private HashMap<Integer, LinkedList<String>> internalPrefixMap;
        private LinkedList<String> buffer;

        public BufferedLog() {
            internalPrefixMap = new HashMap<>();
            buffer = new LinkedList<>();
            for (Level l : Level.values()) {
                LinkedList<String> prefix = new LinkedList<>();
                prefix.addLast(l.toString());
                prefix.addLast("\t");
                internalPrefixMap.put(l.ordinal(), prefix);
            }
        }

        public void indent() {
            for (int key : internalPrefixMap.keySet()) {
                internalPrefixMap.get(key).addLast(indentString);
            }
        }

        public void indent(int delta) {
            if (delta > 0) {
                for (int i = 0; i < delta; i++) {
                    for (int key : internalPrefixMap.keySet())
                        internalPrefixMap.get(key).addLast(indentString);
                }
            }
            else if (delta < 0) {
                for (int i = 0; i > delta; i--) {
                    for (int key : internalPrefixMap.keySet())
                        internalPrefixMap.get(key).removeLast();
                }
            }
        }

        public void log(String msg, Level level) {
            if (level.ordinal() >= logLevel.ordinal()) {
                StringBuilder b = new StringBuilder();
                for (String s : internalPrefixMap.get(level.ordinal()))
                    b.append(s);
                b.append(msg);

                buffer.addLast(b.toString());
            }
        }

        public void dumpLog() {
            System.out.println("Log dump:");
            for (String s : buffer)
                System.out.println(s);
        }
    }

    public static BufferedLog newBufferedLog() {
        return new BufferedLog();
    }

}
