package com.bus.service;

import com.bus.domain.*;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule; // For LocalTime
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.InputStream;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@ApplicationScoped
public class DataService {

    @Inject
    ObjectMapper objectMapper; // Injected by Quarkus

    private final Map<String, BusStop> busStops = new ConcurrentHashMap<>();
    private final Map<String, BusDepot> busDepots = new ConcurrentHashMap<>();
    private final Map<String, BusTerminal> busTerminals = new ConcurrentHashMap<>();
    private final Map<String, Bus> buses = new ConcurrentHashMap<>();
    private final Map<String, BusDriver> busDrivers = new ConcurrentHashMap<>();
    private final Map<String, BusRoute> busRoutes = new ConcurrentHashMap<>();
    private final List<RouteRun> routeRunsToSchedule = new ArrayList<>();

    @PostConstruct
    void initializeData() {
        objectMapper.registerModule(new JavaTimeModule());

        loadDataFile("/data/bus_stops.json", new TypeReference<List<BusStop>>() {}, list -> list.forEach(item -> busStops.put(item.getId(), item)));
        loadDataFile("/data/bus_depots.json", new TypeReference<List<BusDepot>>() {}, list -> list.forEach(item -> {
            busDepots.put(item.getId(), item);
            busStops.put(item.getId(), item); 
        }));
        loadDataFile("/data/bus_terminals.json", new TypeReference<List<BusTerminal>>() {}, list -> list.forEach(item -> {
            busTerminals.put(item.getId(), item);
            busStops.put(item.getId(), item); 
        }));
        // ****** THIS IS THE CORRECTED LINE ******
        loadDataFile("/data/buses.json", new TypeReference<List<Bus>>() {}, list -> list.forEach(item -> buses.put(item.getBusId(), item)));
        // *****************************************
        loadDataFile("/data/bus_drivers.json", new TypeReference<List<BusDriver>>() {}, list -> list.forEach(item -> busDrivers.put(item.getId(), item)));
        loadDataFile("/data/bus_routes.json", new TypeReference<List<BusRoute>>() {}, list -> list.forEach(item -> busRoutes.put(item.getId(), item)));

        List<RouteRunConfigEntry> routeRunConfigs = loadDataFile("/data/route_runs_config.json", new TypeReference<List<RouteRunConfigEntry>>() {}, null);
        if (routeRunConfigs != null) {
            for (RouteRunConfigEntry config : routeRunConfigs) {
                BusRoute route = busRoutes.get(config.getBusRouteId());
                if (route != null) {
                    for (String timeStr : config.getDepartureTimes()) {
                        LocalTime departureTime = LocalTime.parse(timeStr); 
                        String runId = route.getId() + "-" + departureTime.toString().replace(":", "");
                        routeRunsToSchedule.add(new RouteRun(runId, route.getId(), departureTime, route.getTravelTimeMinutes()));
                    }
                }
            }
        }

        System.out.println("DataService Initialized with data from JSON files.");
        System.out.println("Total Bus Stops (incl. depots, terminals): " + this.busStops.size());
        System.out.println("Total Depots: " + this.busDepots.size());
        System.out.println("Total Terminals: " + this.busTerminals.size());
        System.out.println("Total Buses: " + this.buses.size());
        System.out.println("Total Drivers: " + this.busDrivers.size());
        System.out.println("Total Route Templates: " + this.busRoutes.size());
        System.out.println("Total Route Runs to Schedule: " + this.routeRunsToSchedule.size());
    }

    private <T> List<T> loadDataFile(String filePath, TypeReference<List<T>> typeReference, java.util.function.Consumer<List<T>> processor) {
        try (InputStream inputStream = DataService.class.getResourceAsStream(filePath)) {
            if (inputStream == null) {
                System.err.println("Cannot find data file: " + filePath);
                return null;
            }
            List<T> list = objectMapper.readValue(inputStream, typeReference);
            if (processor != null && list != null) {
                processor.accept(list);
            }
            return list;
        } catch (Exception e) {
            System.err.println("Failed to load data from " + filePath + ": " + e.getMessage());
            // e.printStackTrace(); // Optional: uncomment for more detailed error during development
            return null;
        }
    }

    private static class RouteRunConfigEntry {
        private String busRouteId;
        private List<String> departureTimes;

        public String getBusRouteId() { return busRouteId; }
        public void setBusRouteId(String busRouteId) { this.busRouteId = busRouteId; }
        public List<String> getDepartureTimes() { return departureTimes; }
        public void setDepartureTimes(List<String> departureTimes) { this.departureTimes = departureTimes; }
    }

    public List<BusStop> getAllBusStops() { return new ArrayList<>(busStops.values()); }
    public BusStop getBusStopById(String id) { return busStops.get(id); }
    public List<BusDepot> getAllBusDepots() { return new ArrayList<>(busDepots.values()); }
    public BusDepot getBusDepotById(String id) { return busDepots.get(id); }
    public List<BusTerminal> getAllBusTerminals() { return new ArrayList<>(busTerminals.values()); }
    public BusTerminal getBusTerminalById(String id) { return busTerminals.get(id); }
    public List<Bus> getAllBuses() { return new ArrayList<>(buses.values()); }
    public Bus getBusById(String busId) { return buses.get(busId); } // Changed param name for clarity
    public List<BusDriver> getAllBusDrivers() { return new ArrayList<>(busDrivers.values()); }
    public BusDriver getBusDriverById(String id) { return busDrivers.get(id); }
    public List<BusRoute> getAllBusRoutes() { return new ArrayList<>(busRoutes.values()); }
    public BusRoute getBusRouteById(String id) { return busRoutes.get(id); }
    public List<RouteRun> getRouteRunsToSchedule() {
        return new ArrayList<>(routeRunsToSchedule.stream()
            .map(rr -> {
                BusRoute route = busRoutes.get(rr.getBusRouteId());
                int travelTime = (route != null) ? route.getTravelTimeMinutes() : 0;
                return new RouteRun(rr.getId(), rr.getBusRouteId(), rr.getDepartureTime(), travelTime);
            })
            .collect(Collectors.toList()));
    }
}