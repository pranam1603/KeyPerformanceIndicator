package com.example.KpiIndicator;

import com.example.GridCenterFinder.GridCenterFinder;
import org.onebusaway.gtfs.model.Stop;
import com.example.oneBusLoader.GTFSLoader;
import org.jgrapht.graph.DefaultWeightedEdge;
import com.example.DemandCalculator.DemandCalculator;
import com.example.StopToGridFinder.StopToGridFinder;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import com.example.BusScheduleFinder.BusScheduleFinder;
import com.example.GraphhopperRequest.GraphhopperRequest;

import java.util.Map;
import java.util.List;
import java.io.FileWriter;
import java.time.Duration;
import java.io.IOException;
import java.util.ArrayList;
import java.io.BufferedWriter;
import java.time.LocalDateTime;

public class KpiIndicator {
    Stop startStop;
    Stop endStop;
    String origin;
    String destination;
    String time;
    String kpiFilePath;
    Double peopleFromOD;
    GTFSLoader loader;
    LocalDateTime startDateTime;
    DefaultDirectedWeightedGraph<Stop, DefaultWeightedEdge> graph;
    double walkingSpped;

    public KpiIndicator(Stop startStop, Stop endStop, String origin, String destination, String time, String kpiFilePath, Double peopleFromOD, GTFSLoader loader, DefaultDirectedWeightedGraph<Stop,DefaultWeightedEdge> graph, LocalDateTime startDateTime, double walkingSpeed){
        this.startDateTime = startDateTime;
        this.endStop = endStop;
        this.origin = origin;
        this.destination = destination;
        this.time = time;
        this.kpiFilePath = kpiFilePath;
        this.peopleFromOD = peopleFromOD;
        this.startStop = startStop;
        this.graph = graph;
        this.walkingSpped = walkingSpeed;
        this.loader = loader;
    }

    public void calculateKpi() throws Exception {

        GridCenterFinder gridCenterFinder = new GridCenterFinder();
        List<Double> originLatLon = gridCenterFinder.getLatLon(origin);
        List<Double> destinationLatLon = gridCenterFinder.getLatLon(destination);

        System.out.println("Origin: " + originLatLon + " Destination " + destinationLatLon);

        // ------------------- Find the time taken by the Car between the stops -------------------
        GraphhopperRequest graphhopperRequest = new GraphhopperRequest();
        double timeByCar = graphhopperRequest.calculateTimeByCar(originLatLon.get(0), originLatLon.get(1), destinationLatLon.get(0), destinationLatLon.get(1));
        double timeByCarInMin = (double)Math.round((timeByCar/60000) * 100.0) / 100;

        double adjustedTime = timeByCarInMin * 1.25 + 5;

        System.out.println("Time By Car between " + origin + " and " + destination + " is: " + timeByCarInMin + " Adjusted time " + adjustedTime);

        // ------------------- Find the Bus Information(Next bus, Previous Bus and Duration) between the stops -------------------
        BusScheduleFinder.BusTimesResult busInfo = BusScheduleFinder.getPrevNextBusAndDuration(startStop, endStop, startDateTime, new ArrayList<>(loader.getAllStopTimes()), graph);
        if(busInfo == null) {
            System.out.println("No Bus Info!");
            return;
        }
        LocalDateTime previousBus = busInfo.previousBus;
        System.out.println("From KPI INDICATOR: " + previousBus);
        LocalDateTime nextBus =  busInfo.nextBus;
        System.out.println("Previous Bus: " + previousBus + " Next Bus: " + nextBus);

        double busTravelTime = busInfo.totalDuration.toMinutes();
        System.out.println("Time taken by Bus: " + busTravelTime);

        double distanceOriginBus = haversine(originLatLon.get(0), originLatLon.get(1), startStop.getLat(), startStop.getLon());
        double distanceBusDestination = haversine(endStop.getLat(), endStop.getLon(), destinationLatLon.get(0), destinationLatLon.get(1));

        double timeOriginBus = 0.0;
        double timeBusDestination = 0.0;
        if(distanceOriginBus > 800){
            double tempCarTime =  graphhopperRequest.calculateTimeByCar(originLatLon.get(0), originLatLon.get(1), startStop.getLat(), startStop.getLon());
            timeOriginBus = (double)Math.round((tempCarTime/60000) * 100.0) / 100;
        } else{
            timeOriginBus = (distanceOriginBus/walkingSpped);
        }

        if(distanceBusDestination > 800){
            double tempCarTime = graphhopperRequest.calculateTimeByCar(endStop.getLat(), endStop.getLon(), destinationLatLon.get(0), destinationLatLon.get(1));
            timeBusDestination = (double)Math.round((tempCarTime/60000) * 100.0) / 100;
        } else{
            timeBusDestination = (distanceBusDestination/walkingSpped);
        }


        System.out.println("Origin to bus " + timeOriginBus + " Bus to dest " + timeBusDestination);
        double totalTravelTime = (timeOriginBus+timeBusDestination)*1.25 + busTravelTime;
        System.out.println("total travel time" + totalTravelTime );
//         ------------------- Find the OD Matrix Row and Number Of People  -------------------
//        StopToGridFinder stopToGridFinder = new StopToGridFinder();
//        String startStopGrid = stopToGridFinder.getGridForStop(startStop.getId().getId());
//        String endStopGrid = stopToGridFinder.getGridForStop(endStop.getId().getId());
//
//        System.out.println("StartStopGrid: " + startStopGrid + " EndStopGrid: " + endStopGrid);

//        ODCalculator odCalculator = new ODCalculator();
//        Map<Integer, Double> odRow = odCalculator.loadODRow(startStopGrid, endStopGrid);
//        double numberOfPeopleFromOD = 0.0;
//        if (odRow == null){
//            System.out.println("No People btw " + startStopGrid + " and " + endStopGrid);
//        }else {
//            numberOfPeopleFromOD = ODCalculator.peopleBetweenTimes(odRow, previousBus, nextBus);
//            System.out.println("Number of People: " + numberOfPeopleFromOD);
//        }

//        Double numberOfPeopleFromOD = 0.0;



        // ------------------- Calculate KPI -------------------
        double diffMinutes = Duration.between(previousBus, nextBus).toMinutes();
        System.out.println("Time Difference btw Busses: " + diffMinutes);

        double acceptableTime = 2*adjustedTime;
        double margin = 0;
        if(acceptableTime >= totalTravelTime){
            margin = (double)(acceptableTime-totalTravelTime)/diffMinutes;
            System.out.println("Margin: " + margin);
        }
        double numberOfPeopleFromDemand = 0.0;
        double timeInMinutes = (double)(acceptableTime-totalTravelTime);
        System.out.println("Time in Minutes: " + timeInMinutes);
        if(timeInMinutes > 0){
            List<String> intersectGrids = DemandCalculator.getIntersectGrids(origin, originLatLon.get(1), originLatLon.get(0), timeInMinutes, walkingSpped);
            System.out.println("Intersecting Grids: " + intersectGrids);

            if(!intersectGrids.isEmpty()){
                numberOfPeopleFromDemand = DemandCalculator.getTotalDemand(intersectGrids, time, destination);
            }
            System.out.println("Demand from D->S: " + numberOfPeopleFromDemand);
        }
        double numberOfPeople = numberOfPeopleFromDemand + peopleFromOD;
        System.out.println("Total Demand: " + numberOfPeople);
        double kpi = numberOfPeople*margin;
        System.out.println("Kpi Indicator: " + kpi);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(kpiFilePath, true))) {
            writer.write(origin + "," + destination + "," + kpi);
            writer.newLine();
            System.out.println("KPI written for stop " + endStop.getId());
        } catch (IOException e) {
            System.err.println("Error writing KPI to file: " + e.getMessage());
        }

    }

    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        // Convert degrees to radians
        double EARTH_RADIUS = 6371000;
        double latRad1 = Math.toRadians(lat1);
        double latRad2 = Math.toRadians(lat2);
        double deltaLat = Math.toRadians(lat2 - lat1);
        double deltaLon = Math.toRadians(lon2 - lon1);

        // Haversine formula
        double a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(latRad1) * Math.cos(latRad2)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS * c; // distance in meters
    }
}
