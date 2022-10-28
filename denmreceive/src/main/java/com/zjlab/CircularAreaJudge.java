package com.zjlab;

import lombok.Data;
import lombok.EqualsAndHashCode;
import org.gavaghan.geodesy.Ellipsoid;
import org.gavaghan.geodesy.GeodeticCalculator;
import org.gavaghan.geodesy.GlobalCoordinates;

/**
 * @author xue
 * @create 2022-10-27 9:45
 */
@Data
@EqualsAndHashCode(exclude = "circleCenter")
public class CircularAreaJudge {

    private GlobalCoordinates circleCenter;

    private Integer radius;

    private Double lon;

    private Double lat;

    public CircularAreaJudge(Integer radius, Double lon, Double lat) {
        this.radius = radius;
        this.lon = lon;
        this.lat = lat;
        this.circleCenter = new GlobalCoordinates(lat, lon);
    }

    public Boolean areaJudge(Position position) {
        GlobalCoordinates target = new GlobalCoordinates(position.getLat(), position.getLon());
        double distance = new GeodeticCalculator()
                .calculateGeodeticCurve(Ellipsoid.WGS84, circleCenter, target).getEllipsoidalDistance();
        return distance < radius;
    }


}
