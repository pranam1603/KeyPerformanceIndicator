package com.example.GridCenterFinder;
import java.io.*;
import java.util.*;

public class GridCenterFinder {

    // Store all grid centers in memory
    private static Map<String, double[]> gridCenterMap = new HashMap<>();

    // Load CSV once
    public static void loadGridCenters(String filePath) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        String line;

        // skip header
        br.readLine();

        while ((line = br.readLine()) != null) {
            String[] parts = line.split(",");
            String id = parts[0];
            double lon = Double.parseDouble(parts[1]);  // x_center
            double lat = Double.parseDouble(parts[2]);  // y_center

            gridCenterMap.put(id, new double[]{lat, lon});
        }

        br.close();
    }

    // 🔥 Function you want: input grid ID → return [lat, lon]
    public static List<Double> getLatLon(String gridId) throws Exception {
        loadGridCenters("C:\\Users\\prana\\IdeaProjects\\JAVA\\keyPerformanceI - Copy - Copy\\src\\main\\java\\com\\example\\grid_centers.csv");
        double[] value = gridCenterMap.get(gridId);

        if (value == null) {
            return null; // or throw new RuntimeException("Grid not found");
        }

        return Arrays.asList(value[0], value[1]); // lat, lon
    }
}
