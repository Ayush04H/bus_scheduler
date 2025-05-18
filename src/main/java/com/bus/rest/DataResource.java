package com.bus.rest;

import java.util.List; // Import all

import com.bus.domain.Bus;
import com.bus.domain.BusDepot;
import com.bus.domain.BusDriver;
import com.bus.domain.BusRoute;
import com.bus.domain.BusStop;
import com.bus.domain.BusTerminal;
import com.bus.domain.RouteRun;
import com.bus.service.DataService;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

@Path("/api/data")
public class DataResource {

    @Inject
    DataService dataService;

    @GET
    @Path("/allstops") // Combined list of all stop types
    @Produces(MediaType.APPLICATION_JSON)
    public List<BusStop> getAllBusStops() {
        return dataService.getAllBusStops();
    }

    @GET
    @Path("/depots")
    @Produces(MediaType.APPLICATION_JSON)
    public List<BusDepot> getAllBusDepots() {
        return dataService.getAllBusDepots();
    }

    @GET
    @Path("/terminals")
    @Produces(MediaType.APPLICATION_JSON)
    public List<BusTerminal> getAllBusTerminals() {
        return dataService.getAllBusTerminals();
    }

    @GET
    @Path("/buses")
    @Produces(MediaType.APPLICATION_JSON)
    public List<Bus> getAllBuses() {
        return dataService.getAllBuses();
    }

    @GET
    @Path("/drivers")
    @Produces(MediaType.APPLICATION_JSON)
    public List<BusDriver> getAllBusDrivers() {
        return dataService.getAllBusDrivers();
    }

    @GET
    @Path("/busroutes") // Route templates
    @Produces(MediaType.APPLICATION_JSON)
    public List<BusRoute> getAllBusRoutes() {
        return dataService.getAllBusRoutes();
    }

    @GET
    @Path("/routeruns") // The actual runs to be scheduled
    @Produces(MediaType.APPLICATION_JSON)
    public List<RouteRun> getRouteRunsToSchedule() {
        return dataService.getRouteRunsToSchedule();
    }
}