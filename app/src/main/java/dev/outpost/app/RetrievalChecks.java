package dev.outpost.app;

import java.util.List;

/** Transparent development fixtures, not a measure of general research quality. */
final class RetrievalChecks {
    record Case(String query, String expectedId) {}
    static final List<Case> CASES = List.of(
        new Case("GPS satellites", "gps"),
        new Case("GPS position signals", "gps"),
        new Case("satellites receiver clock", "gps"),
        new Case("GPS coordinates map", "gps"),
        new Case("GPS accuracy buildings", "gps-accuracy"),
        new Case("GPS obstacles trees", "gps-accuracy"),
        new Case("signal reflections walls", "gps-accuracy"),
        new Case("compass magnetic north", "compass"),
        new Case("compass orientation", "compass"),
        new Case("sensor accelerometer", "compass"),
        new Case("magnetic declination", "compass"),
        new Case("difference kW kWh", "energy"),
        new Case("100 W three hours", "energy"),
        new Case("watts kilowatts power", "energy"),
        new Case("solar panel direct current", "solar"),
        new Case("panels modules mounting", "solar"),
        new Case("photovoltaic efficiency temperature", "solar-efficiency"),
        new Case("radiation electricity efficiency", "solar-efficiency"),
        new Case("platypus", null),
        new Case("", null)
    );
}
