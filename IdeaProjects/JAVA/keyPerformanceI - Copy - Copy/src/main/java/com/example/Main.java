package com.example;

import com.google.gson.Gson;
import com.example.KpiIndicator.KpiIndicator;
import com.google.gson.reflect.TypeToken;
import com.example.oneBusLoader.GTFSLoader;
import com.example.oneBusLoader.GraphBuilder;
import com.example.DemandCalculator.DemandCalculator;

import org.json.JSONArray;
import org.json.JSONObject;
import org.onebusaway.gtfs.model.Stop;
import org.jgrapht.graph.DefaultWeightedEdge;
import org.jgrapht.graph.DefaultDirectedWeightedGraph;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;

import java.io.*;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalTime;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.stream.Collectors;

public class Main {

    public static void main(String[] args) throws Exception {
        // ------------------- Set up GTFS and start time -------------------

        double walkingSpeed = 80.0; // meters per minute (~5 km/h)
        double maxWalkingDistance = 800.0; // meters

        String jsonData = """
                [
                    {
                        "origin": "250mN301850E444750",
                        "destination": "250mN301925E445000",
                        "timeslots": {
                            "08-10" : {
                                "work": 19.20,
                                "shopping": 2.15
                            }
                        }
                    }
                ]
                """;
        Gson gson = new Gson();
        List<Map<String, Object>> data = gson.fromJson(
                jsonData,
                new TypeToken<List<Map<String, Object>>>(){}.getType()
        );
        String kpiFilePath = "kpiData.txt";
        // Access normally
        for (Map<String, Object> obj : data) {

            Map<String, Object> timeslots = (Map<String, Object>) obj.get("timeslots");
            for(Map.Entry<String, Object> timeslot: timeslots.entrySet()){
                String time = timeslot.getKey();
                Map<String, Object> activities = (Map<String, Object>) timeslot.getValue();
                Double  peopleFromOD = 0.0;

                for(Map.Entry<String, Object> activity: activities.entrySet()){
                    Double people = ((Number) activity.getValue()).doubleValue();
                    peopleFromOD+=people;
                }
                String[] parts = time.split("-");

                int startHour = Integer.parseInt(parts[0]);  // "08" → 8
                LocalDate travelDate = LocalDate.now();
                LocalTime travelTime = LocalTime.of(startHour, 00);

                LocalDateTime startDateTime = LocalDateTime.of(travelDate, travelTime);


                GTFSLoader loader = new GTFSLoader("D:\\Filtered_gtfs_feed_delfi", startDateTime);

                GraphBuilder builder = new GraphBuilder();
                DefaultDirectedWeightedGraph<Stop, DefaultWeightedEdge> graph = builder.buildGraph(loader, walkingSpeed, maxWalkingDistance);

                Map<String, Stop> allStops = new HashMap<>();
                for (Stop stop : loader.getAllStops()) {
                    allStops.put(stop.getId().getId().toString(), stop);
                }

                Map<String, List<Stop>> gridStops = loadGridStops("C:\\Users\\prana\\IdeaProjects\\JAVA\\keyPerformanceI - Copy\\src\\main\\java\\com\\example\\grid_stops.csv", allStops);

                System.out.println("People "+ peopleFromOD);
                List<Stop> bestRoute = findFastestConnection(args, graph, gridStops, obj, startDateTime, loader, walkingSpeed);
                if(bestRoute != null){
                    KpiIndicator kpiIndicator = new KpiIndicator(bestRoute.get(0),bestRoute.get(1), obj.get("origin").toString(), obj.get("destination").toString(), time, kpiFilePath, peopleFromOD, loader, graph, startDateTime, walkingSpeed);
                    kpiIndicator.calculateKpi();
                } else {
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(kpiFilePath, true))) {
                        writer.write(obj.get("origin").toString() + "," + obj.get("destination").toString() + "," + 0.0);
                        writer.newLine();
                        System.out.println("KPI for stop " + obj.get("origin").toString() + " to " + obj.get("destination").toString() + " is " + 0);
                        System.out.println("KPI written for stop " + obj.get("origin").toString());
                    } catch (IOException e) {
                        System.err.println("Error writing KPI to file: " + e.getMessage());
                    }
                }
            }
            System.out.println("------------------------");
        }
    }

    private static Map<String, List<Stop>> loadGridStops(String path, Map<String, Stop> allStops) throws IOException {
        Map<String, List<Stop>> map = new HashMap<>();
        List<String> lines = Files.readAllLines(Paths.get(path));
        for (String line : lines.subList(1, lines.size())) { // skip header
            int firstComma = line.indexOf(",");
            if (firstComma == -1) continue;

            String gridId = line.substring(0, firstComma).trim();
            String stopsPart = line.substring(firstComma + 1).trim();

            // Clean the stop list: remove brackets, quotes, and spaces
            stopsPart = stopsPart.replace("[", "")
                    .replace("]", "")
                    .replace("\"", "")
                    .replace("'", "")
                    .trim();

            // Split by comma
            List<String> stopIds = Arrays.stream(stopsPart.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
            // Map stop IDs to Stop objects
            List<Stop> stops = stopIds.stream()
                    .map(allStops::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            map.put(gridId, stops);
        }
        System.out.println("Loaded " + map.size() + " grids from file.");
        System.out.println("Map is: " + map);
        return map;
    }

    public static double haversine(double lat1, double lon1, double lat2, double lon2) {
        double R = 6371000; // Earth radius in meters
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat/2)*Math.sin(dLat/2) +
                Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2)) *
                        Math.sin(dLon/2)*Math.sin(dLon/2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        return R * c;
    }

    public static Stop findNearestBusStop(GTFSLoader loader, double lat, double lon) {
        Stop nearest = null;
        Collection<Stop> allBusStopsCollection = loader.getAllStops();
        List<Stop> allBusStops = new ArrayList<>(allBusStopsCollection);
        double minDist = Double.MAX_VALUE;

        for (Stop stop : allBusStops) {
            double dist = haversine(lat, lon, stop.getLat(), stop.getLon());
            if (dist < minDist) {
                minDist = dist;
                nearest = stop;
            }
        }
        return nearest;
    }

    private static List<Stop> findFastestConnection(String[] args, DefaultDirectedWeightedGraph<Stop, DefaultWeightedEdge> graph,
                                                    Map<String, List<Stop>> gridStops,
                                                    Map<String, Object> obj, LocalDateTime startDateTime, GTFSLoader loader, double walkingSpeed) throws Exception {

        String startGrid = obj.get("origin").toString();
        String endGrid = obj.get("destination").toString();
        List<Stop> startStops = gridStops.getOrDefault(startGrid, Collections.emptyList());
        List<Stop> endStops = gridStops.getOrDefault(endGrid, Collections.emptyList());
        double startCenterLon = 0;
        double startCenterLat = 0;
        double endCenterLon = 0;
        double endCenterLat = 0;

        if (startStops.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader("C:\\Users\\prana\\IdeaProjects\\JAVA\\keyPerformanceI - Copy\\src\\main\\java\\com\\example\\grid_filtered.geojson"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }

            JSONObject root = new JSONObject(sb.toString());
            JSONArray features = root.getJSONArray("features");

            for (int i = 0; i < features.length(); i++) {
                JSONObject feature = features.getJSONObject(i);
                JSONObject properties = feature.getJSONObject("properties");
                String id = properties.getString("id");
                if (startGrid.equals(id)) {
                    // Get polygon coordinates
                    JSONArray coordinates = feature.getJSONObject("geometry")
                            .getJSONArray("coordinates")
                            .getJSONArray(0);

                    double sumLon = 0;
                    double sumLat = 0;
                    int n = coordinates.length()-1;

                    for (int j = 0; j < n; j++) {
                        JSONArray coord = coordinates.getJSONArray(j);
                        double lon = coord.getDouble(0);
                        double lat = coord.getDouble(1);
                        sumLon += lon;
                        sumLat += lat;
                    }

                    startCenterLon = sumLon / n;
                    startCenterLat = sumLat / n;

                    System.out.println("Center Lat: " + startCenterLat + ", Lon: " + startCenterLon);
                }
            }
            System.out.println("No stops found for starting grids.");
            DemandCalculator demandCalculator = new DemandCalculator();
            List<String> nearbyGrids = demandCalculator.getIntersectGrids(startGrid, startCenterLon, startCenterLat, 7.5, walkingSpeed);


            System.out.println("Intersecting start grids: " + nearbyGrids.size());
            startStops = new ArrayList<>();
            for (String g : nearbyGrids) {
                startStops.addAll(gridStops.getOrDefault(g, Collections.emptyList()));
            }

            if(startStops.isEmpty()){
                System.out.println("Still no start stops after buffer!");
                Stop nearestStartStop = findNearestBusStop(loader, startCenterLat, startCenterLon);
                startStops.add(nearestStartStop);
                System.out.println("Nearest Start Stop: " + nearestStartStop.getName());
            }
        }

        if (endStops.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            try (BufferedReader br = new BufferedReader(new FileReader("C:\\Users\\prana\\IdeaProjects\\JAVA\\keyPerformanceI - Copy\\src\\main\\java\\com\\example\\grid_filtered.geojson"))) {
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
            }

            JSONObject root = new JSONObject(sb.toString());
            JSONArray features = root.getJSONArray("features");

            for (int i = 0; i < features.length(); i++) {
                JSONObject feature = features.getJSONObject(i);
                JSONObject properties = feature.getJSONObject("properties");
                String id = properties.getString("id");

                if (endGrid.equals(id)) {
                    // Get polygon coordinates
                    JSONArray coordinates = feature.getJSONObject("geometry")
                            .getJSONArray("coordinates")
                            .getJSONArray(0);

                    double sumLon = 0;
                    double sumLat = 0;
                    int n = coordinates.length()-1;
                    System.out.println(n + " no od coords");
                    for (int j = 0; j < n; j++) {
                        JSONArray coord = coordinates.getJSONArray(j);
                        double lon = coord.getDouble(0);
                        double lat = coord.getDouble(1);
                        sumLon += lon;
                        sumLat += lat;
                    }
                    endCenterLon = sumLon / n;
                    endCenterLat = sumLat / n;

                    System.out.println("Center Lat: " + endCenterLat + ", Lon: " + endCenterLon);
                }
            }
            System.out.println("No stops found for end grids.");
            DemandCalculator demandCalculator = new DemandCalculator();
            List<String> nearbyGrids = demandCalculator.getIntersectGrids(startGrid, endCenterLon, endCenterLat, 7.5, walkingSpeed);


            System.out.println("Intersecting end grids: " + nearbyGrids.size());
            endStops = new ArrayList<>();
            for (String g : nearbyGrids) {
                endStops.addAll(gridStops.getOrDefault(g, Collections.emptyList()));
            }
            if(endStops.isEmpty()){
                System.out.println("Still no start stops after buffer!");
                Stop nearestStartStop = findNearestBusStop(loader, endCenterLat, endCenterLon);
                endStops.add(nearestStartStop);
                System.out.println("Nearest End Stop: " + nearestStartStop.getName());
            }
        }

        DijkstraShortestPath<Stop, DefaultWeightedEdge> dijkstra = new DijkstraShortestPath<>(graph);
        double bestTime = Double.MAX_VALUE;
        Stop bestStart = null, bestEnd = null;
        System.out.println("StartStopss: " + startStops);
        System.out.println("EndStopss: " + endStops);

        for (Stop s : startStops) {
            for (Stop e : endStops) {
                try {
                    double time = dijkstra.getPathWeight(s, e);
                    if (time < bestTime) {
                        bestTime = time;
                        bestStart = s;
                        bestEnd = e;
                    }
                } catch (Exception ignored) {
                    // BAAAAAAAD
                }
            }
        }
        System.out.println("Best Start: " + bestStart + " " + bestEnd);

        System.out.println("Start in graph: " + graph.containsVertex(startStops.get(0)));
        System.out.println("End in graph:   " + graph.containsVertex(endStops.get(0)));

        System.out.println("Neighbors of start: " + graph.edgesOf(startStops.get(0)));
        System.out.println("Neighbors of end:   " + graph.edgesOf(endStops.get(0)));

        System.out.println("Has direct edge: " + graph.containsEdge(startStops.get(0), endStops.get(0)));

        if (bestStart != null) {
            System.out.printf("Fastest route: %s → %s (%f minutes)%n",
                    bestStart.getName(), bestEnd.getName(), bestTime);
            System.out.printf("Fastest route: %s → %s (%.2f minutes)%n",
                    bestStart.getId().getId(), bestEnd.getId().getId(), bestTime);
            List<Stop> bestRoute = new ArrayList<>(Arrays.asList(bestStart, bestEnd));

            return bestRoute;
//
        } else {
            System.out.println("No valid connection found between grids.");
            return null;
        }
    }

// run and check for buffer stops and nearest stop

//        Stop startStop = graph.vertexSet().stream()
//                .filter(s -> s.getId().getId().equals("de:09464:60:0:2"))
//                .findFirst()
//                .orElseThrow(() -> new RuntimeException("Start stop not found!"));
//
//        Stop endStop = graph.vertexSet().stream()
//                .filter(s -> s.getId().getId().equals("de:09464:61:0:2_G_G"))
//                .findFirst()
//                .orElseThrow(() -> new RuntimeException("End stop not found!"));
//
//        DijkstraShortestPath<Stop, DefaultWeightedEdge> dijkstra = new DijkstraShortestPath<>(graph);
//        var pathw = dijkstra.getPath(startStop, endStop);
//        if(pathw != null) System.out.println(pathw);
//        else System.out.println("No Path btw start and end stop");
//
//        // ------------------- Run Dijkstra to find all reachable stops -------------------
//        double timeBudgetMinutes = 2; // 24 hours
//
//        Map<Stop, Double> reachableStopsMap = new HashMap<>();
//        for (Stop stop : graph.vertexSet()) {
//            GraphPath<Stop, DefaultWeightedEdge> path = dijkstra.getPath(startStop, stop);
//            if (path != null && path.getWeight() <= timeBudgetMinutes) {
//                reachableStopsMap.put(stop, path.getWeight());
//            }
//        }

//        String kpiFilePath = "kpiData.txt";
//
//        try(BufferedWriter writer = new BufferedWriter(new FileWriter(kpiFilePath))){
//            writer.write("start_stop, end_stop, kpi");
//            writer.newLine();
//        } catch (IOException e){
//            System.err.println("Error creating file: " + e.getMessage());
//        }
//
//        System.out.println("Total reachable stops within " + timeBudgetMinutes + " min: " + reachableStopsMap.size());
//        KpiIndicator kpiIndicator = new KpiIndicator(startStop, loader, graph, startDateTime, walkingSpeed);
//        int i = 1;
//        for (Map.Entry<Stop, Double> entry : reachableStopsMap.entrySet()) {
//            Stop end = entry.getKey();
//            if(!end.equals(startStop)){
//                System.out.println("About Stop "+ i);
//                System.out.println("End Stop: " + end);
//                kpiIndicator.calculateKpi(end, kpiFilePath);
//                i++;
//                System.out.println("-----------------------------------------------------------");
//            }
//        }

        // ------------------- Create walking buffers around reachable stops -------------------
//        GeometryFactory geometryFactory = new GeometryFactory();
//        List<Geometry> buffers = new ArrayList<>();
//        for (Map.Entry<Stop, Double> entry : reachableStopsMap.entrySet()) {
//            Stop stop = entry.getKey();
//            double travelTime = entry.getValue();
//            double remainingTime = Math.max(timeBudgetMinutes - travelTime, 0);
//            double walkingDistance = Math.min(remainingTime * walkingSpeed, maxWalkingDistance);
//
//            if (walkingDistance > 0) {
//                Point pt = geometryFactory.createPoint(new Coordinate(stop.getLon(), stop.getLat()));
//                Geometry buffer = pt.buffer(walkingDistance / 111320.0); // approximate meters to degrees
//                buffers.add(buffer);
//            }
//        }

        // Merge buffers to create isochrone
//        Geometry isochrone = null;
//        for (Geometry buffer :                                                                                                                                                                                                                                         buffers) {
//            if (isochrone == null) {
//                isochrone = buffer;
//            } else {
//                isochrone = isochrone.union(buffer);
//            }
//        }

//         ------------------- 5. Export as GeoJSON -------------------
//        GeoJSONWriter writer = new GeoJSONWriter();
//        String geojson = writer.write(isochrone).toString();
//
//        Files.writeString(Paths.get("newgraph10.geojson"), geojson);
//        System.out.println("Isochrone GeoJSON saved!");
}
