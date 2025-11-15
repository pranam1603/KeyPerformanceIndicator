package com.example.ODCalculator;

import java.time.*;
import java.util.Map;
import java.util.HashMap;
import java.io.FileReader;
import java.io.IOException;
import java.io.BufferedReader;
import java.time.temporal.ChronoUnit;

public class ODCalculator {

//    Loads the hourly OD data for a given start and end grid pair.
    public static Map<Integer, Double> loadODRow(String startGrid, String endGrid) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(
                "C:\\Users\\prana\\IdeaProjects\\JAVA\\keyPerformanceI\\src\\main\\java\\com\\example\\hof_OD_hourly_wide.csv"));
        String header = br.readLine(); // skip header
        String line;

        while ((line = br.readLine()) != null) {
            String[] parts = line.split(",");
            if (parts[0].equals(startGrid) && parts[1].equals(endGrid)) {
                Map<Integer, Double> counts = new HashMap<>();
                for (int h = 0; h < 24; h++) {
                    counts.put(h, Double.parseDouble(parts[h + 2])); // offset (id columns at 0,1)
                }
                br.close();
                return counts;
            }
        }
        br.close();
        return null;
    }


//      Calculates number of people between two bus times (can span across midnight)

    public static double peopleBetweenTimes(Map<Integer, Double> odCounts,
                                            LocalDateTime prev,
                                            LocalDateTime next) {
        double total = 0.0;

        // Normalize start to full hour
        System.out.println("FROM OD PREV: " + prev );
        LocalDateTime t = prev.truncatedTo(ChronoUnit.HOURS);
        if (t.isAfter(prev)) t = t.minusHours(1);

        while (t.isBefore(next)) {
            int hour = t.getHour();
            double hourPeople = odCounts.getOrDefault(hour, 0.0);

            LocalDateTime hourStart = t;
            LocalDateTime hourEnd = t.plusHours(1);

            // calculate overlap
            LocalDateTime overlapStart = prev.isAfter(hourStart) ? prev : hourStart;
            LocalDateTime overlapEnd = next.isBefore(hourEnd) ? next : hourEnd;

            if (overlapStart.isBefore(overlapEnd)) {
                long minutes = ChronoUnit.MINUTES.between(overlapStart, overlapEnd);
                total += hourPeople * (minutes / 60.0);
            }

            t = t.plusHours(1);
        }


        // round to 2 decimal places
        total = Math.round(total * 100.0) / 100.0;
        return total;
    }
}
