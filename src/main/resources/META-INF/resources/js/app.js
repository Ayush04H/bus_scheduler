// ... (Keep ALL your existing JS code: DOMContentLoaded, renderActivityLogs, fetchData, populateList, loadX functions) ...

// Modify setupToggleButtons to handle the icon change
function getDisplayLabel(targetId) {
    let label = targetId.replace('List', '').replace('Output', '');
    // Regex to insert space before capital letters (and numbers if they follow a letter)
    label = label.replace(/([a-z])([A-Z0-9])/g, '$1 $2');
    label = label.replace(/([A-Z])([A-Z][a-z])/g, '$1 $2'); // Handles cases like "BusStops" -> "Bus Stops"
    return label.charAt(0).toUpperCase() + label.slice(1);
}

function setupToggleButtons() { 
    const toggleButtons = document.querySelectorAll('.toggle-data-btn');
    toggleButtons.forEach(button => {
        const targetId = button.dataset.target;
        const targetElement = document.getElementById(targetId);
        const iconElement = button.querySelector('.toggle-icon'); // Get the icon span

        if (!targetElement) {
            console.error(`Target element with ID "${targetId}" not found for button:`, button);
            return;
        }

        const updateButton = () => {
            const labelName = getDisplayLabel(targetId);
            const isHidden = targetElement.classList.contains('hidden');
            
            button.innerHTML = ''; // Clear button content
            const newIconSpan = document.createElement('span');
            newIconSpan.className = 'toggle-icon';
            newIconSpan.textContent = isHidden ? '+' : '−'; // Minus sign for expanded
            
            button.appendChild(newIconSpan);
            button.appendChild(document.createTextNode(isHidden ? ` Show ${labelName}` : ` Hide ${labelName}`));

            if (isHidden) {
                button.classList.remove('expanded');
            } else {
                button.classList.add('expanded');
            }
        };

        updateButton(); // Set initial text and icon

        button.addEventListener('click', () => {
            targetElement.classList.toggle('hidden');
            // Animate toggle (optional, can be done with CSS transitions on max-height)
            // If using CSS for max-height transition, this JS toggle is enough.
            updateButton();
        });
    });
}

// The rest of your app.js (DOMContentLoaded, renderActivityLogs, fetchData, populateList, loadX functions)
// should remain unchanged from the version that correctly displays the activity log tables.
// I'm pasting the DOMContentLoaded again just to be sure about the button text update part
document.addEventListener('DOMContentLoaded', () => {
    loadAllStops(); 
    loadBuses(); 
    loadBusRoutes(); 
    loadBusDepots();
    loadBusTerminals(); 
    loadBusDrivers(); 
    loadRouteRuns();
    setupToggleButtons(); // This will now use the enhanced version

    const solveButton = document.getElementById('solveButton');
    const scheduleSummaryOutputElement = document.getElementById('scheduleSummaryOutput');
    const assignedRunsOutputElement = document.getElementById('assignedRunsOutput');
    const fullActivityLogOutputElement = document.getElementById('fullActivityLogOutput');

    if (solveButton && scheduleSummaryOutputElement && assignedRunsOutputElement && fullActivityLogOutputElement) {
        solveButton.addEventListener('click', async () => {
            scheduleSummaryOutputElement.textContent = 'Solving...';
            assignedRunsOutputElement.textContent = 'Please wait...';
            fullActivityLogOutputElement.innerHTML = 'Loading activity logs...';

            [scheduleSummaryOutputElement, assignedRunsOutputElement, fullActivityLogOutputElement].forEach(el => {
                if (el.classList.contains('hidden')) {
                    el.classList.remove('hidden');
                    // Update button associated with this element
                    const btn = document.querySelector(`.toggle-data-btn[data-target="${el.id}"]`);
                    if (btn) {
                         const labelName = getDisplayLabel(el.id);
                         btn.innerHTML = ''; // Clear button content
                         const newIconSpan = document.createElement('span');
                         newIconSpan.className = 'toggle-icon';
                         newIconSpan.textContent = '−'; // Minus sign for expanded
                         btn.appendChild(newIconSpan);
                         btn.appendChild(document.createTextNode(` Hide ${labelName}`));
                         btn.classList.add('expanded');
                    }
                }
            });

            try {
                const response = await fetch('/api/schedule/solve', {
                    method: 'POST', headers: { 'Accept': 'application/json' }
                });
                if (!response.ok) { 
                    const errorText = await response.text();
                    throw new Error(`HTTP error! status: ${response.status} ${response.statusText}. Server says: ${errorText}`);
                }
                const solution = await response.json();
                
                let summaryText = `Schedule Score: ${solution.score !== undefined ? solution.score : 'N/A'}\n`;
                summaryText += `(Buses Used: ${solution.totalBusesUsedCount !== undefined ? solution.totalBusesUsedCount : 'N/A'}, Unassigned Runs: ${solution.unassignedRunCount !== undefined ? solution.unassignedRunCount : 'N/A'})\n`;
                summaryText += `Score Explanation: ${solution.scoreExplanation || ''}\n\n`;
                scheduleSummaryOutputElement.textContent = summaryText;

                if (solution.assignedRouteRuns && solution.assignedRouteRuns.length > 0) {
                    let runsText = "Assigned Route Runs Details:\n\n";
                    solution.assignedRouteRuns.forEach(run => {
                        runsText += `Run ID: ${run.id} (Route: ${run.busRouteId})\n`;
                        runsText += `  Time: ${run.departureTime} - ${run.arrivalTime}\n`;
                        runsText += `  Bus: ${run.assignedBusId || 'N/A'}, Driver: ${run.assignedDriverId || 'N/A'}\n\n`;
                    });
                    assignedRunsOutputElement.textContent = runsText;
                } else {
                    assignedRunsOutputElement.textContent = "No route runs assigned or found in the solution.";
                }

                fullActivityLogOutputElement.innerHTML = ''; 
                if (solution.activityLog && solution.activityLog.length > 0) {
                    renderActivityLogs(solution.activityLog, fullActivityLogOutputElement);
                } else {
                    fullActivityLogOutputElement.textContent = 'No detailed activity logs available.';
                }

            } catch (error) { 
                console.error('Error solving schedule:', error);
                scheduleSummaryOutputElement.textContent = `Error: ${error.message}`;
                assignedRunsOutputElement.textContent = 'Error retrieving data.';
                fullActivityLogOutputElement.textContent = 'Error retrieving data.';
            }
        });
    } else {
        if (!solveButton) console.error("Solve button (#solveButton) not found!");
        // ... other element checks
    }
});


// renderActivityLogs, fetchData, populateList, loadX functions as per your last working version
// (The version I provided in the previous response which correctly renders activity tables)
// For brevity, I'm not repeating them here, but ensure they are the ones that were working.
// Specifically, renderActivityLogs is crucial for the activity display.
// ... (paste your working renderActivityLogs, fetchData, populateList, and all loadX functions here) ...
// Ensure your `renderActivityLogs` from the previous successful step is included here.
// All the `loadX` functions (loadAllStops, loadBuses, etc.) and `fetchData`, `populateList`
// from your last fully working `app.js` should be retained. The key change is in `setupToggleButtons`
// and the `DOMContentLoaded` listener's `solveButton` part for updating toggle buttons.

// Make sure to include the FULL, PREVIOUSLY WORKING renderActivityLogs, fetchData, populateList, and all loadX functions.
// The snippet below just shows where they go relative to the modified setupToggleButtons.

function renderActivityLogs(logs, containerElement) {
    // ... (This should be your fully working version from the previous step) ...
    const logsByEntity = {};
    logs.forEach(log => {
        const key = `${log.entityType}-${log.entityId}`;
        if (!logsByEntity[key]) {
            logsByEntity[key] = { entityType: log.entityType, entityId: log.entityId, activities: [] };
        }
        logsByEntity[key].activities.push(log);
    });
    for (const key in logsByEntity) {
        logsByEntity[key].activities.sort((a, b) => (a.startTime < b.startTime ? -1 : 1));
    }
    const sortedEntityKeys = Object.keys(logsByEntity).sort((keyA, keyB) => {
        const entityA = logsByEntity[keyA];
        const entityB = logsByEntity[keyB];
        if (entityA.entityType !== entityB.entityType) { return entityA.entityType === 'BUS' ? -1 : 1; }
        return entityA.entityId.localeCompare(entityB.entityId);
    });
    containerElement.innerHTML = ''; // Clear previous logs
    sortedEntityKeys.forEach(key => {
        const entityLog = logsByEntity[key];
        const block = document.createElement('div'); block.className = 'entity-activity-block';
        const title = document.createElement('h4'); title.textContent = `${entityLog.entityType}: ${entityLog.entityId}`; block.appendChild(title);
        const table = document.createElement('table'); table.className = 'activity-table';
        const thead = table.createTHead(); const headerRow = thead.insertRow();
        const headers = ['Start', 'End', 'Activity', 'Description', 'Location (S->E)', 'Charge (S->E)'];
        headers.forEach(text => { const th = document.createElement('th'); th.textContent = text; headerRow.appendChild(th); });
        const tbody = table.createTBody();
        entityLog.activities.forEach(act => {
            const row = tbody.insertRow();
            row.insertCell().textContent = act.startTime || 'N/A';
            row.insertCell().textContent = act.endTime || 'N/A';
            const activityCell = row.insertCell(); activityCell.textContent = act.activityType || 'N/A';
            if(act.activityType) { activityCell.classList.add(`activity-type-${act.activityType}`); }
            row.insertCell().textContent = act.description || '';
            row.insertCell().textContent = `${act.startLocationId || ''}${act.startLocationId && act.endLocationId ? ' -> ' : ''}${act.endLocationId || ''}`;
            let chargeText = '';
            if (act.entityType === 'BUS' && (act.startChargeKm !== null || act.endChargeKm !== null)) {
                chargeText = `${act.startChargeKm !== null ? act.startChargeKm + 'km' : '-'} -> ${act.endChargeKm !== null ? act.endChargeKm + 'km' : '-'}`;
            }
            row.insertCell().textContent = chargeText;
            Array.from(row.cells).forEach((cell, index) => { cell.setAttribute('data-label', headers[index]); });
        });
        block.appendChild(table); containerElement.appendChild(block);
    });
}

async function fetchData(url) { 
    try {
        const response = await fetch(url);
        if (!response.ok) { throw new Error(`HTTP error! status: ${response.status}`); }
        return await response.json();
    } catch (error) { console.error(`Could not fetch data from ${url}:`, error); return []; }
}
function populateList(elementId, data, formatter) { 
    const listElement = document.getElementById(elementId);
    if (!listElement) { console.error(`List element with ID "${elementId}" not found.`); return; }
    listElement.innerHTML = '<li>Loading...</li>'; 
    if (!data || !Array.isArray(data) || data.length === 0) { // Added Array.isArray check
        listElement.innerHTML = '<li>No data available or error loading.</li>'; 
        return; 
    }
    listElement.innerHTML = ''; 
    data.forEach(item => { 
        const li = document.createElement('li'); 
        li.textContent = formatter(item); 
        listElement.appendChild(li); 
    });
}
async function loadAllStops() { 
    document.getElementById('busStopsList').innerHTML = '<li>Loading Bus Stops...</li>';
    const data = await fetchData('/api/data/allstops');
    populateList('busStopsList', data, stop => `ID: ${stop.id}, Name: ${stop.name}, Type: ${stop.maxCapacity ? 'Depot' : (stop.id.startsWith('T') ? 'Terminal' : 'Stop')}`);
}
async function loadBuses() { 
    document.getElementById('busesList').innerHTML = '<li>Loading Buses...</li>';
    const data = await fetchData('/api/data/buses');
    populateList('busesList', data, bus => `ID: ${bus.busId}, Plate: ${bus.numberPlate}, Depot: ${bus.depotId}, Range: ${bus.rangeKm}km, Charge: ${bus.currentChargeKm}km, Location: ${bus.currentLocationId}`);
}
async function loadBusRoutes() { 
    document.getElementById('busRoutesList').innerHTML = '<li>Loading Bus Routes...</li>';
    const data = await fetchData('/api/data/busroutes');
    populateList('busRoutesList', data, route => `ID: ${route.id}, Name: ${route.name}, From: ${route.startTerminalId} To: ${route.endTerminalId}, Stops: [${route.stopIds ? route.stopIds.join(', ') : ''}], Distance: ${route.totalDistanceKm}km, Time: ${route.travelTimeMinutes}min`);
}
async function loadBusDepots() { 
    document.getElementById('busDepotsList').innerHTML = '<li>Loading Bus Depots...</li>';
    const data = await fetchData('/api/data/depots');
    populateList('busDepotsList', data, depot => `ID: ${depot.id}, Name: ${depot.name}, Capacity: ${depot.maxCapacity}`);
}
async function loadBusTerminals() { 
    document.getElementById('busTerminalsList').innerHTML = '<li>Loading Bus Terminals...</li>';
    const data = await fetchData('/api/data/terminals');
    populateList('busTerminalsList', data, terminal => `ID: ${terminal.id}, Name: ${terminal.name}`);
}
async function loadBusDrivers() { 
    document.getElementById('busDriversList').innerHTML = '<li>Loading Bus Drivers...</li>';
    const data = await fetchData('/api/data/drivers'); 
    populateList('busDriversList', data, driver => `ID: ${driver.id}, Name: ${driver.name}`);
}
async function loadRouteRuns() { 
    document.getElementById('routeRunsList').innerHTML = '<li>Loading Route Runs...</li>';
    const data = await fetchData('/api/data/routeruns');
    populateList('routeRunsList', data, run => `ID: ${run.id}, Route: ${run.busRouteId}, Departure: ${run.departureTime}, Arrival: ${run.arrivalTime}, Bus: ${run.assignedBusId || 'N/A'}, Driver: ${run.assignedDriverId || 'N/A'}`);
}