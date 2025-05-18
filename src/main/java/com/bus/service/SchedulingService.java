package com.bus.service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.bus.domain.Bus;
import com.bus.domain.BusDepot;
import com.bus.domain.BusDriver;
import com.bus.domain.BusRoute;
import com.bus.domain.BusStop;
import com.bus.domain.RouteRun;
import com.bus.domain.ScheduleSolution;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SchedulingService {

    @Inject
    DataService dataService;

    // Helper inner class to track bus state during scheduling
    private static class BusState {
        Bus bus;
        LocalTime nextAvailableTime;
        String currentLocationId; // BusStop, Terminal, or Depot ID
        int currentChargeKm;

        BusState(Bus bus) {
            this.bus = bus;
            this.nextAvailableTime = LocalTime.MIN; // Available from the beginning of the day
            this.currentLocationId = bus.getCurrentLocationId(); // Initial location from data
            this.currentChargeKm = bus.getCurrentChargeKm();
        }
    }

    // Helper inner class to track driver state
    private static class DriverState {
        BusDriver driver;
        LocalTime nextAvailableTime;
        String currentLocationId; // For simplicity, assume drivers can be at terminals

        DriverState(BusDriver driver) {
            this.driver = driver;
            this.nextAvailableTime = LocalTime.MIN;
            // For now, let's assume drivers can start at any terminal if not assigned.
            // A more complex model would track their home base or last known terminal.
            this.currentLocationId = null; // Or initialize to a default depot/terminal if applicable
        }
    }


    public ScheduleSolution solveSchedule() {
        System.out.println("SchedulingService: solveSchedule() called for actual solving.");

        // 1. Get fresh problem data (immutable copies from DataService)
        List<RouteRun> originalRunsToSchedule = dataService.getRouteRunsToSchedule();
        List<Bus> allBuses = dataService.getAllBuses();
        List<BusDriver> allDrivers = dataService.getAllBusDrivers();
        Map<String, BusRoute> routeTemplates = dataService.getAllBusRoutes().stream()
                .collect(Collectors.toMap(BusRoute::getId, route -> route));
        Map<String, BusStop> allStops = dataService.getAllBusStops().stream()
                .collect(Collectors.toMap(BusStop::getId, stop -> stop));


        // 2. Create mutable states for buses and drivers
        Map<String, BusState> busStates = allBuses.stream()
                .collect(Collectors.toMap(Bus::getBusId, BusState::new));
        Map<String, DriverState> driverStates = allDrivers.stream()
                .collect(Collectors.toMap(BusDriver::getId, DriverState::new));

        // 3. Sort RouteRuns by departure time (critical for a greedy approach)
        List<RouteRun> sortedRuns = originalRunsToSchedule.stream()
                .sorted(Comparator.comparing(RouteRun::getDepartureTime))
                .collect(Collectors.toList());

        List<RouteRun> assignedRuns = new ArrayList<>();

        // 4. Iterate through sorted RouteRuns and try to assign
        for (RouteRun run : sortedRuns) {
            BusRoute routeTemplate = routeTemplates.get(run.getBusRouteId());
            if (routeTemplate == null) {
                System.err.println("Could not find route template for run: " + run.getId());
                continue; // Skip this run
            }

            String requiredStartTerminalId = routeTemplate.getStartTerminalId();
            LocalTime runDepartureTime = run.getDepartureTime();
            LocalTime runArrivalTime = run.getArrivalTime(); // Calculated when RouteRun was created
            int routeDistance = routeTemplate.getTotalDistanceKm();

            BusState assignedBusState = null;
            DriverState assignedDriverState = null;

            // Find an available bus
            for (BusState busState : busStates.values()) {
                // Basic checks:
                // 1. Is bus available before or at run departure?
                // 2. Is bus at the correct start terminal? (Simplification: or can travel from its depot)
                // 3. Does bus have enough charge?

                boolean canReachStartTerminal = false;
                LocalTime busArrivalTimeAtStartTerminal = busState.nextAvailableTime; // Base time
                int travelDistanceToStartTerminal = 0;

                if (busState.currentLocationId.equals(requiredStartTerminalId)) {
                    canReachStartTerminal = true;
                } else {
                    // Simplistic: Assume bus is at a depot and can travel to the terminal.
                    // A real system needs travel times/distances between any two points.
                    // For now, let's assume 15 mins travel from its depot to *any* terminal if not already there.
                    // And a nominal distance, say 5km.
                    // THIS IS A MAJOR SIMPLIFICATION.
                    BusDepot busDepot = dataService.getBusDepotById(busState.bus.getDepotId());
                    if (busDepot != null && busState.currentLocationId.equals(busDepot.getId())) {
                        // If bus is at its home depot, simulate travel to start terminal
                        busArrivalTimeAtStartTerminal = busState.nextAvailableTime.plusMinutes(15); 
                        travelDistanceToStartTerminal = 5; 
                        canReachStartTerminal = true; 
                    }
                    // If bus is at another terminal, more complex logic is needed.
                    // For now, if not at start terminal or its home depot, it's not considered.
                }


                if (canReachStartTerminal && !busArrivalTimeAtStartTerminal.isAfter(runDepartureTime) &&
                    (busState.currentChargeKm >= (routeDistance + travelDistanceToStartTerminal))) {
                    assignedBusState = busState;
                    break; // Found a bus
                }
            }

            if (assignedBusState != null) {
                // Find an available driver
                for (DriverState driverState : driverStates.values()) {
                    // Basic checks:
                    // 1. Is driver available?
                    // 2. Is driver at the correct start terminal? (MAJOR SIMPLIFICATION)
                    boolean driverAtTerminal = driverState.currentLocationId == null || driverState.currentLocationId.equals(requiredStartTerminalId);

                    if (!driverState.nextAvailableTime.isAfter(runDepartureTime) && driverAtTerminal) {
                         // If driver was at a different terminal, we'd need travel time. Assume they can "teleport" or are waiting.
                        assignedDriverState = driverState;
                        break; // Found a driver
                    }
                }
            }

            // If both bus and driver are found, make the assignment
            if (assignedBusState != null && assignedDriverState != null) {
                run.setAssignedBusId(assignedBusState.bus.getBusId());
                run.setAssignedDriverId(assignedDriverState.driver.getId());
                assignedRuns.add(run);

                // Update bus state
                int travelDistanceToStartTerminal = 0;
                if (!assignedBusState.currentLocationId.equals(requiredStartTerminalId)) {
                     // This implies it traveled from its depot (as per our simplified logic)
                    travelDistanceToStartTerminal = 5; // Nominal distance assumed
                }
                assignedBusState.currentChargeKm -= (routeDistance + travelDistanceToStartTerminal);
                assignedBusState.currentLocationId = routeTemplate.getEndTerminalId();
                assignedBusState.nextAvailableTime = runArrivalTime;

                // Update driver state
                assignedDriverState.currentLocationId = routeTemplate.getEndTerminalId();
                assignedDriverState.nextAvailableTime = runArrivalTime;

                System.out.println("Assigned Run: " + run.getId() + " to Bus: " + run.getAssignedBusId() +
                                   " and Driver: " + run.getAssignedDriverId());
            } else {
                System.out.println("Could not assign Run: " + run.getId() +
                                   (assignedBusState == null ? " - No suitable bus." : "") +
                                   (assignedDriverState == null && assignedBusState != null ? " - No suitable driver." : ""));
                // Add unassigned run to a separate list or handle as needed.
                // For now, it just won't be in assignedRuns if not assigned.
                // To show all runs in UI, we should return 'sortedRuns' with updated assignments.
            }
        } // End of loop through runs

        // The 'sortedRuns' list now contains runs, some of which have assignments.
        // Let's return this list.
        ScheduleSolution solution = new ScheduleSolution(sortedRuns); 
        // You can add scoring logic later, e.g., based on number of assigned runs.
        
        System.out.println("Returning " + assignedRuns.size() + " assigned runs out of " + sortedRuns.size());
        return solution;
    }
}