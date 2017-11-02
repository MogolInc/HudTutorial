package com.mogolinc.hudtutorial;

import android.location.Location;


/**
 * Latitude- and longitude-aligned pseudo-rectangular boundary defined by low and high corners.
 */

public class LocationBounds {
    protected Location Low;
    protected Location High;

    /**
     * Constructs a new, empty LocationBounds with uninitialized low and high corners.
     */
    public LocationBounds() {
    }

    /**
     * Constructs a new LocationBounds that is a copy of an existing LocationBounds.
     *
     * @param bounds the LocationBounds to copy
     */
    public LocationBounds(LocationBounds bounds) {
        Low = bounds.Low;
        High = bounds.High;
    }

    /**
     * Constructs a new LocationBounds with low and high corners copied from the input Locations.
     *
     * @param low low corner Location
     * @param high high corner Location
     */
    public LocationBounds(Location low, Location high) {
        Low = new Location(low);
        High = new Location(high);
    }

    /**
     * Sets the low and high corners to the smallest values that will encompass those corners and the input Location.
     * <br /><br />
     * If this LocationBounds has uninitialized low/high corner(s) then the uninitialized corner(s)
     * will be set to copies of the the input location.
     *
     * @param location
     */
    public void AddLocation(Location location) {
        if(Low == null || High == null) {
            Low = new Location(location);
            High = new Location(location);
            return;
        }

        Low.setLatitude(Math.min(location.getLatitude(),Low.getLatitude()));
        Low.setLongitude(Math.min(location.getLongitude(),Low.getLongitude()));
        High.setLatitude(Math.max(location.getLatitude(),High.getLatitude()));
        High.setLongitude(Math.max(location.getLongitude(),High.getLongitude()));
    }

    /**
     * Returns a copy of the low corner Location.
     *
     * @return low corner
     */
    public Location getLowCorner() {
        return new Location(Low);
    }

    /**
     * Returns a copy of the high corner Location.
     *
     * @return high corner
     */
    public Location getHighCorner() {
        return new Location(High);
    }

    /**
     * Returns whether or not these boundaries contain the input Location.
     *
     * @param location input Location
     * @return true if input is within these boundaries
     */
    public boolean Contains(Location location) {
        return Low.getLatitude() <= location.getLatitude() &&
                High.getLatitude() >= location.getLatitude() &&
                Low.getLongitude() <= location.getLongitude() &&
                High.getLongitude() >= location.getLongitude();
    }
}