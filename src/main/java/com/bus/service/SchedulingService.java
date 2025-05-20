package com.bus.service;

import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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

    // Bus related constants (from Step 8/9)
    private static final int TRAVEL_TIME_DEPOT_TO_TERMINAL_MINS = 15;
    private static final int TRAVEL_DISTANCE_DEPOT_TO_TERMINAL_KM = 5;
    private static final int TRAVEL_TIME_TERMINAL_TO_DEPOT_MINS = 20;
    private static final int TRAVEL_DISTANCE_TERMINAL_TO_DEPOT_KM = 10;
    private static final int CHARGING_DURATION_MINS = 120;
    private static final double MIN_CHARGE_PERCENTAGE_THRESHOLD_FOR_NEXT_OPERATION = 0.20;
    private static final int MINIMAL_NEXT_ROUTE_PROXY_DISTANCE_KM = 20;

    // Driver related constants (from Step 9/10)
    private static final int MAX_CONTINUOUS_DRIVING_MINS = 4 * 60;
    private static final int MANDATORY_BREAK_MINS = 30;
    private static final int MAX_REGULAR_DRIVING_MINS_PER_DAY = 8 * 60;
    private static final int MAX_TOTAL_DRIVING_MINS_PER_DAY_WITH_OT = 10 * 60;
    private static final int MIN_WAIT_TIME_TO_RESET_CONTINUOUS_DRIVING_MINS = 15;
    private static final int DRIVER_TRAVEL_TIME_BETWEEN_TERMINALS_MINS = 15; // From Step 10

    // Score weights (NEW for Step 11)
    private static final int PENALTY_PER_UNASSIGNED_RUN = -1000;
    private static final int PENALTY_PER_BUS_USED = -100;

    // BusState class (unchanged from your previous version)
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
        // Getter needed by heuristic if we sort on private field
        public LocalTime getNextAvailableTime() { return nextAvailableTime; }
    }

    // DriverState class (unchanged from your previous version)
    private static class DriverState {
        BusDriver driver;
        LocalTime nextAvailableTime;
        String currentLocationId;
        int continuousDrivingTimeMinutes;
        int totalDrivingTimeMinutesToday;

        DriverState(BusDriver driver) {
            this.driver = driver;
            this.nextAvailableTime = LocalTime.MIN;
            this.currentLocationId = null;
            this.continuousDrivingTimeMinutes = 0;
            this.totalDrivingTimeMinutesToday = 0;
        }
    }

    public ScheduleSolution solveSchedule() {
        System.out.println("SchedulingService: solveSchedule() with SCORING & BUS HEURISTIC logic."); // Updated message

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

        for (RouteRun run : sortedRuns) {
            BusRoute routeTemplate = routeTemplates.get(run.getBusRouteId());
            if (routeTemplate == null) { /* ... unchanged ... */ continue; }

            String requiredStartTerminalId = routeTemplate.getStartTerminalId();
            LocalTime runDepartureTime = run.getDepartureTime();
            LocalTime runArrivalTimeAtEndTerminal = run.getArrivalTime();
            int routeTravelTimeMinutes = routeTemplate.getTravelTimeMinutes();
            int routeDistanceKm = routeTemplate.getTotalDistanceKm();

            BusState bestFitBusState = null;
            DriverState bestFitDriverState = null;
            LocalTime driverEffectiveStartTimeForRun = null;

            // --- Find an available bus (MODIFIED for Step 11 Heuristic) ---
            List<BusState> suitableBuses = new ArrayList<>();
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
                    suitableBuses.add(busState); // Add to list instead of immediate break
                }
            }

            if (!suitableBuses.isEmpty()) {
                // Heuristic:
                // 1. Prefer buses already used (nextAvailableTime != LocalTime.MIN) over fresh ones.
                // 2. Among those of the same "used" status, prefer the one available earliest.
                // 3. Tie-break by bus ID for deterministic behavior.
                bestFitBusState = suitableBuses.stream()
                    .min(Comparator.comparing((BusState bs) -> bs.nextAvailableTime.equals(LocalTime.MIN)) // false (used) before true (fresh)
                                   .thenComparing(BusState::getNextAvailableTime) // Earliest availability
                                   .thenComparing(bs -> bs.bus.getBusId()))       // Tie-breaker
                    .orElse(null); // Should not be null if suitableBuses is not empty

                if (bestFitBusState != null) {
                     System.out.println("For run " + run.getId() + ", selected bus " + bestFitBusState.bus.getBusId() +
                                       " (Available: " + bestFitBusState.nextAvailableTime +
                                       ", Fresh: " + bestFitBusState.nextAvailableTime.equals(LocalTime.MIN) + ")" +
                                       " from " + suitableBuses.size() + " suitable buses.");
                }
            }
            // --- End Bus Search Modification ---


            if (bestFitBusState != null) {
                // --- Driver Search Logic (unchanged from Step 10) ---
                for (DriverState driverState : driverStates.values()) {
                    if (driverState.totalDrivingTimeMinutesToday + routeTravelTimeMinutes > MAX_TOTAL_DRIVING_MINS_PER_DAY_WITH_OT) { continue; }
                    LocalTime driverArrivalTimeAtRequiredTerminal = driverState.nextAvailableTime;
                    if (driverState.currentLocationId == null) { /* ... */ }
                    else if (!driverState.currentLocationId.equals(requiredStartTerminalId)) {
                        driverArrivalTimeAtRequiredTerminal = driverState.nextAvailableTime.plusMinutes(DRIVER_TRAVEL_TIME_BETWEEN_TERMINALS_MINS);
                        System.out.println("Driver " + driverState.driver.getId() + " needs to travel from " +
                                           driverState.currentLocationId + " to " + requiredStartTerminalId +
                                           ". Arrives at: " + driverArrivalTimeAtRequiredTerminal);
                    }
                    LocalTime referenceTimeForWaitCalc = driverState.nextAvailableTime;
                    if (ChronoUnit.MINUTES.between(referenceTimeForWaitCalc, runDepartureTime) >= MIN_WAIT_TIME_TO_RESET_CONTINUOUS_DRIVING_MINS) {
                        if (driverState.continuousDrivingTimeMinutes > 0) {
                             System.out.println("Driver " + driverState.driver.getId() + " had a wait... Resetting continuous driving...");
                            driverState.continuousDrivingTimeMinutes = 0;
                        }
                    }
                    LocalTime availabilityConsideringPotentialBreak = driverArrivalTimeAtRequiredTerminal;
                    if (driverState.continuousDrivingTimeMinutes + routeTravelTimeMinutes > MAX_CONTINUOUS_DRIVING_MINS) {
                        System.out.println("Driver " + driverState.driver.getId() + " would exceed continuous... Needs break...");
                        availabilityConsideringPotentialBreak = driverArrivalTimeAtRequiredTerminal.plusMinutes(MANDATORY_BREAK_MINS);
                    }
                    if (!availabilityConsideringPotentialBreak.isAfter(runDepartureTime)) {
                        bestFitDriverState = driverState;
                        driverEffectiveStartTimeForRun = availabilityConsideringPotentialBreak;
                        break;
                    }
                }
                // --- End Driver Search ---
            }

            if (bestFitBusState != null && bestFitDriverState != null) {
                run.setAssignedBusId(bestFitBusState.bus.getBusId());
                run.setAssignedDriverId(bestFitDriverState.driver.getId());

                // --- Update Bus State (unchanged from Step 10) ---
                // ... (full bus update and charging logic) ...
                int travelToStartDistanceKm_bus = 0;
                if (!bestFitBusState.currentLocationId.equals(requiredStartTerminalId)) {
                    travelToStartDistanceKm_bus = TRAVEL_DISTANCE_DEPOT_TO_TERMINAL_KM;
                }
                bestFitBusState.currentChargeKm -= (travelToStartDistanceKm_bus + routeDistanceKm);
                bestFitBusState.currentLocationId = routeTemplate.getEndTerminalId();
                bestFitBusState.nextAvailableTime = runArrivalTimeAtEndTerminal;
                int travelFromEndToHomeDepotKm_bus = 0;
                LocalTime arrivalAtHomeDepot_bus = runArrivalTimeAtEndTerminal;
                if (!routeTemplate.getEndTerminalId().equals(bestFitBusState.bus.getDepotId())) {
                    travelFromEndToHomeDepotKm_bus = TRAVEL_DISTANCE_TERMINAL_TO_DEPOT_KM;
                    arrivalAtHomeDepot_bus = runArrivalTimeAtEndTerminal.plusMinutes(TRAVEL_TIME_TERMINAL_TO_DEPOT_MINS);
                }
                int chargeAfterReturningToDepotKm_bus = bestFitBusState.currentChargeKm - travelFromEndToHomeDepotKm_bus;
                double chargePercentageAfterReturn_bus = (double) chargeAfterReturningToDepotKm_bus / bestFitBusState.bus.getRangeKm();
                boolean canDoMinimalNextOp_bus = chargeAfterReturningToDepotKm_bus >= (TRAVEL_DISTANCE_DEPOT_TO_TERMINAL_KM + MINIMAL_NEXT_ROUTE_PROXY_DISTANCE_KM);
                if (chargePercentageAfterReturn_bus < MIN_CHARGE_PERCENTAGE_THRESHOLD_FOR_NEXT_OPERATION || !canDoMinimalNextOp_bus) {
                    System.out.println("Bus " + bestFitBusState.bus.getBusId() + " needs charging..."); // Abridged log
                    bestFitBusState.isChargingOrHeadingToCharge = true;
                    bestFitBusState.currentLocationId = bestFitBusState.bus.getDepotId();
                    bestFitBusState.currentChargeKm = chargeAfterReturningToDepotKm_bus;
                    bestFitBusState.nextAvailableTime = arrivalAtHomeDepot_bus.plusMinutes(CHARGING_DURATION_MINS);
                    bestFitBusState.currentChargeKm = bestFitBusState.bus.getRangeKm();
                    bestFitBusState.isChargingOrHeadingToCharge = false;
                    System.out.println("Bus " + bestFitBusState.bus.getBusId() + " finished charging."); // Abridged log
                } else {
                    bestFitBusState.currentLocationId = bestFitBusState.bus.getDepotId();
                    bestFitBusState.currentChargeKm = chargeAfterReturningToDepotKm_bus;
                    bestFitBusState.nextAvailableTime = arrivalAtHomeDepot_bus;
                }
                // --- End Bus State Update ---

                // --- Update Driver State (unchanged from Step 10) ---
                // ... (full driver state update logic including breaks) ...
                boolean breakWasForcedByThisRun = (bestFitDriverState.continuousDrivingTimeMinutes + routeTravelTimeMinutes > MAX_CONTINUOUS_DRIVING_MINS) &&
                                                  driverEffectiveStartTimeForRun.equals(
                                                      (bestFitDriverState.currentLocationId == null || bestFitDriverState.currentLocationId.equals(requiredStartTerminalId) ?
                                                          bestFitDriverState.nextAvailableTime :
                                                          bestFitDriverState.nextAvailableTime.plusMinutes(DRIVER_TRAVEL_TIME_BETWEEN_TERMINALS_MINS)
                                                      ).plusMinutes(MANDATORY_BREAK_MINS)
                                                  );
                if (breakWasForcedByThisRun) {
                    System.out.println("Driver " + bestFitDriverState.driver.getId() + " is confirmed to have taken a mandatory break...");
                    bestFitDriverState.continuousDrivingTimeMinutes = 0;
                }
                bestFitDriverState.continuousDrivingTimeMinutes += routeTravelTimeMinutes;
                bestFitDriverState.totalDrivingTimeMinutesToday += routeTravelTimeMinutes;
                bestFitDriverState.currentLocationId = routeTemplate.getEndTerminalId();
                bestFitDriverState.nextAvailableTime = runArrivalTimeAtEndTerminal;
                System.out.println("Assigned Run: " + run.getId() + " to Bus: " + run.getAssignedBusId() +
                                   " and Driver: " + run.getAssignedDriverId() +
                                   ". Driver ... details ..."); // Abridged log
                // --- End Driver State Update ---

            } else { /* ... unchanged ... */ 
                 System.out.println("Could not assign Run: " + run.getId() +
                                   (bestFitBusState == null ? " - No suitable bus." : "") +
                                   (bestFitDriverState == null && bestFitBusState != null ? " - No suitable driver." : ""));
            }
        } // End of loop through runs

        ScheduleSolution solution = new ScheduleSolution(sortedRuns);
        calculateScore(solution, busStates); // NEW: Call scoring method

        long countAssignedBoth = sortedRuns.stream().filter(r -> r.getAssignedBusId() != null && r.getAssignedDriverId() != null).count();
        System.out.println("Finished scheduling. Total runs with Bus & Driver: " + countAssignedBoth + " out of " + sortedRuns.size() + " total runs.");
        System.out.println("Solution Score: " + solution.getScore() + " (" + solution.getScoreExplanation() + ")"); // Log score
        return solution;
    }

    // NEW calculateScore method for Step 11
    private void calculateScore(ScheduleSolution solution, Map<String, BusState> finalBusStates) {
        int currentScore = 0;
        StringBuilder explanation = new StringBuilder();

        // 1. Penalty for unassigned runs
        long unassignedCount = solution.getAssignedRouteRuns().stream()
                .filter(run -> run.getAssignedBusId() == null || run.getAssignedDriverId() == null)
                .count();
        currentScore += unassignedCount * PENALTY_PER_UNASSIGNED_RUN;
        explanation.append(unassignedCount).append(" unassigned runs (penalty: ").append(unassignedCount * PENALTY_PER_UNASSIGNED_RUN).append("). ");
        solution.setUnassignedRunCount((int) unassignedCount);

        // 2. Penalty for each bus used
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