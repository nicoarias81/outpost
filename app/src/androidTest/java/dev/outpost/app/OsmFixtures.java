package dev.outpost.app;

/** Synthetic format fixtures, never product seed data or claims about real places. */
final class OsmFixtures {
    static final String XML="""
        <?xml version="1.0" encoding="UTF-8"?>
        <osm version="0.6" generator="Outpost synthetic tests">
          <bounds minlat="39.9" minlon="-3.2" maxlat="40.2" maxlon="-2.8"/>
          <meta osm_base="2026-09-01T12:00:00Z"/>
          <node id="1" lat="40.0" lon="-3.0"/>
          <node id="2" lat="40.002" lon="-2.998"/>
          <node id="10" version="3" timestamp="2026-08-20T10:00:00Z" lat="40.01" lon="-3.02">
            <tag k="name" v="Fixture Pharmacy"/><tag k="amenity" v="pharmacy"/>
            <tag k="addr:street" v="Sample Lane"/><tag k="opening_hours" v="Mo-Fr 09:00-18:00"/>
            <tag k="note" v="Synthetic fixture, not a real place"/>
          </node>
          <way id="10" version="2"><nd ref="1"/><nd ref="2"/><tag k="name" v="Fixture Cafe"/><tag k="amenity" v="cafe"/></way>
          <relation id="20"><tag k="name" v="Fixture Park"/><tag k="leisure" v="park"/></relation>
          <node id="99" visible="false" lat="40.0" lon="-3.0"><tag k="name" v="Deleted fixture"/></node>
        </osm>
        """;
    static final String JSON="""
        {"version":0.6,"generator":"Outpost synthetic tests","osm3s":{"timestamp_osm_base":"2026-09-02T12:00:00Z"},"elements":[
          {"type":"node","id":30,"lat":40.03,"lon":-3.04,"tags":{"name":"Fixture Water","amenity":"drinking_water","note":"Synthetic fixture"}},
          {"type":"way","id":31,"center":{"lat":40.04,"lon":-3.05},"tags":{"name":"Fixture Charger","amenity":"charging_station","socket:type2":"yes"}}
        ]}
        """;
}
