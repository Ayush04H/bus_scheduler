document.addEventListener('DOMContentLoaded', () => {
    // Load initial data
    loadAllStops();
    loadBuses();
    loadBusRoutes();
    loadBusDepots();
    loadBusTerminals();
    loadBusDrivers();
    loadRouteRuns();

    // Get button and output area elements
    const solveButton = document.getElementById('solveButton');
    const scheduleOutputElement = document.getElementById('scheduleOutput');

    // Add event listener to the solve button
    if (solveButton && scheduleOutputElement) {
        solveButton.addEventListener('click', async () => { // Make the handler async
            scheduleOutputElement.textContent = 'Solving... Please wait.'; // Update status
            try {
                // Call the backend endpoint to solve the schedule
                const response = await fetch('/api/schedule/solve', {
                    method: 'POST', // Match the @POST in SchedulingResource
                    headers: {
                        'Accept': 'application/json'
                        // 'Content-Type': 'application/json' // Not needed for this POST without a body
                    }
                });

                if (!response.ok) {
                    // If response is not OK, throw an error to be caught by the catch block
                    throw new Error(`HTTP error! status: ${response.status} ${response.statusText}`);
                }

                const solution = await response.json(); // Parse the JSON response
                
                // Display the solution in a structured way
                if (solution && solution.assignedRouteRuns) {
                    if (solution.assignedRouteRuns.length === 0) {
                         scheduleOutputElement.textContent = "No route runs found in the solution or to schedule.";
                    } else {
                        let displayText = `Schedule (Placeholder - No actual assignments yet):\n\n`;
                        solution.assignedRouteRuns.forEach(run => {
                            displayText += `Run ID: ${run.id}\n`;
                            displayText += `  Route: ${run.busRouteId}\n`;
                            displayText += `  Departure: ${run.departureTime}, Arrival: ${run.arrivalTime}\n`;
                            displayText += `  Assigned Bus: ${run.assignedBusId || 'N/A'}\n`; // Will be N/A for now
                            displayText += `  Assigned Driver: ${run.assignedDriverId || 'N/A'}\n\n`; // Will be N/A for now
                        });
                        scheduleOutputElement.textContent = displayText;
                    }
                } else {
                    scheduleOutputElement.textContent = 'Received an empty or invalid solution from the server.';
                }

            } catch (error) {
                console.error('Error solving schedule:', error);
                scheduleOutputElement.textContent = `Error solving schedule: ${error.message}`;
            }
        });
    } else {
        if (!solveButton) console.error("Solve button not found!");
        if (!scheduleOutputElement) console.error("Schedule output element not found!");
    }
});

// --- Data Fetching and Population Functions (Keep these as they are) ---

async function fetchData(url) {
    try {
        const response = await fetch(url);
        if (!response.ok) {
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        return await response.json();
    } catch (error) {
        console.error(`Could not fetch data from ${url}:`, error);
        return []; // Return empty array on error
    }
}

function populateList(elementId, data, formatter) {
    const listElement = document.getElementById(elementId);
    if (!listElement) return;

    listElement.innerHTML = ''; // Clear "Loading..."
    if (!data || data.length === 0) { 
        listElement.innerHTML = '<li>No data available or error loading.</li>';
        return;
    }
    data.forEach(item => {
        const li = document.createElement('li');
        li.textContent = formatter(item);
        listElement.appendChild(li);
    });
}

async function loadAllStops() {
    const busStops = await fetchData('/api/data/allstops');
    populateList('busStopsList', busStops, stop => `ID: ${stop.id}, Name: ${stop.name}, Type: ${stop.maxCapacity ? 'Depot' : (stop.id.startsWith('T') ? 'Terminal' : 'Stop')}`);
}

async function loadBuses() {
    const buses = await fetchData('/api/data/buses');
    populateList('busesList', buses, bus => 
        `ID: ${bus.busId}, Plate: ${bus.numberPlate}, Depot: ${bus.depotId}, Range: ${bus.rangeKm}km, Charge: ${bus.currentChargeKm}km, Location: ${bus.currentLocationId}`
    );
}

async function loadBusRoutes() {
    const busRoutes = await fetchData('/api/data/busroutes');
    populateList('busRoutesList', busRoutes, route => 
        `ID: ${route.id}, Name: ${route.name}, From: ${route.startTerminalId} To: ${route.endTerminalId}, Stops: [${route.stopIds ? route.stopIds.join(', ') : ''}], Distance: ${route.totalDistanceKm}km, Time: ${route.travelTimeMinutes}min`
    );
}

async function loadBusDepots() {
    const busDepots = await fetchData('/api/data/depots');
    populateList('busDepotsList', busDepots, depot => 
        `ID: ${depot.id}, Name: ${depot.name}, Capacity: ${depot.maxCapacity}`
    );
}

async function loadBusTerminals() {
    const busTerminals = await fetchData('/api/data/terminals');
    populateList('busTerminalsList', busTerminals, terminal => 
        `ID: ${terminal.id}, Name: ${terminal.name}`
    );
}

async function loadBusDrivers() {
    const busDrivers = await fetchData('/api/data/drivers');
    populateList('busDriversList', busDrivers, driver => 
        `ID: ${driver.id}, Name: ${driver.name}`
    );
}

async function loadRouteRuns() {
    const routeRuns = await fetchData('/api/data/routeruns');
    populateList('routeRunsList', routeRuns, run => 
        `ID: ${run.id}, Route: ${run.busRouteId}, Departure: ${run.departureTime}, Arrival: ${run.arrivalTime}, Bus: ${run.assignedBusId || 'N/A'}, Driver: ${run.assignedDriverId || 'N/A'}`
    );
}