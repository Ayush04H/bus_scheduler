package com.bus.domain;

import java.util.List;
import java.util.Objects;

public class BusRoute {
    private String id;
    private String name;
    private String startTerminalId;
    private String endTerminalId;
    private List<String> stopIds; // Ordered list of stop IDs, includes start and end
    private int totalDistanceKm;
    private int travelTimeMinutes; // Time to complete this one-way route

    // Constructors
    public BusRoute() {
    }

    public BusRoute(String id, String name, String startTerminalId, String endTerminalId, List<String> stopIds, int totalDistanceKm, int travelTimeMinutes) {
        this.id = id;
        this.name = name;
        this.startTerminalId = startTerminalId;
        this.endTerminalId = endTerminalId;
        this.stopIds = stopIds;
        this.totalDistanceKm = totalDistanceKm;
        this.travelTimeMinutes = travelTimeMinutes;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStartTerminalId() {
        return startTerminalId;
    }

    public void setStartTerminalId(String startTerminalId) {
        this.startTerminalId = startTerminalId;
    }

    public String getEndTerminalId() {
        return endTerminalId;
    }

    public void setEndTerminalId(String endTerminalId) {
        this.endTerminalId = endTerminalId;
    }

    public List<String> getStopIds() {
        return stopIds;
    }

    public void setStopIds(List<String> stopIds) {
        this.stopIds = stopIds;
    }

    public int getTotalDistanceKm() {
        return totalDistanceKm;
    }

    public void setTotalDistanceKm(int totalDistanceKm) {
        this.totalDistanceKm = totalDistanceKm;
    }

    public int getTravelTimeMinutes() {
        return travelTimeMinutes;
    }

    public void setTravelTimeMinutes(int travelTimeMinutes) {
        this.travelTimeMinutes = travelTimeMinutes;
    }

    // toString, equals, and hashCode
    @Override
    public String toString() {
        return "BusRoute{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               ", startTerminalId='" + startTerminalId + '\'' +
               ", endTerminalId='" + endTerminalId + '\'' +
               ", stopIds=" + stopIds +
               ", totalDistanceKm=" + totalDistanceKm +
               ", travelTimeMinutes=" + travelTimeMinutes +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BusRoute busRoute = (BusRoute) o;
        return Objects.equals(id, busRoute.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}