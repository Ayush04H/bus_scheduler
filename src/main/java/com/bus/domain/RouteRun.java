package com.bus.domain;

import java.time.LocalTime; // For departure/arrival times
import java.util.Objects;

public class RouteRun {
    private String id; // Unique ID for this specific run, e.g., R1-08:00
    private String busRouteId; // FK to BusRoute
    private LocalTime departureTime;
    private LocalTime arrivalTime; // Calculated based on departure + route travel time

    // Planning Variables (to be assigned by our solver)
    private String assignedBusId;
    private String assignedDriverId;

    public RouteRun() {
    }

    public RouteRun(String id, String busRouteId, LocalTime departureTime, int routeTravelTimeMinutes) {
        this.id = id;
        this.busRouteId = busRouteId;
        this.departureTime = departureTime;
        this.arrivalTime = departureTime.plusMinutes(routeTravelTimeMinutes);
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getBusRouteId() {
        return busRouteId;
    }

    public void setBusRouteId(String busRouteId) {
        this.busRouteId = busRouteId;
    }

    public LocalTime getDepartureTime() {
        return departureTime;
    }

    public void setDepartureTime(LocalTime departureTime) {
        this.departureTime = departureTime;
    }

    public LocalTime getArrivalTime() {
        return arrivalTime;
    }

    public void setArrivalTime(LocalTime arrivalTime) {
        this.arrivalTime = arrivalTime;
    }

    public String getAssignedBusId() {
        return assignedBusId;
    }

    public void setAssignedBusId(String assignedBusId) {
        this.assignedBusId = assignedBusId;
    }

    public String getAssignedDriverId() {
        return assignedDriverId;
    }

    public void setAssignedDriverId(String assignedDriverId) {
        this.assignedDriverId = assignedDriverId;
    }

    @Override
    public String toString() {
        return "RouteRun{" +
               "id='" + id + '\'' +
               ", busRouteId='" + busRouteId + '\'' +
               ", departureTime=" + departureTime +
               ", arrivalTime=" + arrivalTime +
               ", assignedBusId='" + assignedBusId + '\'' +
               ", assignedDriverId='" + assignedDriverId + '\'' +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RouteRun routeRun = (RouteRun) o;
        return Objects.equals(id, routeRun.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}