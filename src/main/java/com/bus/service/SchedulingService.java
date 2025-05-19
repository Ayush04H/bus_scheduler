package com.bus.service;

import java.time.LocalTime; // Assuming this imports all necessary domain classes
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map; // Import for calculating duration
import java.util.stream.Collectors; // Though not directly used if sortedRuns is primary list

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

    // Bus related constants (from Step 8)
    private static final int TRAVEL_TIME_DEPOT_TO_TERMINAL_MINS = 15;
    private static final int TRAVEL_DISTANCE_DEPOT_TO_TERMINAL_KM = 5;
    private static final int TRAVEL_TIME_TERMINAL_TO_DEPOT_MINS = 20;
    private static final int TRAVEL_DISTANCE_TERMINAL_TO_DEPOT_KM = 10;
    private static final int CHARGING_DURATION_MINS = 120;
    private static final double MIN_CHARGE_PERCENTAGE_THRESHOLD_FOR_NEXT_OPERATION = 0.20;
    private static final int MINIMAL_NEXT_ROUTE_PROXY_DISTANCE_KM = 20;

    // Driver related constants (NEW for Step 9)
    private static final int MAX_CONTINUOUS_DRIVING_MINS = 4 * 60; // 240 minutes
    private static final int MANDATORY_BREAK_MINS = 30;
    private static final int MAX_REGULAR_DRIVING_MINS_PER_DAY = 8 * 60; // 480 minutes
    private static final int MAX_TOTAL_DRIVING_MINS_PER_DAY_WITH_OT = 10 * 60; // 600 minutes (8 reg + 2 OT)
    private static final int MIN_WAIT_TIME_TO_RESET_CONTINUOUS_DRIVING_MINS = 15; // If driver waits 15+ mins, continuous driving resets

    // BusState class (from Step 8)
    private static class BusState {
        Bus bus;
        LocalTime nextAvailableTime;
        String currentLocationId;
        int currentChargeKm;
        boolean isChargingOrHeadingToCharge;

        BusState(Bus bus) {
            this.bus = bus;
            this.nextAvailableTime = LocalTime.MIN;
            this.currentLocationId = bus.getCurrentLocationId();
            this.currentChargeKm = bus.getCurrentChargeKm();
            this.isChargingOrHeadingToCharge = false;
        }
    }

    // DriverState class (MODIFIED for Step 9)
    private static class DriverState {
        BusDriver driver;
        LocalTime nextAvailableTime;
        String currentLocationId;
        int continuousDrivingTimeMinutes; // NEW
        int totalDrivingTimeMinutesToday; // NEW
        // boolean isOnMandatoryBreak; // Can be inferred

        DriverState(BusDriver driver) {
            this.driver = driver;
            this.nextAvailableTime = LocalTime.MIN;
            this.currentLocationId = null; // Default, or could be driver's home base
            this.continuousDrivingTimeMinutes = 0; // NEW
            this.totalDrivingTimeMinutesToday = 0; // NEW
        }
    }

    public ScheduleSolution solveSchedule() {
        System.out.println("SchedulingService: solveSchedule() with DRIVER HOURS & BREAKS logic."); // Updated message

        List<RouteRun> originalRunsToSchedule = dataService.getRouteRunsToSchedule();
        List<Bus> allBuses = dataService.getAllBuses();
        List<BusDriver> allDrivers = dataService.getAllBusDrivers();
        Map<String, BusRoute> routeTemplates = dataService.getAllBusRoutes().stream()
                .collect(Collectors.toMap(BusRoute::getId, route -> route));

        Map<String, BusState> busStates = allBuses.stream()
                .collect(Collectors.toMap(Bus::getBusId, BusState::new));
        Map<String, DriverState> driverStates = allDrivers.stream()
                .collect(Collectors.toMap(BusDriver::getId, DriverState::new)); // Uses new DriverState

        List<RouteRun> sortedRuns = originalRunsToSchedule.stream()
                .sorted(Comparator.comparing(RouteRun::getDepartureTime))
                .collect(Collectors.toList());

        // We modify 'sortedRuns' by setting assignments.
        // No separate 'processedRuns' or 'assignedRuns' list is strictly needed for the final solution object
        // if 'sortedRuns' is the one being updated and returned.

        for (RouteRun run : sortedRuns) {
            BusRoute routeTemplate = routeTemplates.get(run.getBusRouteId());
            if (routeTemplate == null) {
                System.err.println("Could not find route template for run: " + run.getId());
                continue;
            }

            String requiredStartTerminalId = routeTemplate.getStartTerminalId();
            LocalTime runDepartureTime = run.getDepartureTime();
            LocalTime runArrivalTimeAtEndTerminal = run.getArrivalTime();
            int routeDistanceKm = routeTemplate.getTotalDistanceKm();
            int routeTravelTimeMinutes = routeTemplate.getTravelTimeMinutes(); // Get route duration

            BusState bestFitBusState = null;
            DriverState bestFitDriverState = null;
            LocalTime driverEffectiveStartTimeForRun = null; // When driver is truly ready after potential break

            // --- Bus Search Logic (Same as Step 8 - No changes here) ---
            for (BusState busState : busStates.values()) {
                if (busState.isChargingOrHeadingToCharge) { continue; }
                int travelToStartDistanceKm = 0;
                LocalTime busReadyAtStartTerminal = busState.nextAvailableTime;
                if (!busState.currentLocationId.equals(requiredStartTerminalId)) {
                    if (busState.currentLocationId.equals(busState.bus.getDepotId())) {
                        travelToStartDistanceKm = TRAVEL_DISTANCE_DEPOT_TO_TERMINAL_KM;
                        busReadyAtStartTerminal = busState.nextAvailableTime.plusMinutes(TRAVEL_TIME_DEPOT_TO_TERMINAL_MINS);
                    } else { continue; }
                }
                if (busReadyAtStartTerminal.isAfter(runDepartureTime)) { continue; }
                int travelFromEndToHomeDepotKm = 0;
                if (!routeTemplate.getEndTerminalId().equals(busState.bus.getDepotId())) {
                    travelFromEndToHomeDepotKm = TRAVEL_DISTANCE_TERMINAL_TO_DEPOT_KM;
                }
                int totalCycleDistanceKm = travelToStartDistanceKm + routeDistanceKm + travelFromEndToHomeDepotKm;
                if (busState.currentChargeKm >= totalCycleDistanceKm) {
                    bestFitBusState = busState;
                    break;
                }
            }
            // --- End Bus Search ---

            if (bestFitBusState != null) {
                // --- Find an available driver (NEW STEP 9 LOGIC) ---
                for (DriverState driverState : driverStates.values()) {
                    // 1. Check total driving time limit for the day
                    if (driverState.totalDrivingTimeMinutesToday + routeTravelTimeMinutes > MAX_TOTAL_DRIVING_MINS_PER_DAY_WITH_OT) {
                        continue; // Would exceed max daily driving
                    }

                    LocalTime driverReadyAtTerminalTime = driverState.nextAvailableTime;
                    // Simplistic: if driver is not at the required terminal, assume 10 min travel.
                    // A proper implementation would require a distance/time matrix for driver travel.
                    if (driverState.currentLocationId != null && !driverState.currentLocationId.equals(requiredStartTerminalId)) {
                        driverReadyAtTerminalTime = driverState.nextAvailableTime.plusMinutes(10); // Placeholder for inter-terminal travel
                    }
                    
                    // If driver has been waiting at a terminal before this run's departure time, reset continuous driving
                    // This means any gap between driver's last 'nextAvailableTime' and 'runDepartureTime'
                    if (driverState.nextAvailableTime.isBefore(runDepartureTime) && // Driver was free before this run
                        ChronoUnit.MINUTES.between(driverState.nextAvailableTime, runDepartureTime) >= MIN_WAIT_TIME_TO_RESET_CONTINUOUS_DRIVING_MINS) {
                        if (driverState.continuousDrivingTimeMinutes > 0) { // Only log if there was continuous time to reset
                             System.out.println("Driver " + driverState.driver.getId() + " had a wait of " +
                                               ChronoUnit.MINUTES.between(driverState.nextAvailableTime, runDepartureTime) +
                                               " mins. Resetting continuous driving from " + driverState.continuousDrivingTimeMinutes + " to 0.");
                            driverState.continuousDrivingTimeMinutes = 0;
                        }
                    }

                    LocalTime availabilityConsideringPotentialBreak = driverReadyAtTerminalTime;
                    boolean needsBreakBeforeThisRun = false;

                    // 2. Check continuous driving limit and if a break is needed *before* this run
                    if (driverState.continuousDrivingTimeMinutes + routeTravelTimeMinutes > MAX_CONTINUOUS_DRIVING_MINS) {
                        System.out.println("Driver " + driverState.driver.getId() + " needs a break. Cont. driving: " +
                                           driverState.continuousDrivingTimeMinutes + "min. Route time: " + routeTravelTimeMinutes + "min.");
                        availabilityConsideringPotentialBreak = driverReadyAtTerminalTime.plusMinutes(MANDATORY_BREAK_MINS);
                        needsBreakBeforeThisRun = true;
                    }

                    // 3. Is driver available on time for the run (considering the potential break)?
                    if (!availabilityConsideringPotentialBreak.isAfter(runDepartureTime)) {
                        bestFitDriverState = driverState;
                        driverEffectiveStartTimeForRun = availabilityConsideringPotentialBreak; // This is when they are TRULY ready
                        break; // Found a suitable driver
                    }
                } // --- End Driver Search ---
            }


            if (bestFitBusState != null && bestFitDriverState != null) {
                run.setAssignedBusId(bestFitBusState.bus.getBusId());
                run.setAssignedDriverId(bestFitDriverState.driver.getId());

                // --- Update Bus State (Same as Step 8) ---
                int travelToStartDistanceKm = 0;
                if (!bestFitBusState.currentLocationId.equals(requiredStartTerminalId)) {
                    travelToStartDistanceKm = TRAVEL_DISTANCE_DEPOT_TO_TERMINAL_KM;
                }
                bestFitBusState.currentChargeKm -= (travelToStartDistanceKm + routeDistanceKm);
                bestFitBusState.currentLocationId = routeTemplate.getEndTerminalId();
                bestFitBusState.nextAvailableTime = runArrivalTimeAtEndTerminal;

                int travelFromEndToHomeDepotKm = 0;
                LocalTime arrivalAtHomeDepot = runArrivalTimeAtEndTerminal;
                if (!routeTemplate.getEndTerminalId().equals(bestFitBusState.bus.getDepotId())) {
                    travelFromEndToHomeDepotKm = TRAVEL_DISTANCE_TERMINAL_TO_DEPOT_KM;
                    arrivalAtHomeDepot = runArrivalTimeAtEndTerminal.plusMinutes(TRAVEL_TIME_TERMINAL_TO_DEPOT_MINS);
                }
                int chargeAfterReturningToDepotKm = bestFitBusState.currentChargeKm - travelFromEndToHomeDepotKm;
                double chargePercentageAfterReturn = (double) chargeAfterReturningToDepotKm / bestFitBusState.bus.getRangeKm();
                boolean canDoMinimalNextOp = chargeAfterReturningToDepotKm >= (TRAVEL_DISTANCE_DEPOT_TO_TERMINAL_KM + MINIMAL_NEXT_ROUTE_PROXY_DISTANCE_KM);

                if (chargePercentageAfterReturn < MIN_CHARGE_PERCENTAGE_THRESHOLD_FOR_NEXT_OPERATION || !canDoMinimalNextOp) {
                    System.out.println("Bus " + bestFitBusState.bus.getBusId() + " (at " + routeTemplate.getEndTerminalId() +
                                       " after run " + run.getId() + ") needs charging. Charge before return trip to depot: " + bestFitBusState.currentChargeKm + "km. " +
                                       "Est. charge after return: " + chargeAfterReturningToDepotKm + "km.");
                    bestFitBusState.isChargingOrHeadingToCharge = true;
                    bestFitBusState.currentLocationId = bestFitBusState.bus.getDepotId();
                    bestFitBusState.currentChargeKm = chargeAfterReturningToDepotKm;
                    bestFitBusState.nextAvailableTime = arrivalAtHomeDepot.plusMinutes(CHARGING_DURATION_MINS);
                    bestFitBusState.currentChargeKm = bestFitBusState.bus.getRangeKm();
                    bestFitBusState.isChargingOrHeadingToCharge = false;
                    System.out.println("Bus " + bestFitBusState.bus.getBusId() + " completed charging at " + bestFitBusState.currentLocationId +
                                       ". Next available: " + bestFitBusState.nextAvailableTime + ". Charge: " + bestFitBusState.currentChargeKm + "km.");
                } else {
                    bestFitBusState.currentLocationId = bestFitBusState.bus.getDepotId();
                    bestFitBusState.currentChargeKm = chargeAfterReturningToDepotKm;
                    bestFitBusState.nextAvailableTime = arrivalAtHomeDepot;
                     System.out.println("Bus " + bestFitBusState.bus.getBusId() + " returned to depot " + bestFitBusState.currentLocationId +
                                       " after run " + run.getId() + ". Next available: " + bestFitBusState.nextAvailableTime +
                                       ". Charge: " + bestFitBusState.currentChargeKm + "km. No immediate charge needed.");
                }
                // --- End Bus State Update ---


                // --- Update Driver State (NEW STEP 9 LOGIC) ---
                // If driver's effective start time implies a break was taken before this run started
                if (driverEffectiveStartTimeForRun.isAfter(bestFitDriverState.nextAvailableTime) && // Original availability was earlier
                    driverEffectiveStartTimeForRun.isAfter(runDepartureTime.minusMinutes(MANDATORY_BREAK_MINS + 1))) { // And it's not just due to travel
                     // This logic path is a bit tricky because driverEffectiveStartTimeForRun *should be* <= runDepartureTime from selection.
                     // The critical part is whether continuousDrivingTimeMinutes was reset *because* a break was *forced*.
                     // The condition `bestFitDriverState.continuousDrivingTimeMinutes + routeTravelTimeMinutes > MAX_CONTINUOUS_DRIVING_MINS`
                     // in the search loop already identified if a break was *needed*.
                }

                // If a break was needed and factored into driverEffectiveStartTimeForRun
                if (bestFitDriverState.continuousDrivingTimeMinutes + routeTravelTimeMinutes > MAX_CONTINUOUS_DRIVING_MINS &&
                    driverEffectiveStartTimeForRun.equals(bestFitDriverState.nextAvailableTime.plusMinutes(MANDATORY_BREAK_MINS)) || // Covers case where no travel, just break
                    (bestFitDriverState.currentLocationId != null && !bestFitDriverState.currentLocationId.equals(requiredStartTerminalId) && // Covers travel + break
                     driverEffectiveStartTimeForRun.equals(bestFitDriverState.nextAvailableTime.plusMinutes(10).plusMinutes(MANDATORY_BREAK_MINS))) 
                    ) {
                    System.out.println("Driver " + bestFitDriverState.driver.getId() + " took a mandatory break. Continuous driving resets.");
                    bestFitDriverState.continuousDrivingTimeMinutes = 0; // Reset because break was taken
                    // The nextAvailableTime is effectively driverEffectiveStartTimeForRun (which is runDepartureTime or earlier)
                    // and they start driving *this* route.
                }
                
                bestFitDriverState.continuousDrivingTimeMinutes += routeTravelTimeMinutes;
                bestFitDriverState.totalDrivingTimeMinutesToday += routeTravelTimeMinutes;
                bestFitDriverState.currentLocationId = routeTemplate.getEndTerminalId();
                bestFitDriverState.nextAvailableTime = runArrivalTimeAtEndTerminal;

                System.out.println("Assigned Run: " + run.getId() + " to Bus: " + run.getAssignedBusId() +
                                   " and Driver: " + run.getAssignedDriverId() +
                                   ". Driver cont. driving: " + bestFitDriverState.continuousDrivingTimeMinutes + "min, total day: " + bestFitDriverState.totalDrivingTimeMinutesToday + "min");

            } else { // Could not assign bus and/or driver
                 System.out.println("Could not assign Run: " + run.getId() +
                                   (bestFitBusState == null ? " - No suitable bus." : "") +
                                   (bestFitDriverState == null && bestFitBusState != null ? " - No suitable driver found." : ""));
            }
        } // End of loop through runs

        ScheduleSolution solution = new ScheduleSolution(sortedRuns); // Return all runs, with assignments updated
        long countAssignedBoth = sortedRuns.stream().filter(r -> r.getAssignedBusId() != null && r.getAssignedDriverId() != null).count();
        System.out.println("Finished scheduling. Total runs with Bus & Driver: " + countAssignedBoth + " out of " + sortedRuns.size() + " total runs.");
        return solution;
    }
}