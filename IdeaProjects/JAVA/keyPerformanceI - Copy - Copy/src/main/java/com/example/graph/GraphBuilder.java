package com.example.OneBusLoader;

import io.github.cdimascio.dotenv.Dotenv;
import org.onebusaway.gtfs.model.Trip;
import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.StopTime;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;

import java.time.LocalTime;
import java.util.List;

public class GraphBuilder {
    private DefaultDirectedWeightedGraph<Stop, DefaultWeightedEdge> graph;
    private static final Dotenv dotenv = Dotenv.load();

    public GraphBuilder() {
        graph = new DefaultDirectedWeightedGraph<>(DefaultWeightedEdge.class);
    }

    public DefaultDirectedWeightedGraph<Stop, DefaultWeightedEdge> buildGraph(GTFSLoader loader, LocalTime windowStart, LocalTime windowEnd, double walkingSpeed , double maxWalkingDistance) {
        // Add all stops as vertices
        for (Stop stop : loader.getAllStops()) {
            graph.addVertex(stop);
        }

        // Add all stop edges
        List<Trip> activeTrips = loader.getActiveTrips();
        for (Trip trip : activeTrips) {
            List<StopTime> stopTimes = loader.getStopTimesForTrip(trip);
            for (int i = 0; i < stopTimes.size() - 1; i++) {
                StopTime st1 = stopTimes.get(i);
                StopTime st2 = stopTimes.get(i + 1);

                Stop from = (Stop) st1.getStop();
                Stop to = (Stop) st2.getStop();

                // Calculate travel time in second
                int departureSec = st1.getDepartureTime();
                int arrivalSec = st2.getArrivalTime();

                if (departureSec < windowStart.toSecondOfDay() || departureSec > windowEnd.toSecondOfDay()) {
                    continue;  // skip the rest of this loop iteration
                }

                double travelMinutes = (arrivalSec - departureSec) / 60.0;

                // Skip negative or zero travel time (some GTFS may have errors)
                if (travelMinutes <= 0) travelMinutes = 1; // minimal weight

                DefaultWeightedEdge edge = graph.addEdge(from, to);
                if (edge != null) {
                    // here add the time window
                    graph.setEdgeWeight(edge, travelMinutes);
                }
            }
        }

        // Add walking edges between nearby stops

        for (Stop s1 : graph.vertexSet()) {
            for (Stop s2 : graph.vertexSet()) {
                if (!s1.equals(s2)) {
                    double distanceMeters = haversineDistance(
                            s1.getLat(), s1.getLon(),
                            s2.getLat(), s2.getLon()
                    );

                    if (distanceMeters <= maxWalkingDistance) {
                        double walkTimeMinutes = distanceMeters / walkingSpeed;
                        DefaultWeightedEdge walkEdge = graph.addEdge(s1, s2);
                        if (walkEdge != null) {
                            graph.setEdgeWeight(walkEdge, walkTimeMinutes);
                        }
                    }
                }
            }
        }
        System.out.println("Graph built with " + graph.vertexSet().size() + " stops and " +
                graph.edgeSet().size() + " edges.");
        return graph;
    }
    // Haversine distance in meters
    private static double haversineDistance(double lat1, double lon1, double lat2, double lon2) {
        final int R = 6371000; // Earth radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }
}
