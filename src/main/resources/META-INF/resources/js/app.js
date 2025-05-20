document.addEventListener('DOMContentLoaded', () => {
    // ... (your existing DOMContentLoaded setup is good) ...
    loadAllStops(); loadBuses(); loadBusRoutes(); loadBusDepots();
    loadBusTerminals(); loadBusDrivers(); loadRouteRuns();
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
                    if (btn) btn.textContent = `Hide ${el.id.replace('Output', '').replace('List', '')}`;
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
                
                // 1. Display Schedule Summary
                let summaryText = `Schedule Score: ${solution.score !== undefined ? solution.score : 'N/A'}\n`;
                summaryText += `(Buses Used: ${solution.totalBusesUsedCount !== undefined ? solution.totalBusesUsedCount : 'N/A'}, Unassigned Runs: ${solution.unassignedRunCount !== undefined ? solution.unassignedRunCount : 'N/A'})\n`;
                summaryText += `Score Explanation: ${solution.scoreExplanation || ''}\n\n`;
                scheduleSummaryOutputElement.textContent = summaryText;

                // 2. Display Assigned Route Runs
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

                // 3. Display Detailed Activity Logs
                fullActivityLogOutputElement.innerHTML = ''; 
                if (solution.activityLog && solution.activityLog.length > 0) {
                    renderActivityLogs(solution.activityLog, fullActivityLogOutputElement);
                } else {
                    fullActivityLogOutputElement.textContent = 'No detailed activity logs available.';
                }

            } catch (error) { /* ... error handling ... */ 
                console.error('Error solving schedule:', error);
                scheduleSummaryOutputElement.textContent = `Error: ${error.message}`;
                assignedRunsOutputElement.textContent = '';
                fullActivityLogOutputElement.textContent = '';
            }
        });
    } 
});


function renderActivityLogs(logs, containerElement) {
    const logsByEntity = {};
    logs.forEach(log => {
        const key = `${log.entityType}-${log.entityId}`;
        if (!logsByEntity[key]) {
            logsByEntity[key] = {
                entityType: log.entityType,
                entityId: log.entityId,
                activities: []
            };
        }
        logsByEntity[key].activities.push(log);
    });

    for (const key in logsByEntity) {
        logsByEntity[key].activities.sort((a, b) => (a.startTime < b.startTime ? -1 : 1));
    }
    
    // Sort entity blocks to show BUS first, then DRIVER, then by ID
    const sortedEntityKeys = Object.keys(logsByEntity).sort((keyA, keyB) => {
        const entityA = logsByEntity[keyA];
        const entityB = logsByEntity[keyB];
        if (entityA.entityType !== entityB.entityType) {
            return entityA.entityType === 'BUS' ? -1 : 1; // BUS comes before DRIVER
        }
        return entityA.entityId.localeCompare(entityB.entityId); // Then sort by ID
    });


    sortedEntityKeys.forEach(key => { // Iterate over sorted keys
        const entityLog = logsByEntity[key];
        const block = document.createElement('div');
        block.className = 'entity-activity-block';

        const title = document.createElement('h4');
        title.textContent = `${entityLog.entityType}: ${entityLog.entityId}`;
        block.appendChild(title);

        const table = document.createElement('table');
        table.className = 'activity-table';
        const thead = table.createTHead();
        const headerRow = thead.insertRow();
        const headers = ['Start', 'End', 'Activity', 'Description', 'Location (S->E)', 'Charge (S->E)'];
        headers.forEach(text => {
            const th = document.createElement('th');
            th.textContent = text;
            headerRow.appendChild(th);
        });
        
        const tbody = table.createTBody();
        entityLog.activities.forEach(act => {
            const row = tbody.insertRow();
            
            row.insertCell().textContent = act.startTime || 'N/A';
            row.insertCell().textContent = act.endTime || 'N/A';
            
            const activityCell = row.insertCell();
            activityCell.textContent = act.activityType || 'N/A';
            if(act.activityType) { // Add class for color coding
                activityCell.classList.add(`activity-type-${act.activityType}`);
            }

            row.insertCell().textContent = act.description || '';
            row.insertCell().textContent = `${act.startLocationId || ''}${act.startLocationId && act.endLocationId ? ' -> ' : ''}${act.endLocationId || ''}`;
            
            let chargeText = '';
            if (act.entityType === 'BUS' && (act.startChargeKm !== null || act.endChargeKm !== null)) {
                chargeText = `${act.startChargeKm !== null ? act.startChargeKm + 'km' : '-'} -> ${act.endChargeKm !== null ? act.endChargeKm + 'km' : '-'}`;
            }
            row.insertCell().textContent = chargeText;

            Array.from(row.cells).forEach((cell, index) => {
                cell.setAttribute('data-label', headers[index]);
            });
        });
        block.appendChild(table);
        containerElement.appendChild(block);
    });
}

// --- Your existing setupToggleButtons and loadData functions ---
// (Make sure they are exactly as in your last working version)
function setupToggleButtons() { 
    const toggleButtons = document.querySelectorAll('.toggle-data-btn');
    toggleButtons.forEach(button => {
        button.addEventListener('click', () => {
            const targetId = button.dataset.target;
            const targetElement = document.getElementById(targetId);
            if (targetElement) {
                targetElement.classList.toggle('hidden');
                button.textContent = targetElement.classList.contains('hidden') ?
                    `Show ${targetId.replace('List', '').replace('Output', '')}` :
                    `Hide ${targetId.replace('List', '').replace('Output', '')}`;
            } else { console.error(`Target element with ID "${targetId}" not found.`); }
        });
        const targetElement = document.getElementById(button.dataset.target);
        if (targetElement) {
            button.textContent = targetElement.classList.contains('hidden') ?
                `Show ${button.dataset.target.replace('List', '').replace('Output', '')}` :
                `Hide ${button.dataset.target.replace('List', '').replace('Output', '')}`;
        }
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
    listElement.innerHTML = '';
    if (!data || data.length === 0) { listElement.innerHTML = '<li>No data available or error loading.</li>'; return; }
    data.forEach(item => { const li = document.createElement('li'); li.textContent = formatter(item); listElement.appendChild(li); });
}
async function loadAllStops() { 
    const busStops = await fetchData('/api/data/allstops');
    populateList('busStopsList', busStops, stop => `ID: ${stop.id}, Name: ${stop.name}, Type: ${stop.maxCapacity ? 'Depot' : (stop.id.startsWith('T') ? 'Terminal' : 'Stop')}`);
}
async function loadBuses() { 
    const buses = await fetchData('/api/data/buses');
    populateList('busesList', buses, bus => `ID: ${bus.busId}, Plate: ${bus.numberPlate}, Depot: ${bus.depotId}, Range: ${bus.rangeKm}km, Charge: ${bus.currentChargeKm}km, Location: ${bus.currentLocationId}`);
}
async function loadBusRoutes() { 
    const busRoutes = await fetchData('/api/data/busroutes');
    populateList('busRoutesList', busRoutes, route => `ID: ${route.id}, Name: ${route.name}, From: ${route.startTerminalId} To: ${route.endTerminalId}, Stops: [${route.stopIds ? route.stopIds.join(', ') : ''}], Distance: ${route.totalDistanceKm}km, Time: ${route.travelTimeMinutes}min`);
}
async function loadBusDepots() { 
    const busDepots = await fetchData('/api/data/depots');
    populateList('busDepotsList', busDepots, depot => `ID: ${depot.id}, Name: ${depot.name}, Capacity: ${depot.maxCapacity}`);
}
async function loadBusTerminals() { 
    const busTerminals = await fetchData('/api/data/terminals');
    populateList('busTerminalsList', terminal => `ID: ${terminal.id}, Name: ${terminal.name}`);
}
async function loadBusDrivers() { 
    const busDrivers = await fetchData('/api/data/drivers');
    populateList('busDriversList', driver => `ID: ${driver.id}, Name: ${driver.name}`);
}
async function loadRouteRuns() { 
    const routeRuns = await fetchData('/api/data/routeruns');
    populateList('routeRunsList', routeRuns, run => `ID: ${run.id}, Route: ${run.busRouteId}, Departure: ${run.departureTime}, Arrival: ${run.arrivalTime}, Bus: ${run.assignedBusId || 'N/A'}, Driver: ${run.assignedDriverId || 'N/A'}`);
}