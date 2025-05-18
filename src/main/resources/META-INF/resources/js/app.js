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
    setupToggleButtons(); // <--- NEW FUNCTION CALL

    // Get button and output area elements
    const solveButton = document.getElementById('solveButton');
    const scheduleOutputElement = document.getElementById('scheduleOutput');

    // Add event listener to the solve button
    if (solveButton && scheduleOutputElement) {
        solveButton.addEventListener('click', async () => {
            scheduleOutputElement.textContent = 'Solving... Please wait.';
            // Make sure the schedule section is visible when solving
            if (scheduleOutputElement.classList.contains('hidden')) {
                scheduleOutputElement.classList.remove('hidden');
                // Optionally update its button text
                const scheduleButton = document.querySelector('.toggle-data-btn[data-target="scheduleOutput"]');
                if (scheduleButton) scheduleButton.textContent = 'Hide Schedule';
            }

            try {
                const response = await fetch('/api/schedule/solve', {
                    method: 'POST',
                    headers: {
                        'Accept': 'application/json'
                    }
                });

                if (!response.ok) {
                    const errorText = await response.text(); // Get more error details
                    throw new Error(`HTTP error! status: ${response.status} ${response.statusText}. Server says: ${errorText}`);
                }

                const solution = await response.json();
                
                if (solution && solution.assignedRouteRuns) {
                    if (solution.assignedRouteRuns.length === 0) {
                         scheduleOutputElement.textContent = "No route runs found in the solution or to schedule.";
                    } else {
                        let displayText = `Schedule:\n\n`; // Simplified heading
                        solution.assignedRouteRuns.forEach(run => {
                            displayText += `Run ID: ${run.id}\n`;
                            displayText += `  Route: ${run.busRouteId}\n`;
                            displayText += `  Departure: ${run.departureTime}, Arrival: ${run.arrivalTime}\n`;
                            displayText += `  Assigned Bus: ${run.assignedBusId || 'N/A'}\n`;
                            displayText += `  Assigned Driver: ${run.assignedDriverId || 'N/A'}\n\n`;
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

// --- NEW FUNCTION to setup toggle buttons ---
function setupToggleButtons() {
    const toggleButtons = document.querySelectorAll('.toggle-data-btn');
    toggleButtons.forEach(button => {
        button.addEventListener('click', () => {
            const targetId = button.dataset.target; // Get the ID from data-target attribute
            const targetElement = document.getElementById(targetId);

            if (targetElement) {
                targetElement.classList.toggle('hidden');
                // Update button text
                if (targetElement.classList.contains('hidden')) {
                    button.textContent = `Show ${targetId.replace('List', '').replace('Output', '')}`; // e.g., Show BusStops
                } else {
                    button.textContent = `Hide ${targetId.replace('List', '').replace('Output', '')}`; // e.g., Hide BusStops
                }
            } else {
                console.error(`Target element with ID "${targetId}" not found for button.`);
            }
        });
        // Initialize button text based on current state (all are hidden initially)
        const targetElement = document.getElementById(button.dataset.target);
        if (targetElement && targetElement.classList.contains('hidden')) {
             button.textContent = `Show ${button.dataset.target.replace('List', '').replace('Output', '')}`;
        } else if (targetElement) {
            button.textContent = `Hide ${button.dataset.target.replace('List', '').replace('Output', '')}`;
        }
    });
}


// --- Data Fetching and Population Functions (Keep these as they are, with one minor adjustment if needed) ---

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
    if (!listElement) {
        console.error(`List element with ID "${elementId}" not found.`);
        return;
    }

    listElement.innerHTML = ''; // Clear "Loading..."
    if (!data || data.length === 0) { 
        listElement.innerHTML = '<li>No data available or error loading.</li>';
        // Even if no data, ensure the section is not hidden IF the button was clicked to show it
        // This is handled by the button's toggle logic.
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