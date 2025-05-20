package com.bus.domain;

import java.time.LocalTime;
import java.util.Objects;

public class ActivityLogEntry {

    public enum EntityType { // Corrected: enum, not class
        BUS, DRIVER
    }

    public enum ActivityType {
        ROUTE_SERVICE,            // Performing a scheduled RouteRun
        TRAVEL_TO_START_TERMINAL, // Bus travelling from depot to first terminal, or driver between terminals
        TRAVEL_TO_DEPOT,          // Bus travelling to depot (e.g., for charging or end of day)
        CHARGING,                 // Bus charging at depot
        MANDATORY_BREAK,          // Driver taking a mandatory break
        WAITING_IDLE              // Could be used if explicitly logging idle time
        // SHIFT_START, SHIFT_END // Optional future enhancements
    }

    private EntityType entityType;
    private String entityId; // Bus ID or Driver ID
    private ActivityType activityType;
    private LocalTime startTime;
    private LocalTime endTime;
    private String startLocationId;
    private String endLocationId;
    private String description;     // e.g., "Route R1-0800", "Charging at D1", "Break at T1"
    private Integer startChargeKm;  // For Bus
    private Integer endChargeKm;    // For Bus

    // Constructors
    public ActivityLogEntry() {}

    public ActivityLogEntry(EntityType entityType, String entityId, ActivityType activityType,
                              LocalTime startTime, LocalTime endTime, String description) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.activityType = activityType;
        this.startTime = startTime;
        this.endTime = endTime;
        this.description = description;
    }

    // Getters and Setters
    public EntityType getEntityType() { return entityType; }
    public void setEntityType(EntityType entityType) { this.entityType = entityType; }

    public String getEntityId() { return entityId; }
    public void setEntityId(String entityId) { this.entityId = entityId; }

    public ActivityType getActivityType() { return activityType; }
    public void setActivityType(ActivityType activityType) { this.activityType = activityType; }

    public LocalTime getStartTime() { return startTime; }
    public void setStartTime(LocalTime startTime) { this.startTime = startTime; }

    public LocalTime getEndTime() { return endTime; }
    public void setEndTime(LocalTime endTime) { this.endTime = endTime; }

    public String getStartLocationId() { return startLocationId; }
    public void setStartLocationId(String startLocationId) { this.startLocationId = startLocationId; }

    public String getEndLocationId() { return endLocationId; }
    public void setEndLocationId(String endLocationId) { this.endLocationId = endLocationId; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Integer getStartChargeKm() { return startChargeKm; }
    public void setStartChargeKm(Integer startChargeKm) { this.startChargeKm = startChargeKm; }

    public Integer getEndChargeKm() { return endChargeKm; }
    public void setEndChargeKm(Integer endChargeKm) { this.endChargeKm = endChargeKm; }

    @Override
    public String toString() {
        return "ActivityLogEntry{" +
                "entityType=" + entityType +
                ", entityId='" + entityId + '\'' +
                ", activityType=" + activityType +
                ", startTime=" + startTime +
                ", endTime=" + endTime +
                ", startLocationId='" + startLocationId + '\'' +
                ", endLocationId='" + endLocationId + '\'' +
                ", description='" + description + '\'' +
                ", startChargeKm=" + startChargeKm +
                ", endChargeKm=" + endChargeKm +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ActivityLogEntry that = (ActivityLogEntry) o;
        return entityType == that.entityType &&
                Objects.equals(entityId, that.entityId) &&
                activityType == that.activityType &&
                Objects.equals(startTime, that.startTime) &&
                Objects.equals(endTime, that.endTime); // Basic equality for now
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityType, entityId, activityType, startTime, endTime);
    }
}