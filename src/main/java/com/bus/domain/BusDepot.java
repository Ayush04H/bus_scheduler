package com.bus.domain;

import java.util.Objects;

public class BusDepot extends BusStop {
    private int maxCapacity; // Max number of buses it can accommodate
    private boolean hasChargingStation;

    public BusDepot() {
        super();
        this.hasChargingStation = true; // Depots usually have charging
    }

    public BusDepot(String id, String name, int maxCapacity) {
        super(id, name);
        this.maxCapacity = maxCapacity;
        this.hasChargingStation = true;
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(int maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public boolean isHasChargingStation() {
        return hasChargingStation;
    }

    public void setHasChargingStation(boolean hasChargingStation) {
        this.hasChargingStation = hasChargingStation;
    }

    @Override
    public String toString() {
        return "BusDepot{" +
               "id='" + getId() + '\'' +
               ", name='" + getName() + '\'' +
               ", maxCapacity=" + maxCapacity +
               ", hasChargingStation=" + hasChargingStation +
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        if (!super.equals(o)) return false; // Checks id from BusStop
        BusDepot busDepot = (BusDepot) o;
        return maxCapacity == busDepot.maxCapacity && hasChargingStation == busDepot.hasChargingStation;
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), maxCapacity, hasChargingStation);
    }
}