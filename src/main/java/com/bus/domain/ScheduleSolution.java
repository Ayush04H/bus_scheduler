package com.bus.domain;

import java.util.ArrayList;
import java.util.List;

public class ScheduleSolution {
    private List<RouteRun> assignedRouteRuns;
    // We can add scoring details here later, e.g.:
    // private int score;
    // private String scoreExplanation;
    // private int unassignedRuns;

    public ScheduleSolution() {
        this.assignedRouteRuns = new ArrayList<>();
    }

    public ScheduleSolution(List<RouteRun> assignedRouteRuns) {
        this.assignedRouteRuns = assignedRouteRuns;
    }

    public List<RouteRun> getAssignedRouteRuns() {
        return assignedRouteRuns;
    }

    public void setAssignedRouteRuns(List<RouteRun> assignedRouteRuns) {
        this.assignedRouteRuns = assignedRouteRuns;
    }

    // Add methods to add single assignments, calculate score, etc. later

    @Override
    public String toString() {
        return "ScheduleSolution{" +
               "assignedRouteRuns=" + (assignedRouteRuns == null ? "null" : assignedRouteRuns.size() + " runs") +
               '}';
    }
}