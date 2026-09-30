package dev.outpost.app;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/** OSM rows stay outside the document body to avoid the SQLite cursor-window size limit. */
final class OsmStorage {
    record Row(int ordinal,OsmImporter.Feature feature,String text) {}
    static void create(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE osm_features (document_id TEXT NOT NULL,ordinal INTEGER NOT NULL,element_key TEXT NOT NULL,payload TEXT NOT NULL,content TEXT NOT NULL,PRIMARY KEY(document_id,ordinal),UNIQUE(document_id,element_key))");
    }
    static void insert(SQLiteDatabase db,Library.Document document,OsmImporter.Extract extract,String hash,BooleanSupplier canceled) {
        ContentValues d=new ContentValues();d.put("id",document.id());d.put("title",document.title());d.put("category","OpenStreetMap");d.put("source",OsmImporter.ATTRIBUTION);d.put("url",OsmImporter.LICENSE_URL);d.put("date",document.date());d.put("body",extract.summary());
        d.put("format","osm");d.put("language","und");d.put("content_sha256",hash);d.put("imported_at",document.date());
        d.put("content_date",extract.snapshot().isEmpty()?"":java.time.Instant.parse(extract.snapshot()).atZone(java.time.ZoneOffset.UTC).toLocalDate().toString());
        db.insertOrThrow("documents",null,d);
        int ordinal=0;
        for(OsmImporter.Feature feature:extract.features()) {
            if(canceled.getAsBoolean())throw new java.util.concurrent.CancellationException("OSM import canceled.");
            String text=feature.text(extract.snapshot());ordinal++;
            ContentValues row=new ContentValues();row.put("document_id",document.id());row.put("ordinal",ordinal);row.put("element_key",feature.key());row.put("payload",feature.json().toString());row.put("content",text);db.insertOrThrow("osm_features",null,row);
            ContentValues passage=new ContentValues();passage.put("doc_id",document.id());passage.put("part",ordinal);passage.put("content",text);passage.put("search_text",Library.normalize(document.title()+" "+text+" "+aliases(feature)));db.insertOrThrow("passages",null,passage);
        }
    }
    private static String aliases(OsmImporter.Feature f) {
        String amenity=f.tags().getOrDefault("amenity","");
        return switch(amenity){case "pharmacy"->"pharmacies medicine";case "restaurant"->"restaurants food";case "fuel"->"petrol gasoline gas station";case "drinking_water"->"drinking water fountain";case "charging_station"->"electric vehicle charging EV";case "shelter"->"shelters refuge";default->"";};
    }
    static Row row(SQLiteDatabase db,String document,int ordinal) {
        try(Cursor c=db.rawQuery("SELECT ordinal,payload,content FROM osm_features WHERE document_id=? AND ordinal=?",new String[]{document,Integer.toString(ordinal)})) {
            if(!c.moveToFirst())throw new IllegalArgumentException("OSM feature unavailable");return read(c);
        }
    }
    static List<Row> page(SQLiteDatabase db,String document,int offset) {
        List<Row> result=new ArrayList<>();try(Cursor c=db.rawQuery("SELECT ordinal,payload,content FROM osm_features WHERE document_id=? ORDER BY ordinal LIMIT 50 OFFSET ?",new String[]{document,Integer.toString(Math.max(0,offset))})){while(c.moveToNext())result.add(read(c));}return List.copyOf(result);
    }
    static int count(SQLiteDatabase db,String document){try(Cursor c=db.rawQuery("SELECT count(*) FROM osm_features WHERE document_id=?",new String[]{document})){c.moveToFirst();return c.getInt(0);}}
    static void remove(SQLiteDatabase db,String document){db.delete("osm_features","document_id=?",new String[]{document});}
    private static Row read(Cursor c){return new Row(c.getInt(0),OsmImporter.Feature.fromJson(c.getString(1)),c.getString(2));}
}
