package com.mogolinc.hudtutorial;

import android.location.Location;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Models a geographic region with successive polygon vertices.
 * <br /><br />
 * The polygon described by the GeoFence object need not be convex.
 */

public class GeoFence {
    protected List<Location> Points;
    protected LocationBounds Bounds;


    /**
     * Constructs a new GeoFence with input Points and LocationBounds.
     *
     * @param points input Points
     * @param bounds input Location Bounds
     */
    public GeoFence(List<Location> points, LocationBounds bounds) {
        Points = points;
        Bounds = new LocationBounds(bounds);
    }


    /**
     * Creates and returns a new rectangular GeoFence surrounding a great circle segment.
     * <br /><br />
     * Used for creating the fence around a Leg.
     *
     * @param start Leg's start location
     * @param end Leg's end location
     * @param padding how far to expand geofence on all sides (total width = padding * 2, length will be distance from l0 to l1 + padding * 2).
     * @return new rectangular GeoFence
     */
    public static GeoFence CreateFenceAroundEdge(Location start, Location end, double padding) {
        List<Location> poly = new ArrayList<>();

        Double bearing = (double)start.bearingTo(end);
        Double b0 = (bearing + 90) % 360;
        Double b1 = (bearing - 90) % 360;

        b1 = (b1 < 0 ? b1 + 360 : b1);

        LocationBounds bounds = new LocationBounds();
        Location point = CalculateDestinationFromLocation(start, b0, padding);
        poly.add(point);
        bounds.AddLocation(point);
        point = CalculateDestinationFromLocation(start, b1, padding);
        poly.add(point);
        bounds.AddLocation(point);
        point = CalculateDestinationFromLocation(end, b1, padding);
        poly.add(point);
        bounds.AddLocation(point);
        point = CalculateDestinationFromLocation(end, b0, padding);
        poly.add(point);
        bounds.AddLocation(point);

        GeoFence ret = new GeoFence(poly, bounds);

        return ret;
    }


    /**
     * Calculates the point at the end of a great circle arc
     * <br /><br />
     * Calculates the end point of the arc starting at input location in the direction of the input
     * bearing, of the input distance in length.
     * <br /><br />
     * Based on http://www.movable-type.co.uk/scripts/latlong.html
     *
     * @param location start of the arc
     * @param bearing initial bearing of the arc
     * @param distance total length of the arc
     * @return Location at the end of the arc segment.
     */
    public static Location CalculateDestinationFromLocation(Location location, double bearing, double distance) {
        Location dest = new Location(location);
        double bR = ToRadian(bearing);
        double dR = distance / 6378137.0;
        double latR = ToRadian(location.getLatitude());
        double lonR = ToRadian(location.getLongitude());

        double dLatR = Math.asin(Math.sin(latR) * Math.cos(dR) + Math.cos(latR) *
                Math.sin(dR) * Math.cos(bR));
        double dLonR = lonR + Math.atan2(Math.sin(bR) * Math.sin(dR) * Math.cos(latR),
                Math.cos(dR) - Math.sin(latR) * Math.sin(dLatR));

        dest.setLatitude(ToDegree(dLatR));
        dest.setLongitude(ToDegree(dLonR));

        return dest;
    }


    /**
     * Returns true if the input Location is contained within this GeoFence.
     *
     * @param location input location
     * @return
     */
    public boolean Contains(Location location) {
        int count = 0;

        for(int i = 0; i < Points.size(); i++) {
            int idx1 = ((i+1) >= Points.size() ? 0 : (i+1));
            if(Intersect(location, Points.get(i), Points.get(idx1)))
                count++;
        }

        return (count & 1) == 1;
    }

    private boolean Intersect(Location loc, Location l0, Location l1) {
        Double eps = 1e-7;

        Double l0y = l0.getLatitude();
        Double l1y = l1.getLatitude();
        Double py = loc.getLatitude();

        Double l0x = l0.getLongitude();
        Double l1x = l1.getLongitude();
        Double px = loc.getLongitude();

        // l0 must be below l1
        if(l0y > l1y) {
            l0y = l1.getLatitude();
            l0x = l1.getLongitude();
            l1y = l0.getLatitude();
            l1x = l0.getLongitude();
        }

        // Check if latitudinal ray with origin = loc
        // crosses l0 and l1.

        // Make sure not on same level as a vertex
        if(l0y == py && l1y == py)
            py += eps;

        // check that this segment actually intersects
        if(py > l1y || py < l0y || px > Math.max(l0x, l1x))
            return false;

        // Check coord is to the left of both points. If so, it must intersect.
        if(px < Math.min(l0x, l1x))
            return true;

        // Now, we know coord is somewhere in the middle
        // of the segment. To find out if it intersects,
        // check if the slope of l0 to coord is greater
        // than the slope of l0 to l1. If it is, then
        // it intersects.

        // Avoid possible divide by apisdk's.
        double m0;
        double m1;
        if(l0x == l1x)
            m0 = Double.MAX_VALUE;
        else
            m0 = (l1y - l0y) / (l1x - l0x);

        if(px == l0x)
            m1 = Double.MAX_VALUE;
        else
            m1 = (py - l0y) / (px - l0x);

        return m1 >= m0;
    }

    /**
     * Return the number of elements in this GeoFence's Points list.
     *
     * @return number of vertices
     */
    public int GetNumVertices() {
        return Points.size();
    }

    /**
     * Returns the boundary vertex at index "idx"
     *
     * @param idx
     * @return vertex as Location
     */
    public Location GetVertexByIdx(int idx) {
        return Points.get(idx);
    }


    protected static double ToRadian(Double deg) {
        return deg / 180.0 * Math.PI;
    }

    protected static double ToDegree(Double rad) {
        return rad * 180.0 / Math.PI;
    }
}