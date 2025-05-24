document.addEventListener('DOMContentLoaded', () => {
    loadAllStops(); 
    loadBuses(); 
    loadBusRoutes(); 
    loadBusDepots();
    loadBusTerminals(); 
    loadBusDrivers(); 
    loadRouteRuns();
    setupToggleButtons();

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
                    const btn = document.querySelector(`.toggle-data-btn[data-target="${el.id}"]`);
                    if (btn) btn.textContent = `Hide ${getDisplayLabel(el.id)}`;
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
        if (!scheduleSummaryOutputElement) console.error("Schedule summary output element (#scheduleSummaryOutput) not found!");
        if (!assignedRunsOutputElement) console.error("Assigned runs output element (#assignedRunsOutput) not found!");
        if (!fullActivityLogOutputElement) console.error("Full activity log output element (#fullActivityLogOutput) not found!");
    }
});


function renderActivityLogs(logs, containerElement) {
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

function getDisplayLabel(targetId) {
    let label = targetId.replace('List', '').replace('Output', '');
    label = label.replace(/([A-Z0-9])/g, ' $1').trim(); 
    return label.charAt(0).toUpperCase() + label.slice(1);
}

function setupToggleButtons() { 
    const toggleButtons = document.querySelectorAll('.toggle-data-btn');
    toggleButtons.forEach(button => {
        const targetId = button.dataset.target;
        const targetElement = document.getElementById(targetId);
        if (!targetElement) {
            console.error(`Target element with ID "${targetId}" not found for button:`, button);
            return;
        }
        const updateButtonText = () => {
            const labelName = getDisplayLabel(targetId);
            button.textContent = targetElement.classList.contains('hidden') ?
                `Show ${labelName}` : `Hide ${labelName}`;
        };
        updateButtonText();
        button.addEventListener('click', () => {
            targetElement.classList.toggle('hidden');
            updateButtonText();
        });
    });
}

async function fetchData(url) { 
    try {
        console.log(`[fetchData] Fetching from: ${url}`); // DEBUG
        const response = await fetch(url);
        console.log(`[fetchData] Response for ${url} - Status: ${response.status}, OK: ${response.ok}`); // DEBUG
        if (!response.ok) { 
            const errorBody = await response.text();
            console.error(`[fetchData] HTTP error for ${url}! Status: ${response.status}. Body: ${errorBody}`);
            throw new Error(`HTTP error! status: ${response.status}. Server text: ${errorBody}`); 
        }
        const jsonData = await response.json();
        console.log(`[fetchData] Parsed JSON for ${url} - Type: ${typeof jsonData}, IsArray: ${Array.isArray(jsonData)}. Length: ${Array.isArray(jsonData) ? jsonData.length : 'N/A'}. Data:`, jsonData); // DEBUG
        return jsonData;
    } catch (error) { 
        console.error(`[fetchData] CATCH block for ${url}:`, error); 
        return []; // Return empty array on fetch/parse error to prevent further issues
    }
}

function populateList(elementId, data, formatter) { 
    console.log(`[populateList] START for: ${elementId}. Type of data: ${typeof data}. Is Array: ${Array.isArray(data)}. Data:`, data); // ENHANCED DEBUG
    const listElement = document.getElementById(elementId);
    if (!listElement) { 
        console.error(`[populateList] List element with ID "${elementId}" NOT FOUND.`);
        return; 
    }

    if (!Array.isArray(data)) { // MORE ROBUST CHECK
        console.error(`[populateList] Data for ${elementId} is not an array. Received (type ${typeof data}):`, data);
        listElement.innerHTML = '<li>Error: Invalid data format received.</li>'; 
        return;
    }

    if (data.length === 0) { 
        listElement.innerHTML = '<li>No data available or error loading.</li>'; 
        console.log(`[populateList] No data for ${elementId}.`);
        return; 
    }

    listElement.innerHTML = ''; 
    console.log(`[populateList] Cleared content for ${elementId}. Populating with ${data.length} items.`);
    data.forEach(item => { 
        const li = document.createElement('li'); 
        const textContent = formatter(item);
        li.textContent = textContent;
        listElement.appendChild(li); 
    });
    console.log(`[populateList] FINISHED populating ${elementId}.`);
}

// --- Load Data Functions with pre-fetch "Loading..." message ---
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
    const data = await fetchData('/api/data/drivers'); // Renamed to 'data' for consistency
    populateList('busDriversList', data, driver => `ID: ${driver.id}, Name: ${driver.name}`);
}
async function loadRouteRuns() { 
    document.getElementById('routeRunsList').innerHTML = '<li>Loading Route Runs...</li>';
    const data = await fetchData('/api/data/routeruns');
    populateList('routeRunsList', data, run => `ID: ${run.id}, Route: ${run.busRouteId}, Departure: ${run.departureTime}, Arrival: ${run.arrivalTime}, Bus: ${run.assignedBusId || 'N/A'}, Driver: ${run.assignedDriverId || 'N/A'}`);
}