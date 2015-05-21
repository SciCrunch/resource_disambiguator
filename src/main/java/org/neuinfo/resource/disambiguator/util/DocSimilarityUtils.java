package org.neuinfo.resource.disambiguator.util;

import java.util.HashSet;
import java.util.Set;

/**
 * Created by bozyurt on 1/2/14.
 */
public class DocSimilarityUtils {

    private DocSimilarityUtils() {
    }

    public static Set<String> prepShingles(String text, int shingleSize) {
        Set<String> shinglingSet = new HashSet<String>();
        text = text.toLowerCase();
        String[] tokens = text.split("\\s+");
        StringBuilder sb = new StringBuilder(300);
        for (int i = 0; i < (tokens.length - shingleSize + 1); i++) {
            sb.setLength(0);
            for (int j = i; j < i + shingleSize; j++) {
                sb.append(tokens[j]).append('|');
            }
            shinglingSet.add(sb.toString());
        }
        return shinglingSet;
    }

    public static double calcJaccardIndex(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        int unionCount;
        int intersectionCount = getIntersectionCount(a, b);
        unionCount = a.size() + b.size() - intersectionCount;

        return intersectionCount / (double) unionCount;
    }

    public static double calcContainment(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        int intersectionCount = getIntersectionCount(a, b);
        return intersectionCount / (double) a.size();
    }

    private static int getIntersectionCount(Set<String> a, Set<String> b) {
        int intersectionCount = 0;
        if (a.size() < b.size()) {
            for (String key : a) {
                if (b.contains(key)) {
                    intersectionCount++;
                }
            }
        } else {
            for (String key : b) {
                if (a.contains(key)) {
                    intersectionCount++;
                }
            }
        }
        return intersectionCount;
    }
}
