package com.bus.domain;

import java.util.ArrayList;
import java.util.List;

public class ScheduleSolution {
    private List<RouteRun> assignedRouteRuns; // This list should contain ALL runs, with assignments populated
    private int score;
    private String scoreExplanation;
    private int unassignedRunCount;
    private int totalBusesUsedCount;
    private List<ActivityLogEntry> activityLog; // NEW: For detailed activity timeline

    public ScheduleSolution() {
        this.assignedRouteRuns = new ArrayList<>();
        this.activityLog = new ArrayList<>(); // Initialize
        this.score = 0;
        this.scoreExplanation = "Not yet scored.";
        this.unassignedRunCount = 0;
        this.totalBusesUsedCount = 0;
    }

    public ScheduleSolution(List<RouteRun> processedRuns) {
        this.assignedRouteRuns = processedRuns;
        this.activityLog = new ArrayList<>(); // Initialize
        this.score = 0;
        this.scoreExplanation = "Not yet scored.";
        // unassignedRunCount and totalBusesUsedCount will be set by calculateScore
    }

    // Getters and Setters for existing fields
    public List<RouteRun> getAssignedRouteRuns() { return assignedRouteRuns; }
    public void setAssignedRouteRuns(List<RouteRun> assignedRouteRuns) { this.assignedRouteRuns = assignedRouteRuns; }

    public int getScore() { return score; }
    public void setScore(int score) { this.score = score; }

    public String getScoreExplanation() { return scoreExplanation; }
    public void setScoreExplanation(String scoreExplanation) { this.scoreExplanation = scoreExplanation; }

    public int getUnassignedRunCount() { return unassignedRunCount; }
    public void setUnassignedRunCount(int unassignedRunCount) { this.unassignedRunCount = unassignedRunCount; }

    public int getTotalBusesUsedCount() { return totalBusesUsedCount; }
    public void setTotalBusesUsedCount(int totalBusesUsedCount) { this.totalBusesUsedCount = totalBusesUsedCount; }

    // Getter and Setter for activityLog (NEW)
    public List<ActivityLogEntry> getActivityLog() { return activityLog; }
    public void setActivityLog(List<ActivityLogEntry> activityLog) { this.activityLog = activityLog; }

    // Convenience method to add a single log entry
    public void addActivityLogEntry(ActivityLogEntry entry) {
        if (this.activityLog == null) {
            this.activityLog = new ArrayList<>();
        }
        this.activityLog.add(entry);
    }

    @Override
    public String toString() {
        return "ScheduleSolution{" +
               "assignedRouteRunsSize=" + (assignedRouteRuns == null ? "null" : assignedRouteRuns.size()) +
               ", score=" + score +
               ", unassignedRunCount=" + unassignedRunCount +
               ", totalBusesUsedCount=" + totalBusesUsedCount +
               ", activityLogSize=" + (activityLog == null ? 0 : activityLog.size()) + // Added for quick check
               ", scoreExplanation='" + scoreExplanation + '\'' +
               '}';
    }
}