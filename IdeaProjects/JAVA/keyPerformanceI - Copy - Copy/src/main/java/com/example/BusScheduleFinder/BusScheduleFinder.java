package com.example.BusScheduleFinder;

import org.onebusaway.gtfs.model.Stop;
import org.onebusaway.gtfs.model.StopTime;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

public class BusScheduleFinder {

    public static class BusTimesResult {
        public LocalDateTime previousBus;
        public LocalDateTime nextBus;
        public Duration totalDuration;

        public BusTimesResult(LocalDateTime prev, LocalDateTime next, Duration duration) {
            this.previousBus = prev;
            this.nextBus = next;
            this.totalDuration = duration;
        }

        @Override
        public String toString() {
            return "Previous Bus: " + previousBus +
                    ", Next Bus: " + nextBus +
                    ", Total Journey Duration: " + totalDuration.toMinutes() + " min";
        }
    }

    // Finds previous and next bus from startStop to endStop at currentDateTime and total duration of journey using Dijkstra.
    public static BusTimesResult getPrevNextBusAndDuration(
            Stop startStop,
            Stop endStop,
            LocalDateTime currentDateTime,
            List<StopTime> allStopTimes,
            DefaultDirectedWeightedGraph<Stop, DefaultWeightedEdge> graph
    ) {
        // Dijkstra shortest path for total duration
        DijkstraShortestPath<Stop, DefaultWeightedEdge> dijkstra = new DijkstraShortestPath<>(graph);
        var path = dijkstra.getPath(startStop, endStop);
        System.out.println("Path from scheduler: " + path);
        if (path == null) return null;

        Duration totalDuration = Duration.ofMinutes((long) dijkstra.getPathWeight(startStop, endStop));

        // Find first bus stop in the path that has any bus
        Stop firstBusStop = null;
        for (DefaultWeightedEdge edge : path.getEdgeList()) {
            Stop from = graph.getEdgeSource(edge);
            boolean hasBus = allStopTimes.stream()
                    .anyMatch(st -> st.getStop().equals(from));
            if (hasBus) {
                firstBusStop = from;
                break;
            }
        }

        if (firstBusStop == null) {
            System.out.println("No bus found in path (possibly all walking or synthetic connection). Using default 6AM–10PM window.");
            LocalDate currentDate = currentDateTime.toLocalDate();

            LocalDateTime prevBus = LocalDateTime.of(currentDate, LocalTime.of(6, 0));
            LocalDateTime nextBus = LocalDateTime.of(currentDate, LocalTime.of(22, 0));

            return new BusTimesResult(prevBus, nextBus, totalDuration);
        }


        // Collect all StopTimes for that first bus stop
        Stop finalFirstBusStop = firstBusStop;
        List<StopTime> stopTimes = allStopTimes.stream()
                .filter(st -> st.getStop().equals(finalFirstBusStop))
                .collect(Collectors.toList());

        LocalDate currentDate = currentDateTime.toLocalDate();
        LocalDateTime cursorDateTime = currentDateTime;

        // Convert GTFS StopTimes to LocalDateTime (handling 24+ hour rollovers)
        List<LocalDateTime> departureDateTimes = stopTimes.stream()
                .map(st -> {
                    int seconds = st.getDepartureTime();
                    LocalDate date = currentDate;
                    if (seconds >= 86400) { // beyond midnight (24:00:00)
                        seconds -= 86400;
                        date = date.plusDays(1);
                    }
                    LocalTime time = LocalTime.ofSecondOfDay(seconds);
                    return LocalDateTime.of(date, time);
                })
                .sorted()
                .collect(Collectors.toList());

        if (departureDateTimes.isEmpty()) {
            return new BusTimesResult(null, null, totalDuration);
        }

        // Find previous and next bus times (handle rollover properly)
        LocalDateTime prevBus = null;
        LocalDateTime nextBus = null;

        for (LocalDateTime dt : departureDateTimes) {
            if (dt.isBefore(cursorDateTime)) {
                prevBus = dt;
            } else if (dt.isAfter(cursorDateTime)) {
                nextBus = dt;
                break;
            }
        }

        // If no previous bus found, assume last bus of previous service day
        if (prevBus == null) {
            prevBus = LocalDateTime.of(currentDate, LocalTime.of(6, 0)); // 6 AM fallback
            System.out.println("Previous bus from previous day assumed. Set to 6:00 AM.");
        }

        // If no next bus found, assume first bus of next service day
        if (nextBus == null) {
            nextBus = LocalDateTime.of(currentDate, LocalTime.of(22, 0)); // 10 PM fallback
            System.out.println("Next bus from next day assumed. Set to 10:00 PM.");
        }

        // Check if prevBus is before currentDate (previous day)
        if (prevBus.toLocalDate().isBefore(currentDate)) {
            prevBus = LocalDateTime.of(currentDate, LocalTime.of(6, 0));
            System.out.println("Prev bus adjusted to current day 6:00 AM.");
        }

        // Check if nextBus is after currentDate (next day)
        if (nextBus.toLocalDate().isAfter(currentDate)) {
            nextBus = LocalDateTime.of(currentDate, LocalTime.of(22, 0));
            System.out.println("Next bus adjusted to current day 10:00 PM.");
        }

        return new BusTimesResult(prevBus, nextBus, totalDuration);
    }
}
