package com.bus.domain;

import java.util.ArrayList;
import java.util.List;

public class ScheduleSolution {
    private List<RouteRun> assignedRouteRuns; // This list should contain ALL runs, with assignments populated
    private int score;                     // NEW for Step 11
    private String scoreExplanation;       // NEW for Step 11
    private int unassignedRunCount;        // NEW for Step 11
    private int totalBusesUsedCount;       // NEW for Step 11
    // We could add totalBusIdleTimeMinutes, totalBusDrivingTimeMinutes later

    public ScheduleSolution() {
        this.assignedRouteRuns = new ArrayList<>();
        this.score = 0; // Default score
        this.scoreExplanation = "Not yet scored.";
        this.unassignedRunCount = 0;
        this.totalBusesUsedCount = 0;
    }

    // Constructor that takes the list of all processed runs
    public ScheduleSolution(List<RouteRun> processedRuns) {
        this.assignedRouteRuns = processedRuns; // Should be all runs, some assigned, some not
        this.score = 0;
        this.scoreExplanation = "Not yet scored.";
        this.unassignedRunCount = 0; // Will be calculated
        this.totalBusesUsedCount = 0; // Will be calculated
    }

    public List<RouteRun> getAssignedRouteRuns() {
        return assignedRouteRuns;
    }

    public void setAssignedRouteRuns(List<RouteRun> assignedRouteRuns) {
        this.assignedRouteRuns = assignedRouteRuns;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getScoreExplanation() {
        return scoreExplanation;
    }

    public void setScoreExplanation(String scoreExplanation) {
        this.scoreExplanation = scoreExplanation;
    }

    public int getUnassignedRunCount() {
        return unassignedRunCount;
    }

    public void setUnassignedRunCount(int unassignedRunCount) {
        this.unassignedRunCount = unassignedRunCount;
    }

    public int getTotalBusesUsedCount() {
        return totalBusesUsedCount;
    }

    public void setTotalBusesUsedCount(int totalBusesUsedCount) {
        this.totalBusesUsedCount = totalBusesUsedCount;
    }

    @Override
    public String toString() {
        return "ScheduleSolution{" +
               "assignedRouteRuns=" + (assignedRouteRuns == null ? "null" : assignedRouteRuns.size() + " runs processed") +
               ", score=" + score +
               ", unassignedRunCount=" + unassignedRunCount +
               ", totalBusesUsedCount=" + totalBusesUsedCount +
               ", scoreExplanation='" + scoreExplanation + '\'' +
               '}';
    }
}