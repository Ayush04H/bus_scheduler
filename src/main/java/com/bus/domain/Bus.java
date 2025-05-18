package com.bus.domain;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Bus {

    @JsonProperty("bus_id") // For reading "bus_id" from JSON file
    private String busIdValue; // Using a different internal name to avoid confusion

    @JsonProperty("number_plate") // For reading "number_plate" from JSON file
    private String numberPlateValue;

    private String depotId;
    private int rangeKm;
    private int currentChargeKm;
    private String currentLocationId;

    public Bus() {}

    // --- Getters for API JSON output (Serialization) ---
    @JsonProperty("busId") // Output as "busId" in API
    public String getBusId() {
        return busIdValue;
    }

    @JsonProperty("numberPlate") // Output as "numberPlate" in API
    public String getNumberPlate() {
        return numberPlateValue;
    }

    // --- Setters that Jackson can use for Deserialization (from JSON file) ---
    // Jackson will use the field annotations primarily for this if setters are simple
    public void setBusIdValue(String busIdValue) { // Setter matches internal field name
        this.busIdValue = busIdValue;
    }
    
    public void setNumberPlateValue(String numberPlateValue) {
        this.numberPlateValue = numberPlateValue;
    }


    // Standard getters/setters for other fields
    public String getDepotId() { return depotId; }
    public void setDepotId(String depotId) { this.depotId = depotId; }

    public int getRangeKm() { return rangeKm; }
    public void setRangeKm(int rangeKm) { this.rangeKm = rangeKm; }

    public int getCurrentChargeKm() { return currentChargeKm; }
    public void setCurrentChargeKm(int currentChargeKm) { this.currentChargeKm = currentChargeKm; }

    public String getCurrentLocationId() { return currentLocationId; }
    public void setCurrentLocationId(String currentLocationId) { this.currentLocationId = currentLocationId; }

    @Override
    public String toString() {
        return "Bus{" +
               "busId='" + busIdValue + '\'' +
               ", numberPlate='" + numberPlateValue + '\'' +
               // ... rest of fields
               '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Bus bus = (Bus) o;
        return Objects.equals(busIdValue, bus.busIdValue);
    }

    @Override
    public int hashCode() {
        return Objects.hash(busIdValue);
    }
}