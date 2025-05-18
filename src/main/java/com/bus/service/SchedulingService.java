package com.bus.service;

import java.time.LocalTime; // Assuming this imports Bus, BusDepot, BusDriver, BusRoute, BusStop, RouteRun, ScheduleSolution
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.bus.domain.Bus;
import com.bus.domain.BusDriver;
import com.bus.domain.BusRoute;
import com.bus.domain.RouteRun;
import com.bus.domain.ScheduleSolution;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class SchedulingService {

    @Inject
    DataService dataService;

    // Constants for simplified travel and charging (NEW for Step 8)
    private static final int TRAVEL_TIME_DEPOT_TO_TERMINAL_MINS = 15;
    private static final int TRAVEL_DISTANCE_DEPOT_TO_TERMINAL_KM = 5;
    private static final int TRAVEL_TIME_TERMINAL_TO_DEPOT_MINS = 20;
    private static final int TRAVEL_DISTANCE_TERMINAL_TO_DEPOT_KM = 10; // Placeholder distance
    private static final int CHARGING_DURATION_MINS = 120; // 2 hours
    // Stricter threshold: must charge if less than this after returning to depot AND couldn't do a minimal next run
    private static final double MIN_CHARGE_PERCENTAGE_THRESHOLD_FOR_NEXT_OPERATION = 0.20; // e.g., 20%
    private static final int MINIMAL_NEXT_ROUTE_PROXY_DISTANCE_KM = 20; // Proxy for a small next route

    // Helper inner class to track bus state during scheduling
    private static class BusState {
        Bus bus;
        LocalTime nextAvailableTime;
        String currentLocationId; // BusStop, Terminal, or Depot ID
        int currentChargeKm;
        boolean isChargingOrHeadingToCharge; // NEW for Step 8 (renamed from needsChargingNext for clarity)

        BusState(Bus bus) {
            this.bus = bus;
            this.nextAvailableTime = LocalTime.MIN;
            this.currentLocationId = bus.getCurrentLocationId();
            this.currentChargeKm = bus.getCurrentChargeKm();
            this.isChargingOrHeadingToCharge = false; // Initially not charging
        }
    }

    // Helper inner class to track driver state
    private static class DriverState {
        BusDriver driver;
        LocalTime nextAvailableTime;
        String currentLocationId;

        DriverState(BusDriver driver) {
            this.driver = driver;
            this.nextAvailableTime = LocalTime.MIN;
            this.currentLocationId = null; // Or busDriver.getHomeDepotId() or similar if applicable
        }
    }


    public ScheduleSolution solveSchedule() {
        System.out.println("SchedulingService: solveSchedule() called with bus range & charging logic.");

        List<RouteRun> originalRunsToSchedule = dataService.getRouteRunsToSchedule();
        List<Bus> allBuses = dataService.getAllBuses();
        List<BusDriver> allDrivers = dataService.getAllBusDrivers();
        Map<String, BusRoute> routeTemplates = dataService.getAllBusRoutes().stream()
                .collect(Collectors.toMap(BusRoute::getId, route -> route));
        // Map<String, BusStop> allStops = dataService.getAllBusStops().stream() // Not actively used yet
        //         .collect(Collectors.toMap(BusStop::getId, stop -> stop));

        Map<String, BusState> busStates = allBuses.stream()
                .collect(Collectors.toMap(Bus::getBusId, BusState::new));
        Map<String, DriverState> driverStates = allDrivers.stream()
                .collect(Collectors.toMap(BusDriver::getId, DriverState::new));

        List<RouteRun> sortedRuns = originalRunsToSchedule.stream()
                .sorted(Comparator.comparing(RouteRun::getDepartureTime))
                .collect(Collectors.toList());

        // We will modify the 'sortedRuns' list directly by setting assignments
        // and return it wrapped in ScheduleSolution.
        // The 'assignedRuns' list from Step 7 is not strictly needed if we update 'sortedRuns'.

        for (RouteRun run : sortedRuns) { // Iterate through all runs to try and assign
            BusRoute routeTemplate = routeTemplates.get(run.getBusRouteId());
            if (routeTemplate == null) {
                System.err.println("Could not find route template for run: " + run.getId());
                continue;
            }

            String requiredStartTerminalId = routeTemplate.getStartTerminalId();
            LocalTime runDepartureTime = run.getDepartureTime();
            LocalTime runArrivalTimeAtEndTerminal = run.getArrivalTime(); // From RouteRun constructor
            int routeDistanceKm = routeTemplate.getTotalDistanceKm();

            BusState bestFitBusState = null;
            DriverState bestFitDriverState = null;

            // --- Find an available bus (MODIFIED for Step 8) ---
            for (BusState busState : busStates.values()) {
                if (busState.isChargingOrHeadingToCharge) { // If bus is dedicated to charging, skip
                    continue;
                }

                int travelToStartDistanceKm = 0;
                LocalTime busReadyAtStartTerminal = busState.nextAvailableTime;

                if (!busState.currentLocationId.equals(requiredStartTerminalId)) {
                    if (busState.currentLocationId.equals(busState.bus.getDepotId())) { // Bus is at its home depot
                        travelToStartDistanceKm = TRAVEL_DISTANCE_DEPOT_TO_TERMINAL_KM;
                        busReadyAtStartTerminal = busState.nextAvailableTime.plusMinutes(TRAVEL_TIME_DEPOT_TO_TERMINAL_MINS);
                    } else { // Bus is at another terminal or unknown location
                        continue; // Simplification: Bus must be at start terminal or its home depot
                    }
                }

                if (busReadyAtStartTerminal.isAfter(runDepartureTime)) {
                    continue; // Bus would be late for the run
                }

                // --- NEW Step 8 Check: Can bus do this run AND return to its home depot? ---
                int travelFromEndToHomeDepotKm = 0;
                if (!routeTemplate.getEndTerminalId().equals(busState.bus.getDepotId())) {
                    travelFromEndToHomeDepotKm = TRAVEL_DISTANCE_TERMINAL_TO_DEPOT_KM;
                }
                int totalCycleDistanceKm = travelToStartDistanceKm + routeDistanceKm + travelFromEndToHomeDepotKm;

                if (busState.currentChargeKm >= totalCycleDistanceKm) {
                    bestFitBusState = busState;
                    break; // Found a suitable bus
                }
            } // End bus search loop

            if (bestFitBusState != null) {
                // --- Find an available driver (Same as Step 7 for now) ---
                for (DriverState driverState : driverStates.values()) {
                    boolean driverAtTerminalOrAvailable = driverState.currentLocationId == null ||
                                                      driverState.currentLocationId.equals(requiredStartTerminalId);
                    LocalTime driverReadyTime = driverState.nextAvailableTime;
                    if (!driverAtTerminalOrAvailable && driverState.currentLocationId != null) {
                        // Simplistic: if driver is at another terminal, add 10 mins travel
                        // This part needs more robust inter-terminal travel logic for drivers
                        // driverReadyTime = driverState.nextAvailableTime.plusMinutes(10); 
                    }

                    if (!driverReadyTime.isAfter(runDepartureTime)) {
                        // For simplicity, assume driver can reach if not at the exact terminal but available in time
                        bestFitDriverState = driverState;
                        break;
                    }
                }
            }

            if (bestFitBusState != null && bestFitDriverState != null) {
                run.setAssignedBusId(bestFitBusState.bus.getBusId());
                run.setAssignedDriverId(bestFitDriverState.driver.getId());

                // --- Update Bus State (MODIFIED for Step 8) ---
                int travelToStartDistanceKm = 0; // Recalculate for accuracy in update
                if (!bestFitBusState.currentLocationId.equals(requiredStartTerminalId)) {
                    travelToStartDistanceKm = TRAVEL_DISTANCE_DEPOT_TO_TERMINAL_KM;
                }
                bestFitBusState.currentChargeKm -= (travelToStartDistanceKm + routeDistanceKm);
                bestFitBusState.currentLocationId = routeTemplate.getEndTerminalId(); // Location after completing route
                bestFitBusState.nextAvailableTime = runArrivalTimeAtEndTerminal; // Available after completing route

                // --- NEW Step 8: Charging decision logic ---
                int travelFromEndToHomeDepotKm = 0;
                LocalTime arrivalAtHomeDepot = runArrivalTimeAtEndTerminal;
                if (!routeTemplate.getEndTerminalId().equals(bestFitBusState.bus.getDepotId())) {
                    travelFromEndToHomeDepotKm = TRAVEL_DISTANCE_TERMINAL_TO_DEPOT_KM;
                    arrivalAtHomeDepot = runArrivalTimeAtEndTerminal.plusMinutes(TRAVEL_TIME_TERMINAL_TO_DEPOT_MINS);
                }

                int chargeAfterReturningToDepotKm = bestFitBusState.currentChargeKm - travelFromEndToHomeDepotKm;
                double chargePercentageAfterReturn = (double) chargeAfterReturningToDepotKm / bestFitBusState.bus.getRangeKm();
                
                // Check if it can do a minimal next operation (travel to terminal + a small route)
                boolean canDoMinimalNextOp = chargeAfterReturningToDepotKm >= (TRAVEL_DISTANCE_DEPOT_TO_TERMINAL_KM + MINIMAL_NEXT_ROUTE_PROXY_DISTANCE_KM);

                if (chargePercentageAfterReturn < MIN_CHARGE_PERCENTAGE_THRESHOLD_FOR_NEXT_OPERATION || !canDoMinimalNextOp) {
                    System.out.println("Bus " + bestFitBusState.bus.getBusId() + " (at " + routeTemplate.getEndTerminalId() +
                                       " after run " + run.getId() + ") needs charging. Charge before return trip to depot: " + bestFitBusState.currentChargeKm + "km. " +
                                       "Est. charge after return: " + chargeAfterReturningToDepotKm + "km.");

                    bestFitBusState.isChargingOrHeadingToCharge = true; // Bus is now dedicated to charging
                    bestFitBusState.currentLocationId = bestFitBusState.bus.getDepotId(); // Arrived at home depot
                    bestFitBusState.currentChargeKm = chargeAfterReturningToDepotKm; // Charge upon arrival at depot
                    bestFitBusState.nextAvailableTime = arrivalAtHomeDepot.plusMinutes(CHARGING_DURATION_MINS); // Available after charging
                    bestFitBusState.currentChargeKm = bestFitBusState.bus.getRangeKm(); // Fully charged
                    bestFitBusState.isChargingOrHeadingToCharge = false; // Ready for new assignments
                    System.out.println("Bus " + bestFitBusState.bus.getBusId() + " completed charging at " + bestFitBusState.currentLocationId +
                                       ". Next available: " + bestFitBusState.nextAvailableTime + ". Charge: " + bestFitBusState.currentChargeKm + "km.");
                } else {
                    // Bus returns to home depot but doesn't need immediate charging
                    bestFitBusState.currentLocationId = bestFitBusState.bus.getDepotId();
                    bestFitBusState.currentChargeKm = chargeAfterReturningToDepotKm;
                    bestFitBusState.nextAvailableTime = arrivalAtHomeDepot;
                     System.out.println("Bus " + bestFitBusState.bus.getBusId() + " returned to depot " + bestFitBusState.currentLocationId +
                                       " after run " + run.getId() + ". Next available: " + bestFitBusState.nextAvailableTime +
                                       ". Charge: " + bestFitBusState.currentChargeKm + "km. No immediate charge needed.");
                }

                // --- Update Driver State (Same as Step 7) ---
                bestFitDriverState.currentLocationId = routeTemplate.getEndTerminalId();
                bestFitDriverState.nextAvailableTime = runArrivalTimeAtEndTerminal;

                System.out.println("Assigned Run: " + run.getId() + " to Bus: " + run.getAssignedBusId() +
                                   " and Driver: " + run.getAssignedDriverId() +
                                   ". Bus charge after route (before return to depot): " + (bestFitBusState.isChargingOrHeadingToCharge ? "N/A (charging)" : bestFitBusState.currentChargeKm + travelFromEndToHomeDepotKm /*re-add for display only*/) + "km");

            } else { // Could not assign bus and/or driver
                 System.out.println("Could not assign Run: " + run.getId() +
                                   (bestFitBusState == null ? " - No suitable bus found." : "") +
                                   (bestFitDriverState == null && bestFitBusState != null ? " - No suitable driver found." : ""));
            }
        } // End of loop through runs

        // 'sortedRuns' now contains all original runs, with assignments populated where possible.
        ScheduleSolution solution = new ScheduleSolution(sortedRuns);
        long countAssigned = sortedRuns.stream().filter(r -> r.getAssignedBusId() != null).count();
        System.out.println("Returning " + countAssigned + " assigned runs out of " + sortedRuns.size() + " total runs.");
        return solution;
    }
}