package com.bus.domain;

import java.util.Objects;

public class BusDriver {
    private String id;
    private String name;
    // We'll add more properties like current shift hours, last break time etc. later

    public BusDriver() {
    }

    public BusDriver(String id, String name) {
        this.id = id;
        this.name = name;
    }

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

    @Override
    public String toString() {
        return "BusDriver{" +
               "id='" + id + '\'' +
               ", name='" + name + '\'' +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BusDriver busDriver = (BusDriver) o;
        return Objects.equals(id, busDriver.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}