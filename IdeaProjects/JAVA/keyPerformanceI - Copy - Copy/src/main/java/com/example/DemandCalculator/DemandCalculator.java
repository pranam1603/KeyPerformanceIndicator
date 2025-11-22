package com.example.DemandCalculator;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.geotools.referencing.CRS;
import org.geotools.geometry.jts.JTS;
import org.locationtech.jts.geom.Point;
import org.geotools.data.FileDataStore;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Coordinate;
import org.geotools.data.FileDataStoreFinder;
import org.opengis.feature.simple.SimpleFeature;
import org.locationtech.jts.geom.GeometryFactory;
import org.geotools.geometry.jts.JTSFactoryFinder;
import org.geotools.data.simple.SimpleFeatureSource;
import org.geotools.data.simple.SimpleFeatureIterator;
import org.locationtech.jts.operation.buffer.BufferOp;
import org.opengis.referencing.operation.MathTransform;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.opengis.referencing.crs.CoordinateReferenceSystem;
import pabeles.concurrency.IntOperatorTask;

import java.io.*;
import java.util.*;
import java.time.LocalTime;

public class DemandCalculator {
    public static List<String> getIntersectGrids(String origin, double lon, double lat, double timeMinutes, double walkingSpeed) throws Exception {

        String gridFilePath = "D:\\Jupyter\\GTFS filter\\grid_filtered.shp"; // or .geojson

        // ----------------------------
        // STEP 1: Calculate buffer distance
        // ----------------------------
        double bufferDistanceMeters = Math.min(600.00, (double) (timeMinutes * walkingSpeed));
        System.out.println("From demand calculator: " + timeMinutes + " minutes");
        System.out.println("Buffer radius: " + bufferDistanceMeters + " meters");

        // ----------------------------
        // STEP 2: Create point geometry (WGS84)
        // ----------------------------
        GeometryFactory geometryFactory = JTSFactoryFinder.getGeometryFactory();
        Point startPoint = geometryFactory.createPoint(new Coordinate(lon, lat));

        // ----------------------------
        // STEP 3: Reproject to metric CRS (Web Mercator)
        // ----------------------------
        CoordinateReferenceSystem crsWGS84 = CRS.decode("EPSG:4326", true);
        CoordinateReferenceSystem crsMeter = CRS.decode("EPSG:3857", true);
        MathTransform transformToMeter = CRS.findMathTransform(crsWGS84, crsMeter, true);

        Geometry startPointMeters = JTS.transform(startPoint, transformToMeter);

        // ----------------------------
        // STEP 4: Create buffer
        // ----------------------------
        Geometry bufferMeters = BufferOp.bufferOp(startPointMeters, bufferDistanceMeters);

        // ----------------------------
        // STEP 5: Load grid data
        // ----------------------------
        File gridFile = new File(gridFilePath);
        FileDataStore store = FileDataStoreFinder.getDataStore(gridFile);
        SimpleFeatureSource featureSource = store.getFeatureSource();
        SimpleFeatureCollection collection = featureSource.getFeatures();

        // ----------------------------
        // STEP 6: Iterate and find intersections
        // ----------------------------
        MathTransform transformGridToMeter = CRS.findMathTransform(
                featureSource.getInfo().getCRS(), crsMeter, true);
        List<String> intersectGrid = new ArrayList<>();
        System.out.println("Intersecting grid IDs:");
        try (SimpleFeatureIterator it = collection.features()) {
            while (it.hasNext()) {
                SimpleFeature feature = it.next();
                Geometry geom = (Geometry) feature.getDefaultGeometry();
                Geometry geomMeters = JTS.transform(geom, transformGridToMeter);

                if (geomMeters.intersects(bufferMeters)) {
                    Object id = feature.getAttribute("id");
                    if(!Objects.equals(id.toString(), origin)){
                        intersectGrid.add(id.toString());
                        System.out.println(" -> " + id);
                    }
                }
            }
        }
        store.dispose();
        return intersectGrid;
    }

    public static double getTotalDemand(List<String> gridList, String time, String destination) throws IOException {

        // FIXED JSON (was invalid earlier)
//        String jsonData = """
//            [
//                {
//                    "origin": "250mN301850E444750",
//                    "destination": "250mN301925E445000",
//                    "timeslots": {
//                        "08-10" : {
//                            "work": 19.20,
//                            "shopping": 2.15
//                        }
//                    }
//                },
//                {
//                    "origin": "250mN302000E444650",
//                    "destination": "250mN301850E444750",
//                    "timeslots": {
//                        "08-10" : {
//                            "work": 19.20,
//                            "shopping": 2.15
//                        }
//                    }
//                },
//                {
//                    "origin": "250mN301825E444750",
//                    "destination": "250mN301925E445000",
//                    "timeslots": {
//                        "08-10" : {
//                            "leisure": 4.15
//                        },
//                        "12-14" : {
//                            "office": 1.15
//                        }
//                    }
//                },
//                {
//                    "origin": "250mN301850E444775",
//                    "destination": "250mN301925E445000",
//                    "timeslots": {
//                        "08-10" : {
//                            "leisure": 9.20,
//                            "school": 0.15
//                        },
//                        "12-14" : {
//                            "work": 9.20,
//                            "office": 0.15
//                        }
//                    }
//                },
//                {
//                    "origin": "250mN301850E444725",
//                    "destination": "250mN301925E445000",
//                    "timeslots": {
//                        "12-14" : {
//                            "leisure": 9.20,
//                            "school": 0.15
//                        }
//                    }
//                }
//            ]
//            """;

        String dataPath = "C:\\Users\\prana\\IdeaProjects\\JAVA\\keyPerformanceI - Copy - Copy\\src\\main\\java\\com\\example\\data.json";
        InputStream inputStream = new FileInputStream(dataPath);
        InputStreamReader jsonData = new InputStreamReader(inputStream);

        Gson gson = new Gson();
        List<Map<String, Object>> tempdata = gson.fromJson(
                jsonData,
                new TypeToken<List<Map<String, Object>>>() {}.getType()
        );

        double totalDemand = 0.0;

        // Loop through each item in tempdata
        for (Map<String, Object> item : tempdata) {

            String origin = item.get("origin").toString();
            String dest = item.get("destination").toString();

            // 1️⃣ Check origin is inside buffer grids
            if (!gridList.contains(origin)) continue;

            // 2️⃣ Check destination matches company destination
            if (!dest.equals(destination)) continue;

            // Extract timeslots
            Map<String, Object> timeslots = (Map<String, Object>) item.get("timeslots");

            if (!timeslots.containsKey(time)) continue;

            // Get the selected time block (e.g., "08-10")
            Map<String, Object> activities = (Map<String, Object>) timeslots.get(time);

            // 5️⃣ Add all values (work, shopping, leisure, office, school)
            for (Object value : activities.values()) {
                if (value instanceof Number) {
                    totalDemand += ((Number) value).doubleValue();
                }
            }
        }
        System.out.println("demand " + totalDemand);
        return totalDemand;
    }

}
