package com.mogolinc.hudtutorial;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.PermissionChecker;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

public class HudActivity extends AppCompatActivity implements LocationListener {
    protected final int PermissionRequestAccessFineLocation = 1;
    protected final int LocationUpdateMinTime = 1000; // seconds
    protected final int LocationUpdateMinDistance = 10; // meters
    protected final String MogolApiKey = "YOUR-KEY";
    protected final int LookaheadDistance = 2000; // meters

    protected JSONObject route = null;
    protected JSONArray messages = null;
    private boolean requestInProgress = false;
    private RequestRoadApiTask requestTask = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hud);

        setUpLocationUpdates();
    }

    protected void setUpLocationUpdates() {
        // Check permissions
        int permissionCheck = ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION);
        if(permissionCheck != PackageManager.PERMISSION_GRANTED) {
            requestLocationPermission();
        } else {
            LocationManager lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, LocationUpdateMinTime, LocationUpdateMinDistance, this);
        }
    }

    protected void requestLocationPermission() {

        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION},
                PermissionRequestAccessFineLocation);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[], int[] grantResults) {
        switch (requestCode) {
            case PermissionRequestAccessFineLocation: {
                // Request location updates again. The tutorial app requires location to
                // function.
                setUpLocationUpdates();
                return;
            }
        }
    }

    Location prevLocation = null;

    @Override
    public void onLocationChanged(Location location) {
        // Compute bearing if not provided based on last location
        if(!location.hasBearing() && prevLocation != null)
            location.setBearing(prevLocation.bearingTo(location));

        if(isOnRoute(location) && distanceAlongRoute(location) < LookaheadDistance / 2) {
            try {
                updateDisplay(location);
            } catch (JSONException e) {
                Log.d("com.mogolinc", String.format("JSON parse error updating display: %s", e.getMessage()));
            }
        } else if(!requestInProgress) {
            Log.d("com.mogolinc", "Request not in progress, making new request");
            requestInProgress = true;
            requestTask = new RequestRoadApiTask();
            requestTask.execute(location);
        }
    }

    protected boolean isOnRoute(Location location) {
        if(route == null)
            return false;

        // Check we are within 30m of the route.
        Location l0 = new Location("app");
        Location l1 = new Location("app");

        try {
            JSONArray coords = route.getJSONObject("geometry").getJSONArray("coordinates");

            if(coords.length() > 0) {
                for(int i = 1; i < coords.length(); i++) {
                    JSONArray p = coords.getJSONArray(i-1);
                    JSONArray c = coords.getJSONArray(i);

                    l0.setLatitude(p.getDouble(1));
                    l0.setLongitude(p.getDouble(0));

                    l1.setLatitude(c.getDouble(1));
                    l1.setLongitude(c.getDouble(0));


                    // Create a geofence that is the l0-l1 line extended in all directions by 30 meters.
                    // if location is within geofence, then return true.
                    GeoFence fence = GeoFence.CreateFenceAroundEdge(l0, l1, 30);

                    if(fence.Contains(location))
                        return true;
                }
            }
        } catch (JSONException e) {
            Log.d("com.mogoinc", String.format("Failed to parse route json: %s", e.getMessage()));
        }

        return false;
    }

    protected void updateDisplay(Location l) throws JSONException {
        // Select closest message along the route and display.
        int closestIdx = -1;
        double closestDistance = Double.MAX_VALUE;
        for(int i = 0; i < messages.length(); i++) {
            JSONObject msg = (JSONObject) messages.get(i);

            // Get first polyline or point
            JSONObject geometry = msg.getJSONObject("geometry");
            String type = geometry.getString("type").toLowerCase();
            JSONArray coordinates = geometry.getJSONArray("coordinates");
            Location start = null;

            switch(type) {
                case "point": {
                    start = new Location("app");
                    start.setLatitude(coordinates.getDouble(1));
                    start.setLongitude(coordinates.getDouble(0));
                    break;
                }
                case "linestring": {
                    start = new Location("app");
                    JSONArray firstCoordinate = coordinates.getJSONArray(0);
                    start.setLatitude(firstCoordinate.getDouble(1));
                    start.setLongitude(firstCoordinate.getDouble(0));
                    break;
                }

                        /* ...
                         * Additional geojson geometry types
                         * ...
                         */

                default:
                    Log.d("com.mogolinc", String.format("Unhandled geometry type: %s", type));
                    break;
            }

            if(start != null) {
                double distance = distanceAlongRoute(l, start);
                if(distance > 0 && distance < closestDistance) {
                    closestDistance = distance;
                    closestIdx = i;
                }
            }
        }

        // Display selected
        if(messages.length() > 0 && closestIdx >= 0) {
            JSONObject toDisplay = messages.getJSONObject(closestIdx);
            TextView tv =(TextView) findViewById(R.id.tvMessage);
            JSONObject properties = toDisplay.getJSONObject("properties");

            JSONObject geometry = toDisplay.getJSONObject("geometry");
            JSONArray coordinates = geometry.getJSONArray("coordinates");
            String geometryType = geometry.getString("type");

            // geometryType is any valid geojson type.
            Location startLocation = new Location("app");
            tv.setText(createMessage(properties.getString("condition"), properties.getString("subcondition"), closestDistance));
        } else {
            // Clear
            TextView tv =(TextView) findViewById(R.id.tvMessage);
            tv.setText("");
        }
    }

    protected double distanceAlongRoute(Location end) {
        // Distance from start of route to end
        try {
            JSONArray coords = route.getJSONObject("geometry").getJSONArray("coordinates");

            Location start = new Location("app");

            start.setLatitude(coords.getJSONArray(0).getDouble(1));
            start.setLongitude(coords.getJSONArray(0).getDouble(0));

            return distanceAlongRoute(start, end);
        } catch (JSONException e) {
            Log.d("com.mogolinc", String.format("Failed to parse JSON to get coordinates: %s", e.getMessage()));
        }

        return 0;
    }

    protected double distanceAlongRoute(Location start, Location end) {
        // Find the leg start and end are in, and compute the distance between
        double distance = 0;

        Location l0 = new Location("app");
        Location l1 = new Location("app");

        try {
            JSONArray coords = route.getJSONObject("geometry").getJSONArray("coordinates");

            if(coords.length() > 0) {
                for(int i = 1; i < coords.length(); i++) {
                    JSONArray p = coords.getJSONArray(i-1);
                    JSONArray c = coords.getJSONArray(i);

                    l0.setLatitude(p.getDouble(1));
                    l0.setLongitude(p.getDouble(0));

                    l1.setLatitude(c.getDouble(1));
                    l1.setLongitude(c.getDouble(0));

                    // Create a geofence that is the l0-l1 line extended in all directions by 30 meters.
                    // if location is within geofence, then return true.
                    GeoFence fence = GeoFence.CreateFenceAroundEdge(l0, l1, 30);

                    if(fence.Contains(start)) {
                        // This may trigger twice due to geofence overlap. The latter
                        // trigger should take priority, so this isn't an issue.
                        distance = start.distanceTo(l1);
                    } else if(fence.Contains(end)) {
                        distance += l0.distanceTo(end);
                        break;
                    } else if(i == coords.length() - 1) {
                        // Reached the end of the geometry but have not located 'end'
                        // The current position is past the end. Return -1.
                        distance = -1;
                    } else if(distance > 0) {
                        distance += l0.distanceTo(l1);
                    }
                }
            }
        } catch (JSONException e) {
            Log.d("com.mogoinc", String.format("Failed to parse route json: %s", e.getMessage()));
        }

        return distance;
    }

    protected String createMessage(String condition, String subcondition, double distance) {
        String distanceString = distance < 1000 ? String.format("%.0f m", Math.floor(distance / 100) * 100) : String.format("%.0f km", Math.floor(distance / 1000));

        String message = null;

        switch(condition) {
            case "incident": message = String.format("Accident in %s", distanceString); break;
            case "pavement": message = String.format("Slippery road in %s", distanceString); break;
            case "closed": message = String.format("Road closed in %s", distanceString); break;
            case "construction": message = String.format("Construction in %s", distanceString); break;
            case "information": message = subcondition; break;
            default: message = condition; break;

        }

        return message;
    }


    /**
     * Request for Road API
     */
    private class RequestRoadApiTask extends AsyncTask<Location, Void, JSONObject> {
        protected Location queryLocation;

        protected JSONObject doInBackground(Location... location) {
            try {
                Log.d("com.mogolinc", String.format("Fetching new route for %.6f,%.6f, %.1f", location[0].getLatitude(), location[0].getLongitude(), location[0].getBearing()));

                queryLocation = location[0];
                URL url = new URL(String.format("https://api.mogolinc.com/conditions/route?location=%f,%f&bearing=%f&distance=%d&f=coordinates", location[0].getLatitude(), location[0].getLongitude(), location[0].getBearing(), LookaheadDistance));

                HttpsURLConnection urlConnection = (HttpsURLConnection) url.openConnection();
                urlConnection.setRequestProperty("x-api-key", MogolApiKey);
                urlConnection.setRequestProperty("Content-Type", "application/json");

                int status = urlConnection.getResponseCode();
                InputStream in = null;
                if(status == 200) {
                    in = urlConnection.getInputStream();
                } else {
                    in = urlConnection.getErrorStream();
                }
                BufferedReader br = new BufferedReader(new InputStreamReader(in, "UTF-8"));
                StringBuilder sb = new StringBuilder();

                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }

                if(status == 200) {
                    return new JSONObject(sb.toString());
                } else {
                    Log.d("com.mogolinc", String.format("API request failed (%d):\n %s", status, sb.toString()));
                }
            } catch(MalformedURLException ex) {
                /* TODO: Handle */
                Log.d("com.mogolinc", String.format("Malformed URL: %s", ex.getMessage()));
            } catch (IOException e) {
                /* TODO: Handle */
                Log.d("com.mogolinc", String.format("IO exception: %s", e.getMessage()));
            } catch (JSONException e) {
                /* TODO: Handle */
                Log.d("com.mogolinc", String.format("JSON Parsing error: %s", e.getMessage()));
            }
            return null;
        }

        protected void onPostExecute(JSONObject result) {
            if(result == null) {
                Log.d("com.mogolinc", "Failed to retrieve condition data for current position");
                // Runs on same thread as onLocationChanged, no need for synchronized
                requestInProgress = false;
                return;
            }
            // Update messages if needed
            messages = new JSONArray();
            JSONArray features = null;
            try {
                features = result.getJSONArray("features");
                for(int i = 0; i < features.length(); i++) {
                    JSONObject feature = features.getJSONObject(i);
                    if(feature.getString("type").toLowerCase().compareTo("route") == 0) {
                        route = feature;
                    } else if(feature.getString("type").toLowerCase().compareTo("feature") == 0){
                        messages.put(feature);
                    }
                }

                updateDisplay(queryLocation);
            } catch (JSONException e) {
                Log.d("com.mogolinc", String.format("JSON parsing failure: %s", e.getMessage()));
                e.printStackTrace();
            } finally {
                // Runs on same thread as onLocationChanged, no need for synchronized
                Log.d("com.mogolinc", "clearing requestInProgress");
                requestInProgress = false;
            }
        }
    }

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {

    }

    @Override
    public void onProviderEnabled(String provider) {

    }

    @Override
    public void onProviderDisabled(String provider) {

    }
}
