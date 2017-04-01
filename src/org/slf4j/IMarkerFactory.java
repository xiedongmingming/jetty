package org.slf4j;

public interface IMarkerFactory {
    Marker getMarker(String name);
    boolean exists(String name);
    boolean detachMarker(String name);
    Marker getDetachedMarker(String name);
}
