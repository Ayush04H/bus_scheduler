package com.bus.domain;

import java.util.Objects;

public class BusStop {
    private String id;
    private String name;
    // We can add coordinates later if needed for distance calculations
    // private double latitude;
    // private double longitude;

    // Constructors
    public BusStop() {
    }

    public BusStop(String id, String name) {
        this.id = id;
        this.name = name;
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

    // toString, equals, and hashCode
    @Override
    public String toString() {
        return "BusStop{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BusStop busStop = (BusStop) o;
        return Objects.equals(id, busStop.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}