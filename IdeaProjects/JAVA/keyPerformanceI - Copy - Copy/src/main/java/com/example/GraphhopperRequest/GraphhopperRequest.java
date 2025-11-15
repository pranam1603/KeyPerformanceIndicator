package com.example.GraphhopperRequest;

import java.net.URL;
import java.net.URLEncoder;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

import org.json.JSONArray;
import org.json.JSONObject;

public class GraphhopperRequest {

    private static String API_URL = "https://graphhopper.mobidig.cloud/route";

    // Build query parameters
    private static String createQuery(double startLat, double startLon, double destLat, double destLon) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("point=").append(URLEncoder.encode(startLat + "," + startLon, "UTF-8"));
        sb.append("&point=").append(URLEncoder.encode(destLat + "," + destLon, "UTF-8"));
        sb.append("&profile=car");
        sb.append("&calc_points=true");
        sb.append("&points_encoded=false");
        sb.append("&details=road_class");
        sb.append("&details=road_environment");
        sb.append("&details=max_speed");
        sb.append("&details=average_speed");
        sb.append("&snap_preventions=ferry");
        return sb.toString();
    }

    private static JSONObject sendRequest(double startLat, double startLon, double destLat, double destLon) {
        try {
            String query = createQuery(startLat, startLon, destLat, destLon);
            URL url = new URL(API_URL + "?" + query);

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "java-http/1.0");

            BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String inputLine;
            StringBuilder responseContent = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                responseContent.append(inputLine);
            }
            in.close();

            conn.disconnect();

            return new JSONObject(responseContent.toString());

        } catch (Exception e) {
            e.printStackTrace();
            return new JSONObject(); // return empty JSON if error
        }
    }

    public double calculateTimeByCar(double startLat, double startLon, double destLat, double destLon) {

//        double startLat = 50.325;   // dep lat
//        double startLon = 11.942;   // dep lon
//        double destLat  = 50.322;   // des lat
//        double destLon  = 11.913;   // des lon

        JSONObject result = sendRequest(startLat, startLon, destLat, destLon);
        JSONArray pathArr = result.getJSONArray("paths");
        JSONObject firstPath = pathArr.getJSONObject(0);

        double time = firstPath.getDouble("time");
        return time;

        // Parse JSON
//        JSONObject json = new JSONObject(result.toString(2));
//        JSONObject path = json.getJSONArray("paths").getJSONObject(0);
//
//        JSONObject points = path.getJSONObject("points");
//
//
//        JSONObject feature = new JSONObject();
//        feature.put("type", "Feature");
//        feature.put("geometry", points);
//        feature.put("properties", path.getJSONObject("details"));
//
//        JSONArray features = new JSONArray();
//        features.put(feature);
//
//        JSONObject featureCollection = new JSONObject();
//        featureCollection.put("type", "FeatureCollection");
//        featureCollection.put("features", features);
//
//        // Save to file
//        try (FileWriter file = new FileWriter("route.geojson")) {
//            file.write(featureCollection.toString(2));
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
//
//        System.out.println("GeoJSON saved as route.geojson");
    }
}
