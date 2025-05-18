package com.bus.rest;

import com.bus.domain.ScheduleSolution;
import com.bus.service.SchedulingService;

import jakarta.inject.Inject;
import jakarta.ws.rs.POST; // Using POST as it might modify server state or is a complex query
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/schedule")
public class SchedulingResource {

    @Inject
    SchedulingService schedulingService;

    @POST // Or GET if you prefer and it's idempotent, but POST is safer for "actions"
    @Path("/solve")
    @Produces(MediaType.APPLICATION_JSON)
    public ScheduleSolution solve() {
        return schedulingService.solveSchedule();
    }
}