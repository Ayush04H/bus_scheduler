package com.bus.service;

import java.time.LocalTime; // Ensure ActivityLogEntry is imported
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.bus.domain.ActivityLogEntry;
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

    // --- All your existing constants from Step 11 ---
    private static final int TRAVEL_TIME_DEPOT_TO_TERMINAL_MINS = 15;
    private static final int TRAVEL_DISTANCE_DEPOT_TO_TERMINAL_KM = 5;
    private static final int TRAVEL_TIME_TERMINAL_TO_DEPOT_MINS = 20;
    private static final int TRAVEL_DISTANCE_TERMINAL_TO_DEPOT_KM = 10;
    private static final int CHARGING_DURATION_MINS = 120;
    private static final double MIN_CHARGE_PERCENTAGE_THRESHOLD_FOR_NEXT_OPERATION = 0.20;
    private static final int MINIMAL_NEXT_ROUTE_PROXY_DISTANCE_KM = 20;
    private static final int MAX_CONTINUOUS_DRIVING_MINS = 4 * 60;
    private static final int MANDATORY_BREAK_MINS = 30;
    private static final int MAX_REGULAR_DRIVING_MINS_PER_DAY = 8 * 60;
    private static final int MAX_TOTAL_DRIVING_MINS_PER_DAY_WITH_OT = 10 * 60;
    private static final int MIN_WAIT_TIME_TO_RESET_CONTINUOUS_DRIVING_MINS = 15;
    private static final int DRIVER_TRAVEL_TIME_BETWEEN_TERMINALS_MINS = 15;
    private static final int PENALTY_PER_UNASSIGNED_RUN = -1000;
    private static final int PENALTY_PER_BUS_USED = -100;
    // --- End Constants ---


    // --- BusState and DriverState classes (unchanged from your Step 11 version) ---
    private static class BusState {
        Bus bus; LocalTime nextAvailableTime; String currentLocationId;
        int currentChargeKm; boolean isChargingOrHeadingToCharge;
        BusState(Bus bus) {this.bus = bus; this.nextAvailableTime = LocalTime.MIN; this.currentLocationId = bus.getCurrentLocationId(); this.currentChargeKm = bus.getCurrentChargeKm(); this.isChargingOrHeadingToCharge = false;}
        public LocalTime getNextAvailableTime() { return nextAvailableTime; }
    }
    private static class DriverState {
        BusDriver driver; LocalTime nextAvailableTime; String currentLocationId;
        int continuousDrivingTimeMinutes; int totalDrivingTimeMinutesToday;
        DriverState(BusDriver driver) { this.driver = driver; this.nextAvailableTime = LocalTime.MIN; this.currentLocationId = null; this.continuousDrivingTimeMinutes = 0; this.totalDrivingTimeMinutesToday = 0;}
    }
    // --- End State Classes ---


    public ScheduleSolution solveSchedule() {
        System.out.println("SchedulingService: solveSchedule() with ACTIVITY LOG generation.");

        // --- Data Initialization (unchanged) ---
        List<RouteRun> originalRunsToSchedule = dataService.getRouteRunsToSchedule();
        List<Bus> allBuses = dataService.getAllBuses();
        List<BusDriver> allDrivers = dataService.getAllBusDrivers();
        Map<String, BusRoute> routeTemplates = dataService.getAllBusRoutes().stream()
                .collect(Collectors.toMap(BusRoute::getId, route -> route));
        Map<String, BusState> busStates = allBuses.stream()
                .collect(Collectors.toMap(Bus::getBusId, BusState::new));
        Map<String, DriverState> driverStates = allDrivers.stream()
                .collect(Collectors.toMap(BusDriver::getId, DriverState::new));
        List<RouteRun> sortedRuns = originalRunsToSchedule.stream()
                .sorted(Comparator.comparing(RouteRun::getDepartureTime))
                .collect(Collectors.toList());
        // --- End Data Initialization ---

        List<ActivityLogEntry> activityLog = new ArrayList<>(); // Initialize activity log for this solution

        for (RouteRun run : sortedRuns) {
            BusRoute routeTemplate = routeTemplates.get(run.getBusRouteId());
            if (routeTemplate == null) { continue; }

            String requiredStartTerminalId = routeTemplate.getStartTerminalId();
            LocalTime runDepartureTime = run.getDepartureTime();
            LocalTime runArrivalTimeAtEndTerminal = run.getArrivalTime();
            int routeTravelTimeMinutes = routeTemplate.getTravelTimeMinutes();
            int routeDistanceKm = routeTemplate.getTotalDistanceKm();

            BusState bestFitBusState = null;
            DriverState bestFitDriverState = null;
            LocalTime driverEffectiveStartTimeForRun = null;
            LocalTime busEffectiveStartTimeAtTerminal = null; // When bus arrives at required terminal

            // --- Find an available bus (from Step 11, add logging points) ---
            List<BusState> suitableBuses = new ArrayList<>();
            for (BusState busState : busStates.values()) {
                if (busState.isChargingOrHeadingToCharge) { continue; }
                // ... (Bus suitability checks as in Step 11) ...
                int travelToStartDistanceKm = 0;
                LocalTime busReadyAtStartTerminalCandidate = busState.nextAvailableTime; // Renamed for clarity
                String initialBusLocationForThisCheck = busState.currentLocationId;

                if (!initialBusLocationForThisCheck.equals(requiredStartTerminalId)) {
                    if (initialBusLocationForThisCheck.equals(busState.bus.getDepotId())) {
                        travelToStartDistanceKm = TRAVEL_DISTANCE_DEPOT_TO_TERMINAL_KM;
                        busReadyAtStartTerminalCandidate = busState.nextAvailableTime.plusMinutes(TRAVEL_TIME_DEPOT_TO_TERMINAL_MINS);
                    } else { continue; } // Bus not at depot or required terminal
                }
                if (busReadyAtStartTerminalCandidate.isAfter(runDepartureTime)) { continue; } // Bus would be late
                int travelFromEndToHomeDepotKm = 0;
                if (!routeTemplate.getEndTerminalId().equals(busState.bus.getDepotId())) {
                    travelFromEndToHomeDepotKm = TRAVEL_DISTANCE_TERMINAL_TO_DEPOT_KM;
                }
                int totalCycleDistanceKm = travelToStartDistanceKm + routeDistanceKm + travelFromEndToHomeDepotKm;
                if (busState.currentChargeKm >= totalCycleDistanceKm) {
                    suitableBuses.add(busState);
                }
            }
            if (!suitableBuses.isEmpty()) {
                bestFitBusState = suitableBuses.stream()
                    .min(Comparator.comparing((BusState bs) -> bs.nextAvailableTime.equals(LocalTime.MIN))
                                   .thenComparing(BusState::getNextAvailableTime)
                                   .thenComparing(bs -> bs.bus.getBusId()))
                    .orElse(null);
                
                if (bestFitBusState != null) {
                    // Determine when bus would actually arrive at the terminal for this run
                    busEffectiveStartTimeAtTerminal = bestFitBusState.nextAvailableTime;
                    if (!bestFitBusState.currentLocationId.equals(requiredStartTerminalId)) {
                        busEffectiveStartTimeAtTerminal = bestFitBusState.nextAvailableTime.plusMinutes(TRAVEL_TIME_DEPOT_TO_TERMINAL_MINS);
                    }
                }
            }
            // --- End Bus Search ---

            if (bestFitBusState != null) {
                // --- Find an available driver (from Step 10, add logging points) ---
                for (DriverState driverState : driverStates.values()) {
                    if (driverState.totalDrivingTimeMinutesToday + routeTravelTimeMinutes > MAX_TOTAL_DRIVING_MINS_PER_DAY_WITH_OT) { continue; }
                    
                    LocalTime driverArrivalTimeAtRequiredTerminal = driverState.nextAvailableTime;
                    String initialDriverLocationForThisCheck = driverState.currentLocationId;
                    boolean driverTravelled = false;

                    if (initialDriverLocationForThisCheck == null) { /* Assume at required terminal by nextAvailableTime */ }
                    else if (!initialDriverLocationForThisCheck.equals(requiredStartTerminalId)) {
                        driverArrivalTimeAtRequiredTerminal = driverState.nextAvailableTime.plusMinutes(DRIVER_TRAVEL_TIME_BETWEEN_TERMINALS_MINS);
                        driverTravelled = true;
                    }
                    
                    LocalTime referenceTimeForWaitCalc = driverState.nextAvailableTime;
                    if (ChronoUnit.MINUTES.between(referenceTimeForWaitCalc, runDepartureTime) >= MIN_WAIT_TIME_TO_RESET_CONTINUOUS_DRIVING_MINS) {
                        if (driverState.continuousDrivingTimeMinutes > 0) { driverState.continuousDrivingTimeMinutes = 0; }
                    }
                    
                    LocalTime availabilityConsideringPotentialBreak = driverArrivalTimeAtRequiredTerminal;
                    boolean breakNeededBeforeThisRun = false;
                    if (driverState.continuousDrivingTimeMinutes + routeTravelTimeMinutes > MAX_CONTINUOUS_DRIVING_MINS) {
                        availabilityConsideringPotentialBreak = driverArrivalTimeAtRequiredTerminal.plusMinutes(MANDATORY_BREAK_MINS);
                        breakNeededBeforeThisRun = true;
                    }

                    if (!availabilityConsideringPotentialBreak.isAfter(runDepartureTime)) {
                        bestFitDriverState = driverState;
                        driverEffectiveStartTimeForRun = availabilityConsideringPotentialBreak; // Actual time driver is ready for this run
                        
                        // Log driver travel if it happened
                        if (driverTravelled) {
                            activityLog.add(new ActivityLogEntry(ActivityLogEntry.EntityType.DRIVER, driverState.driver.getId(),
                                ActivityLogEntry.ActivityType.TRAVEL_TO_START_TERMINAL,
                                driverState.nextAvailableTime, driverArrivalTimeAtRequiredTerminal,
                                "Travel from " + initialDriverLocationForThisCheck + " to " + requiredStartTerminalId));
                        }
                        // Log break if taken before run
                        if (breakNeededBeforeThisRun) {
                             activityLog.add(new ActivityLogEntry(ActivityLogEntry.EntityType.DRIVER, driverState.driver.getId(),
                                ActivityLogEntry.ActivityType.MANDATORY_BREAK,
                                driverArrivalTimeAtRequiredTerminal, // Break starts after arriving at terminal (or being free there)
                                availabilityConsideringPotentialBreak, // Break ends
                                "Mandatory break at " + requiredStartTerminalId));
                        }
                        break; // Found driver
                    }
                }
                // --- End Driver Search ---
            }

            if (bestFitBusState != null && bestFitDriverState != null) {
                run.setAssignedBusId(bestFitBusState.bus.getBusId());
                run.setAssignedDriverId(bestFitDriverState.driver.getId());

                LocalTime actualRunStartTime = runDepartureTime; // Assume run starts on time if resources are ready
                // If bus or driver effective start times are later, the run effectively starts then.
                // For simplicity, we assume they "wait" if ready early.
                // This means actualRunStartTime is max(runDepartureTime, busEffectiveStartTimeAtTerminal, driverEffectiveStartTimeForRun)
                // But our selection logic ensures bus/driver are ready *by* runDepartureTime.

                // --- Log Bus Travel to Start Terminal (if occurred) ---
                int busInitialChargeForRun = bestFitBusState.currentChargeKm; // Charge before any travel for this run
                if (!bestFitBusState.currentLocationId.equals(requiredStartTerminalId)) { // Implies travel from depot
                     activityLog.add(new ActivityLogEntry(ActivityLogEntry.EntityType.BUS, bestFitBusState.bus.getBusId(),
                        ActivityLogEntry.ActivityType.TRAVEL_TO_START_TERMINAL,
                        bestFitBusState.nextAvailableTime, // Start of travel from depot
                        busEffectiveStartTimeAtTerminal,    // Arrival at terminal
                        "Travel from " + bestFitBusState.currentLocationId + " to " + requiredStartTerminalId));
                    busInitialChargeForRun -= TRAVEL_DISTANCE_DEPOT_TO_TERMINAL_KM; // Charge consumed for travel
                }
                
                // --- Log Route Service for Bus and Driver ---
                ActivityLogEntry busRouteActivity = new ActivityLogEntry(ActivityLogEntry.EntityType.BUS, bestFitBusState.bus.getBusId(),
                    ActivityLogEntry.ActivityType.ROUTE_SERVICE, actualRunStartTime, runArrivalTimeAtEndTerminal,
                    "Run " + run.getId() + " (Route " + routeTemplate.getId() + ")");
                busRouteActivity.setStartLocationId(requiredStartTerminalId);
                busRouteActivity.setEndLocationId(routeTemplate.getEndTerminalId());
                busRouteActivity.setStartChargeKm(busInitialChargeForRun);
                
                ActivityLogEntry driverRouteActivity = new ActivityLogEntry(ActivityLogEntry.EntityType.DRIVER, bestFitDriverState.driver.getId(),
                    ActivityLogEntry.ActivityType.ROUTE_SERVICE, actualRunStartTime, runArrivalTimeAtEndTerminal,
                    "Run " + run.getId() + " (Route " + routeTemplate.getId() + ")");
                driverRouteActivity.setStartLocationId(requiredStartTerminalId);
                driverRouteActivity.setEndLocationId(routeTemplate.getEndTerminalId());
                
                activityLog.add(busRouteActivity);
                activityLog.add(driverRouteActivity);


                // --- Update Bus State (from Step 10) ---
                int travelToStartDistanceKm_bus = 0;
                if (!bestFitBusState.currentLocationId.equals(requiredStartTerminalId)) {
                    travelToStartDistanceKm_bus = TRAVEL_DISTANCE_DEPOT_TO_TERMINAL_KM;
                }
                bestFitBusState.currentChargeKm -= (travelToStartDistanceKm_bus + routeDistanceKm);
                busRouteActivity.setEndChargeKm(bestFitBusState.currentChargeKm); // Set end charge for the bus route activity

                bestFitBusState.currentLocationId = routeTemplate.getEndTerminalId();
                bestFitBusState.nextAvailableTime = runArrivalTimeAtEndTerminal;

                // --- Bus Charging Logic and Logging (from Step 10) ---
                int travelFromEndToHomeDepotKm_bus = 0;
                LocalTime arrivalAtHomeDepot_bus = runArrivalTimeAtEndTerminal;
                String busDepotId = bestFitBusState.bus.getDepotId();
                String busRouteEndTerminal = routeTemplate.getEndTerminalId();

                if (!busRouteEndTerminal.equals(busDepotId)) {
                    travelFromEndToHomeDepotKm_bus = TRAVEL_DISTANCE_TERMINAL_TO_DEPOT_KM;
                    arrivalAtHomeDepot_bus = runArrivalTimeAtEndTerminal.plusMinutes(TRAVEL_TIME_TERMINAL_TO_DEPOT_MINS);
                    
                    ActivityLogEntry busTravelToDepotLog = new ActivityLogEntry(ActivityLogEntry.EntityType.BUS, bestFitBusState.bus.getBusId(),
                        ActivityLogEntry.ActivityType.TRAVEL_TO_DEPOT,
                        runArrivalTimeAtEndTerminal, arrivalAtHomeDepot_bus, "Travel from " + busRouteEndTerminal + " to Depot " + busDepotId);
                    busTravelToDepotLog.setStartChargeKm(bestFitBusState.currentChargeKm); // Charge at end of route
                    busTravelToDepotLog.setEndChargeKm(bestFitBusState.currentChargeKm - travelFromEndToHomeDepotKm_bus);
                    activityLog.add(busTravelToDepotLog);
                }
                
                int chargeAfterReturningToDepotKm_bus = bestFitBusState.currentChargeKm - travelFromEndToHomeDepotKm_bus;
                // ... (rest of charging decision logic and state updates from Step 10) ...
                double chargePercentageAfterReturn_bus = (double) chargeAfterReturningToDepotKm_bus / bestFitBusState.bus.getRangeKm();
                boolean canDoMinimalNextOp_bus = chargeAfterReturningToDepotKm_bus >= (TRAVEL_DISTANCE_DEPOT_TO_TERMINAL_KM + MINIMAL_NEXT_ROUTE_PROXY_DISTANCE_KM);

                if (chargePercentageAfterReturn_bus < MIN_CHARGE_PERCENTAGE_THRESHOLD_FOR_NEXT_OPERATION || !canDoMinimalNextOp_bus) {
                    bestFitBusState.isChargingOrHeadingToCharge = true; // Conceptual
                    bestFitBusState.currentLocationId = busDepotId;
                    int chargeBeforeActualCharging = chargeAfterReturningToDepotKm_bus;
                    LocalTime chargingStartTime = arrivalAtHomeDepot_bus;
                    LocalTime chargingEndTime = arrivalAtHomeDepot_bus.plusMinutes(CHARGING_DURATION_MINS);
                    
                    ActivityLogEntry chargingActivity = new ActivityLogEntry(ActivityLogEntry.EntityType.BUS, bestFitBusState.bus.getBusId(),
                        ActivityLogEntry.ActivityType.CHARGING, chargingStartTime, chargingEndTime, "Charging at Depot " + busDepotId);
                    chargingActivity.setStartChargeKm(chargeBeforeActualCharging < 0 ? 0 : chargeBeforeActualCharging); // Ensure not negative
                    chargingActivity.setEndChargeKm(bestFitBusState.bus.getRangeKm());
                    activityLog.add(chargingActivity);
                    
                    bestFitBusState.nextAvailableTime = chargingEndTime;
                    bestFitBusState.currentChargeKm = bestFitBusState.bus.getRangeKm();
                    bestFitBusState.isChargingOrHeadingToCharge = false;
                } else {
                    bestFitBusState.currentLocationId = busDepotId;
                    bestFitBusState.currentChargeKm = chargeAfterReturningToDepotKm_bus;
                    bestFitBusState.nextAvailableTime = arrivalAtHomeDepot_bus;
                }
                // --- End Bus State & Charging Logic ---

                // --- Update Driver State (from Step 10) ---
                boolean breakWasForcedByThisRun = (bestFitDriverState.continuousDrivingTimeMinutes + routeTravelTimeMinutes > MAX_CONTINUOUS_DRIVING_MINS) &&
                                                  driverEffectiveStartTimeForRun != null &&
                                                  driverEffectiveStartTimeForRun.equals(
                                                      (bestFitDriverState.currentLocationId == null || bestFitDriverState.currentLocationId.equals(requiredStartTerminalId) ?
                                                          bestFitDriverState.nextAvailableTime :
                                                          bestFitDriverState.nextAvailableTime.plusMinutes(DRIVER_TRAVEL_TIME_BETWEEN_TERMINALS_MINS)
                                                      ).plusMinutes(MANDATORY_BREAK_MINS)
                                                  );
                if (breakWasForcedByThisRun) {
                    bestFitDriverState.continuousDrivingTimeMinutes = 0;
                }
                bestFitDriverState.continuousDrivingTimeMinutes += routeTravelTimeMinutes;
                bestFitDriverState.totalDrivingTimeMinutesToday += routeTravelTimeMinutes;
                bestFitDriverState.currentLocationId = routeTemplate.getEndTerminalId();
                bestFitDriverState.nextAvailableTime = runArrivalTimeAtEndTerminal;
                // --- End Driver State Update ---

                // System.out.println("Assigned Run: ..."); // Keep if desired

            } else { /* ... System.out.println("Could not assign Run: ...") ... */ }
        } // End of loop through runs

        ScheduleSolution solution = new ScheduleSolution(sortedRuns);
        solution.setActivityLog(activityLog); // Set the generated activity log
        calculateScore(solution, busStates); // calculateScore is from Step 11

        // ... (final console logs for assigned count and score) ...
        long countAssignedBoth = sortedRuns.stream().filter(r -> r.getAssignedBusId() != null && r.getAssignedDriverId() != null).count();
        System.out.println("Finished scheduling. Total runs with Bus & Driver: " + countAssignedBoth + " out of " + sortedRuns.size() + " total runs.");
        System.out.println("Solution Score: " + solution.getScore() + " (" + solution.getScoreExplanation() + ")");
        System.out.println("Total Activity Log Entries: " + activityLog.size());
        return solution;
    }

    // calculateScore method (from Step 11 - unchanged)
    private void calculateScore(ScheduleSolution solution, Map<String, BusState> finalBusStates) {
        int currentScore = 0;
        StringBuilder explanation = new StringBuilder();
        long unassignedCount = solution.getAssignedRouteRuns().stream()
                .filter(run -> run.getAssignedBusId() == null || run.getAssignedDriverId() == null)
                .count();
        currentScore += unassignedCount * PENALTY_PER_UNASSIGNED_RUN;
        explanation.append(unassignedCount).append(" unassigned runs (penalty: ").append(unassignedCount * PENALTY_PER_UNASSIGNED_RUN).append("). ");
        solution.setUnassignedRunCount((int) unassignedCount);
        long busesUsed = finalBusStates.values().stream()
                .filter(busState -> solution.getAssignedRouteRuns().stream()
                                   .anyMatch(run -> run.getAssignedBusId() != null && run.getAssignedBusId().equals(busState.bus.getBusId())))
                .count();
        currentScore += busesUsed * PENALTY_PER_BUS_USED;
        explanation.append(busesUsed).append(" buses used (penalty: ").append(busesUsed * PENALTY_PER_BUS_USED).append("). ");
        solution.setTotalBusesUsedCount((int) busesUsed);
        solution.setScore(currentScore);
        solution.setScoreExplanation(explanation.toString().trim());
    }
}