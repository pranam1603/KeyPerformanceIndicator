package com.example.StopToGridFinder;

import java.util.Map;
import java.util.HashMap;
import java.io.FileReader;
import java.io.IOException;
import java.io.BufferedReader;

public class StopToGridFinder {
    private Map<String, String> stopToGrid = new HashMap<>();

    public StopToGridFinder() throws IOException{
        loadCSV();
    }

    private void loadCSV() throws IOException{
        try(BufferedReader br = new BufferedReader(new FileReader("C:\\Users\\prana\\IdeaProjects\\JAVA\\keyPerformanceI\\src\\main\\java\\com\\example\\grid_stops.csv"))){
            String header = br.readLine();
            String line;

            while ((line = br.readLine()) != null) {
                // Split only at the first comma
                int firstComma = line.indexOf(",");
                if (firstComma == -1) continue;

                String gridId = line.substring(0, firstComma).trim();
                String stopIds = line.substring(firstComma + 1).trim();

                // Remove brackets, quotes, and spaces
                stopIds = stopIds.replaceAll("[\\[\\]'\" ]", "");
                String[] stops = stopIds.split(",");

                for (String stop : stops) {
                    if (!stop.isEmpty()) {
                        stopToGrid.put(stop, gridId);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public String getGridForStop(String stopId){
        return stopToGrid.getOrDefault(stopId, "UNKNOWN");
    }

}
