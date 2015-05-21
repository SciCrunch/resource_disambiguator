package org.neuinfo.resource.disambiguator.services;

/**
* Created by bozyurt on 3/4/14.
*/
public class QueryCandidate {
    private final String candidate;
    private final String type;

    public final static String URL = "url";
    public final static String OTHER = "other";

    public QueryCandidate(String candidate, String type) {
        this.candidate = candidate;
        this.type = type;
    }

    public String getCandidate() {
        return candidate;
    }

    public String getType() {
        return type;
    }
}
