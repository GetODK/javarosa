package org.javarosa.measure;

import java.util.HashMap;
import java.util.Map;

public class Measure {

    private static final Map<String, Integer> counts = new HashMap<>();
    private static boolean measuring;

    private Measure() {

    }

    public static int withMeasure(String event, Runnable work) {
        start();
        work.run();
        int count = getCount(event);
        start();

        return count;
    }

    public static void log(String event) {
        if (!measuring) return;

        if (!counts.containsKey(event)) {
            counts.put(event, 0);
        }

        counts.put(event, counts.get(event) + 1);
    }

    private static void start() {
        counts.clear();
        measuring = true;
    }

    private static void stop() {
        counts.clear();
        measuring = false;
    }

    private static int getCount(String event) {
        return counts.get(event);
    }
}
