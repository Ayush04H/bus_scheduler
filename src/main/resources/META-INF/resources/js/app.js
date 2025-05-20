document.addEventListener('DOMContentLoaded', () => {
    // Load initial data
    loadAllStops();
    loadBuses();
    loadBusRoutes();
    loadBusDepots();
    loadBusTerminals();
    loadBusDrivers();
    loadRouteRuns();

    // Setup toggle buttons
    setupToggleButtons();

    // Get button and output area elements
    const solveButton = document.getElementById('solveButton');
    const scheduleOutputElement = document.getElementById('scheduleOutput');

    if (solveButton && scheduleOutputElement) {
        solveButton.addEventListener('click', async () => {
            scheduleOutputElement.textContent = 'Solving... Please wait.';
            if (scheduleOutputElement.classList.contains('hidden')) {
                scheduleOutputElement.classList.remove('hidden');
                const scheduleButton = document.querySelector('.toggle-data-btn[data-target="scheduleOutput"]');
                if (scheduleButton) scheduleButton.textContent = 'Hide Schedule';
            }

            try {
                const response = await fetch('/api/schedule/solve', {
                    method: 'POST',
                    headers: { 'Accept': 'application/json' }
                });

                if (!response.ok) {
                    const errorText = await response.text();
                    throw new Error(`HTTP error! status: ${response.status} ${response.statusText}. Server says: ${errorText}`);
                }

                const solution = await response.json();
                
                if (solution && solution.assignedRouteRuns) {
                    // NEW: Display score information at the top
                    let displayText = `Schedule Score: ${solution.score !== undefined ? solution.score : 'N/A'}\n`;
                    displayText += `(Buses Used: ${solution.totalBusesUsedCount !== undefined ? solution.totalBusesUsedCount : 'N/A'}, Unassigned Runs: ${solution.unassignedRunCount !== undefined ? solution.unassignedRunCount : 'N/A'})\n`;
                    displayText += `Score Explanation: ${solution.scoreExplanation || ''}\n\n`;
                    
                    if (solution.assignedRouteRuns.length === 0) {
                         displayText += "No route runs found in the solution or to schedule.";
                    } else {
                        displayText += "Assigned Route Runs:\n"; // Heading for the runs
                        solution.assignedRouteRuns.forEach(run => {
                            displayText += `Run ID: ${run.id}\n`;
                            displayText += `  Route: ${run.busRouteId}\n`;
                            displayText += `  Departure: ${run.departureTime}, Arrival: ${run.arrivalTime}\n`;
                            displayText += `  Assigned Bus: ${run.assignedBusId || 'N/A'}\n`;
                            displayText += `  Assigned Driver: ${run.assignedDriverId || 'N/A'}\n\n`;
                        });
                    }
                    scheduleOutputElement.textContent = displayText;
                } else {
                    scheduleOutputElement.textContent = 'Received an empty or invalid solution from the server.';
                }

            } catch (error) {
                console.error('Error solving schedule:', error);
                scheduleOutputElement.textContent = `Error solving schedule: ${error.message}`;
            }
        });
    } else { /* ... unchanged error logging ... */ }
});

// --- NEW FUNCTION to setup toggle buttons (Your existing function is fine) ---
function setupToggleButtons() {
    const toggleButtons = document.querySelectorAll('.toggle-data-btn');
    toggleButtons.forEach(button => {
        button.addEventListener('click', () => {
            const targetId = button.dataset.target;
            const targetElement = document.getElementById(targetId);
            if (targetElement) {
                targetElement.classList.toggle('hidden');
                if (targetElement.classList.contains('hidden')) {
                    button.textContent = `Show ${targetId.replace('List', '').replace('Output', '')}`;
                } else {
                    button.textContent = `Hide ${targetId.replace('List', '').replace('Output', '')}`;
                }
            } else { console.error(`Target element with ID "${targetId}" not found for button.`); }
        });
        const targetElement = document.getElementById(button.dataset.target);
        if (targetElement && targetElement.classList.contains('hidden')) {
             button.textContent = `Show ${button.dataset.target.replace('List', '').replace('Output', '')}`;
        } else if (targetElement) {
            button.textContent = `Hide ${button.dataset.target.replace('List', '').replace('Output', '')}`;
        }
    });
}

// --- Data Fetching and Population Functions (Your existing functions are fine) ---
async function fetchData(url) { /* ... unchanged ... */ 
    try {
        const response = await fetch(url);
        if (!response.ok) { throw new Error(`HTTP error! status: ${response.status}`); }
        return await response.json();
    } catch (error) { console.error(`Could not fetch data from ${url}:`, error); return []; }
}
function populateList(elementId, data, formatter) { /* ... unchanged ... */ 
    const listElement = document.getElementById(elementId);
    if (!listElement) { console.error(`List element with ID "${elementId}" not found.`); return; }
    listElement.innerHTML = '';
    if (!data || data.length === 0) { listElement.innerHTML = '<li>No data available or error loading.</li>'; return; }
    data.forEach(item => { const li = document.createElement('li'); li.textContent = formatter(item); listElement.appendChild(li); });
}
async function loadAllStops() { /* ... unchanged ... */ 
    const busStops = await fetchData('/api/data/allstops');
    populateList('busStopsList', busStops, stop => `ID: ${stop.id}, Name: ${stop.name}, Type: ${stop.maxCapacity ? 'Depot' : (stop.id.startsWith('T') ? 'Terminal' : 'Stop')}`);
}
async function loadBuses() { /* ... unchanged ... */ 
    const buses = await fetchData('/api/data/buses');
    populateList('busesList', buses, bus => `ID: ${bus.busId}, Plate: ${bus.numberPlate}, Depot: ${bus.depotId}, Range: ${bus.rangeKm}km, Charge: ${bus.currentChargeKm}km, Location: ${bus.currentLocationId}`);
}
async function loadBusRoutes() { /* ... unchanged ... */ 
    const busRoutes = await fetchData('/api/data/busroutes');
    populateList('busRoutesList', busRoutes, route => `ID: ${route.id}, Name: ${route.name}, From: ${route.startTerminalId} To: ${route.endTerminalId}, Stops: [${route.stopIds ? route.stopIds.join(', ') : ''}], Distance: ${route.totalDistanceKm}km, Time: ${route.travelTimeMinutes}min`);
}
async function loadBusDepots() { /* ... unchanged ... */ 
    const busDepots = await fetchData('/api/data/depots');
    populateList('busDepotsList', busDepots, depot => `ID: ${depot.id}, Name: ${depot.name}, Capacity: ${depot.maxCapacity}`);
}
async function loadBusTerminals() { /* ... unchanged ... */ 
    const busTerminals = await fetchData('/api/data/terminals');
    populateList('busTerminalsList', busTerminals, terminal => `ID: ${terminal.id}, Name: ${terminal.name}`);
}
async function loadBusDrivers() { /* ... unchanged ... */ 
    const busDrivers = await fetchData('/api/data/drivers');
    populateList('busDriversList', busDrivers, driver => `ID: ${driver.id}, Name: ${driver.name}`);
}
async function loadRouteRuns() { /* ... unchanged ... */ 
    const routeRuns = await fetchData('/api/data/routeruns');
    populateList('routeRunsList', routeRuns, run => `ID: ${run.id}, Route: ${run.busRouteId}, Departure: ${run.departureTime}, Arrival: ${run.arrivalTime}, Bus: ${run.assignedBusId || 'N/A'}, Driver: ${run.assignedDriverId || 'N/A'}`);
}