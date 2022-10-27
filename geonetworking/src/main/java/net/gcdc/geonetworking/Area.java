package net.gcdc.geonetworking;

import java.nio.ByteBuffer;

/**
 * Area according to ETSI EN 302 931 V1.1.1 "Geographical Area Definition".
 *
 * The class is declared as final, just because there was no need for subclasses yet.
 * If you remove final, make sure to take good care of {@link #equals(Object)} and
 * {@link #hashCode()}.
 */
public final class Area {


    private final Position center;
    private final int      distanceAmeters;
    private final int      distanceBmeters;
    private final int      angleDegreesFromNorth;
    private final Type     type;


    private Area(Position center, int distanceA, int distanceB, int angleDegreesFromNorth, Type type) {
        this.center    = center;
        this.distanceAmeters = validU16Range(distanceA);
        this.distanceBmeters = validU16Range(distanceB);
        this.angleDegreesFromNorth = validDegrees(angleDegreesFromNorth);
        this.type = type;
    }

	@Override public String toString() {
        return "Area [center=" + center + ", distanceAmeters=" + distanceAmeters
                + ", distanceBmeters=" + distanceBmeters + ", angleDegreesFromNorth="
                + angleDegreesFromNorth + ", type=" + type + "]";
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + angleDegreesFromNorth;
        result = prime * result + distanceAmeters;
        result = prime * result + distanceBmeters;
        result = prime * result + ((center == null) ? 0 : center.hashCode());
        result = prime * result + ((type == null) ? 0 : type.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        Area other = (Area) obj;
        if (angleDegreesFromNorth != other.angleDegreesFromNorth)
            return false;
        if (distanceAmeters != other.distanceAmeters)
            return false;
        if (distanceBmeters != other.distanceBmeters)
            return false;
        if (center == null) {
            if (other.center != null)
                return false;
        } else if (!center.equals(other.center))
            return false;
        if (type != other.type)
            return false;
        return true;
    }

    public enum Type {
        CIRCLE    (0),
        RECTANGLE (1),
        ELLIPSE   (2);

        private final int code;
        private Type(int code) { this.code = code; }
        public  int code()     { return code;      }

        public static Type fromCode(int code) {
            for (Type h: Type.values()) { if (h.code() == code) { return h; } }
            throw new IllegalArgumentException("Can't recognize area type: " + code);
        }
    }

    public static Area getFrom(ByteBuffer buffer, Type type) {
        Position center = Position.getFrom(buffer);
        int distanceA = buffer.getShort() & 0xffff;  // Convert to int to remove sign from short.
        int distanceB = buffer.getShort() & 0xffff;
        int angleDegreesFromNorth = buffer.getShort() & 0xffff;
        return new Area(center, distanceA, distanceB, angleDegreesFromNorth, type);
    }

    public ByteBuffer putTo(ByteBuffer buffer) {
        center.putTo(buffer);
        buffer.putShort((short) distanceAmeters);
        buffer.putShort((short) distanceBmeters);
        buffer.putShort((short) angleDegreesFromNorth);
        return buffer;
    }

    public Type type() {
        return type;
    }

    public Position center() {
        return center;
    }

    public boolean contains(Position position) {
        return f(position) >= 0;
    }

    /**  Characteristic function of a geographical area.
     *
     * The function has the following properties:
     * <pre>
     *     = 1 for x = 0 and y = 0 (at the center point)
     *     > 0 inside the geographical area
     *     = 0 at the border of the geographical area
     *     < 0 outside the geographical area
     * </pre>
     * where x, y are the geographical coordinates of a position P in a Cartesian coordinate system
     * with origin in the center of the shape and abscissa parallel to the long side of the shape.
     */
    public double f(Position position) {
        double distance = this.center.distanceInMetersTo(position);
        double bearing = this.center.bearingInDegreesTowards(position);
        double relativeAngle = bearing - angleDegreesFromNorth;
        double x = distance * Math.cos(Math.toRadians(relativeAngle));
        double y = distance * Math.sin(Math.toRadians(relativeAngle));
        double a = distanceAmeters;
        double b = distanceBmeters;
        switch (type) {
            case CIRCLE:
                return 1 - Math.pow(x/a, 2) - Math.pow(x/a, 2);  // distanceB is 0 for circle.
            case ELLIPSE:
                return 1 - Math.pow(x/a, 2) - Math.pow(y/b, 2);
            case RECTANGLE:
                return Math.min(1 - Math.pow(x/a, 2), 1 - Math.pow(y/b, 2));

        }
        throw new IllegalStateException("At a border of an unknown shape");
    }

    public static Area circle(Position center, int radius) {
        return new Area(center, radius, 0, 0, Type.CIRCLE);
    }

    /**
     *
     * @param center               position of the center point
     * @param longDistanceMeters   half the long side -- the (longer) distance between the center point and the short side of the rectangle (perpendicular bisector of the short
side);
     * @param shortDistanceMeters  half the short side -- the (shorter) distance between the center point and the long side of the rectangle (perpendicular bisector of the long
side);
     * @param azimuthAngleDegreesFromNorth  azimuth angle of the long side of the rectangle.
     * @return new rectangular area
     */
    public static Area rectangle(
            Position center,
            int longDistanceMeters,
            int shortDistanceMeters,
            int azimuthAngleDegreesFromNorth) {
        return new Area(center, longDistanceMeters, shortDistanceMeters, azimuthAngleDegreesFromNorth,
                Type.RECTANGLE);
    }

    public static Area ellipse(
            Position center,
            int longSemiAxisMeters,
            int shortSemiAxisMeters,
            int azimuthAngleDegreesFromNorth) {
        return new Area(center, longSemiAxisMeters, shortSemiAxisMeters,
                azimuthAngleDegreesFromNorth, Type.ELLIPSE);
    }

    private int validDegrees(int i) {
        if (0 > i || i > 359) {
            throw new IllegalArgumentException("Valid range 0 to 359");
        }
        return i;
    }

    private int validU16Range(int i) {
        if (0 > i || i > 65535) {
            throw new IllegalArgumentException("Value outside of valid 0 to 65535 range");
        }
        return i;
    }
}
