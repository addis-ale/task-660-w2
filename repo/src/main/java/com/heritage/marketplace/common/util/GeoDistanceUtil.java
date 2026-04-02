package com.heritage.marketplace.common.util;

import org.springframework.stereotype.Component;

@Component
public class GeoDistanceUtil {

    private static final double EARTH_RADIUS_MILES = 3958.8;

    public double haversineMiles(double lat1, double lon1, double lat2, double lon2) {
        double lat1Rad = Math.toRadians(lat1);
        double lon1Rad = Math.toRadians(lon1);
        double lat2Rad = Math.toRadians(lat2);
        double lon2Rad = Math.toRadians(lon2);

        double dLat = lat2Rad - lat1Rad;
        double dLon = lon2Rad - lon1Rad;

        double a = Math.pow(Math.sin(dLat / 2), 2)
            + Math.cos(lat1Rad) * Math.cos(lat2Rad) * Math.pow(Math.sin(dLon / 2), 2);
        double c = 2 * Math.asin(Math.sqrt(a));
        return EARTH_RADIUS_MILES * c;
    }
}
