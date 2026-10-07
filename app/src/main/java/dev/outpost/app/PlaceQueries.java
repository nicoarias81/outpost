package dev.outpost.app;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded, deterministic place answers. Coordinates and entity lists never come from model output. */
final class PlaceQueries {
    static final int MAX_ROWS=20_000, MAX_SCAN_CHARS=32_000_000, MAX_RESULTS=5;
    static final double DEFAULT_RADIUS=2_000, MAX_RADIUS=50_000;
    enum Mode { LOOKUP, NEAR, AREA, UNSCOPED }
    record Request(Mode mode,String category,String subject,String area,double radius,String problem) {}
    record Answer(String status,String text,List<ChatStore.Source> sources,int matched,double radius) {}
    private record Pointer(String document,int ordinal) {}
    private record Place(Pointer pointer,OsmImporter.Feature feature) {}
    private record Found(Place place,double distance) {}
    private static final class Snapshot {
        final byte[] facts; final Pointer first; Pointer conflicting;
        Snapshot(byte[] facts,Pointer first){this.facts=facts;this.first=first;}
    }
    private record Catalog(Map<String,Snapshot> identities,List<Place> named,int names,int rows) {}
    private static final class Limit extends RuntimeException {}
    private static Pattern pattern(String s){return Pattern.compile(s,Pattern.CASE_INSENSITIVE|Pattern.UNICODE_CASE);}
    private static final Pattern RADIUS=pattern("\\b(?:within|a menos de)\\s+([0-9]+(?:[.,][0-9]+)?)\\s*(kilometers?|kilometres?|km|meters?|metres?|miles?|mi|m)\\b");
    private static final Pattern NEAR=pattern("\\s+(?:near|around|close to|in the area of|in the vicinity of|cerca de|alrededor de|por la zona de)\\s+");
    private static final Pattern IN=pattern("\\s+(?:in|en)\\s+");
    private static final Pattern CITATION_SUFFIX=pattern("\\s*[.!?]\\s+(?:please\\s+)?cite\\s+(?:the\\s+)?(?:OSM\\s+)?sources?[.!?]?\\s*$");
    private static final Pattern STREET_LOOKUP=pattern("^(?:which|what)\\s+street\\s+is\\s+(.+?)\\s+on$");
    private static final Pattern ADDRESS_LOOKUP=pattern("^(?:what is|what's)\\s+(?:the\\s+)?address\\s+of\\s+(.+)$");
    private static final Pattern LOOKUP=pattern("^(?:where is|where s|where's|locate|find|d[oó]nde (?:queda|est[aá]))\\s+");
    private static final String[] CATEGORIES={"restaurant","museum","park","cafe","pharmacy","supermarket","hotel","hospital","fuel","drinking water","charging station"};
    private static final String[] ALIASES={"restaurants?|restaurantes?","museums?|museos?","parks?|parques?","cafes?|caf[eé]s|coffee shops?","pharmac(?:y|ies)|farmacias?","supermarkets?|supermercados?","hotels?|hoteles?","hospitals?|hospitales?","gas stations?|petrol stations?|fuel stations?","drinking water|water fountains?|agua potable","charging stations?|ev chargers?"};
    private static final Set<String> REQUEST_WORDS=Set.of("what","which","where","can","i","find","are","is","there","any","show","me","list","a","an","the","some","please","nearby","que","cuales","donde","hay","puedo","encontrar","un","una","los","las","el","la","mostrar");

    static Request parse(String question,List<ChatStore.Turn> history) {
        Request previous=null;
        for(ChatStore.Turn turn:history.subList(Math.max(0,history.size()-4),history.size())) {
            if(turn.status().equals("complete")||turn.status().equals("limit"))previous=parseOne(turn.question(),previous);
        }
        return parseOne(question,previous);
    }
    static Request parseOne(String question,Request previous) {
        String q=CITATION_SUFFIX.matcher(question.trim()).replaceFirst("").replaceAll("^[¿¡]+","").replaceAll("[?!。]+$","").trim();
        if(q.length()>600)return null;
        String norm=Library.normalize(q);
        if(norm.matches(".*\\b(pdf|csv|txt|document|documents|manual|email|downloaded|file|files|function|variable|sensor|valve|switch)\\b.*"))return null;
        Matcher street=STREET_LOOKUP.matcher(q),address=ADDRESS_LOOKUP.matcher(q);
        if(street.matches())return parseOne("Where is "+street.group(1),previous);
        if(address.matches())return parseOne("Where is "+address.group(1),previous);
        Matcher follow=pattern("^(in|en|near|around|cerca de)\\s+(.+)$").matcher(q);
        if(previous!=null && follow.matches()) {
            boolean area=follow.group(1).equalsIgnoreCase("in")||follow.group(1).equalsIgnoreCase("en");
            if(previous.mode()==Mode.LOOKUP && area)return new Request(Mode.LOOKUP,"",previous.subject(),cleanName(follow.group(2)),previous.radius(),"");
            if(previous.mode()==Mode.NEAR && area)return new Request(Mode.NEAR,previous.category(),previous.subject(),cleanName(follow.group(2)),previous.radius(),"");
            if(!previous.category().isEmpty())return new Request(area?Mode.AREA:Mode.NEAR,previous.category(),cleanName(follow.group(2)),"",previous.radius(),"");
        }
        double radius=DEFAULT_RADIUS;String problem="";Matcher radiusMatch=RADIUS.matcher(q);
        if(radiusMatch.find()) {
            try{radius=Double.parseDouble(radiusMatch.group(1).replace(',','.'));String unit=radiusMatch.group(2).toLowerCase(Locale.ROOT);if(unit.startsWith("k"))radius*=1000;else if(unit.startsWith("mi"))radius*=1609.344;}catch(NumberFormatException e){radius=Double.NaN;}
            if(!Double.isFinite(radius)||radius<1||radius>MAX_RADIUS)problem="Choose a search radius from 1 m to 50 km.";
            String rest=q.substring(radiusMatch.end());
            // "restaurants within 500 m of X" and "restaurants near X within 500 m".
            q=q.substring(0,radiusMatch.start())+(rest.matches("(?is)^\\s+(?:of|from|de)\\s+.*")?rest.replaceFirst("(?i)^\\s+(?:of|from|de)\\s+"," near "):rest);
            q=q.trim();
            if(q.isEmpty()&&previous!=null&&previous.mode()==Mode.NEAR)return new Request(Mode.NEAR,previous.category(),previous.subject(),previous.area(),radius,problem);
        } else if(norm.contains("within ")||norm.contains("a menos de "))problem="Use a radius such as 'within 500 m of Central Station'.";
        Matcher near=NEAR.matcher(q);String head=q,subject="",area="";Mode mode=Mode.UNSCOPED;
        if(near.find()){head=q.substring(0,near.start()).trim();subject=cleanName(q.substring(near.end()));mode=Mode.NEAR;
            Matcher locality=IN.matcher(subject);if(locality.find()){area=cleanName(subject.substring(locality.end()));subject=cleanName(subject.substring(0,locality.start()));}}
        else {Matcher in=IN.matcher(q);if(in.find()){head=q.substring(0,in.start()).trim();subject=cleanName(q.substring(in.end()));mode=Mode.AREA;}}
        String category="",remaining=head;
        if(mode==Mode.UNSCOPED&&Library.normalize(head).matches("^(what is|what are|explain|define|tell me about|why|how)\\b.*"))return null;
        for(int i=0;i<CATEGORIES.length;i++){Matcher m=pattern("\\b(?:"+ALIASES[i]+")\\b").matcher(head);if(m.find()){category=CATEGORIES[i];remaining=m.replaceAll(" ");break;}}
        boolean onlyRequestWords=!category.isEmpty();
        for(String word:Library.normalize(remaining).split(" +"))if(!word.isEmpty()&&!REQUEST_WORDS.contains(word))onlyRequestWords=false;
        Matcher lookup=LOOKUP.matcher(head);
        // A named place may itself contain a category word, such as "River Park".
        if(lookup.find()&&!onlyRequestWords) {
            if(mode==Mode.NEAR)return new Request(mode,category,subject,area,radius,"Ask for a category near a reference place, or ask where a named place is.");
            area=mode==Mode.AREA?subject:"";subject=cleanName(head.substring(lookup.end()));
            return subject.isEmpty()?null:new Request(Mode.LOOKUP,"",subject,area,radius,problem);
        }
        if(category.isEmpty())return null;
        if(!onlyRequestWords)problem="I can search imported places by category, location and distance. Extra filters or ratings are not supported yet; please simplify the place question.";
        if(subject.isEmpty()&&mode!=Mode.UNSCOPED)problem="Name the city or reference place to search around.";
        if(mode==Mode.NEAR&&Set.of("me","here","my location","aqui","mi ubicacion").contains(Library.normalize(subject)))problem="I don't have your current position. Name a reference place, for example 'museums near Central Station'.";
        return new Request(mode,category,subject,area,radius,problem);
    }
    private static String cleanName(String s){return s.trim().replaceAll("^[\\\"“‘']+|[\\\"”’'.]+$","").trim();}
    private static String nameKey(String s){return Library.normalize(s).replaceFirst("^(?:the|el|la) +","");}
    private static Set<String> names(OsmImporter.Feature f) {
        Set<String> names=new LinkedHashSet<>();
        f.tags().forEach((k,v)->{if(k.equals("name")||k.startsWith("name:")||Set.of("alt_name","official_name","short_name","loc_name").contains(k))for(String n:v.split(";"))if(!n.isBlank())names.add(nameKey(n));});
        return names;
    }
    private static boolean inArea(OsmImporter.Feature f,String area) {
        String key=nameKey(area);
        for(String tag:List.of("addr:city","addr:town","addr:village","addr:suburb","addr:district","is_in:city","is_in"))for(String value:f.tags().getOrDefault(tag,"").split("[,;]"))if(!key.isEmpty()&&nameKey(value).equals(key))return true;
        return false;
    }
    private static boolean category(OsmImporter.Feature f,String wanted) {
        return switch(wanted) {
            case "restaurant","cafe","pharmacy","hospital","fuel","drinking water","charging station" -> f.tags().getOrDefault("amenity","").equals(wanted.replace(' ','_'));
            case "museum","hotel" -> f.tags().getOrDefault("tourism","").equals(wanted);
            case "park" -> f.tags().getOrDefault("leisure","").equals("park")||f.tags().getOrDefault("boundary","").equals("national_park");
            case "supermarket" -> f.tags().getOrDefault("shop","").equals("supermarket");
            default -> false;
        };
    }
    private static String identity(Place p){return p.feature().id()>0?p.feature().key():p.pointer().document()+"/"+p.feature().key();}
    private static byte[] facts(OsmImporter.Feature f) {
        try{return MessageDigest.getInstance("SHA-256").digest((f.latitude()+"/"+f.longitude()+"/"+f.positionKind()+"/"+new org.json.JSONObject(f.tags()).toString()).getBytes(StandardCharsets.UTF_8));}
        catch(Exception e){throw new IllegalStateException(e);}
    }
    private interface Visitor {void visit(Place place);}
    private static int scan(SQLiteDatabase db,BooleanSupplier canceled,Visitor visitor) {
        int rows=0,chars=0;
        try(Cursor c=db.rawQuery("SELECT f.document_id,f.ordinal,f.payload FROM osm_features f JOIN documents d ON d.id=f.document_id WHERE d.format='osm' ORDER BY f.document_id,f.ordinal LIMIT 20001",null)) {
            while(c.moveToNext()) {
                if(canceled.getAsBoolean())throw new CancellationException();
                if(++rows>MAX_ROWS)throw new Limit();String payload=c.getString(2);chars+=payload.length();if(chars>MAX_SCAN_CHARS)throw new Limit();
                visitor.visit(new Place(new Pointer(c.getString(0),c.getInt(1)),OsmImporter.Feature.fromJson(payload)));
            }
        }
        return rows;
    }
    private static Catalog catalog(SQLiteDatabase db,Request request,BooleanSupplier canceled) {
        Map<String,Snapshot> identities=new HashMap<>();List<Place> matched=new ArrayList<>();Set<String> matchedIds=new HashSet<>();
        int rows=scan(db,canceled,p->{String key=identity(p);byte[] facts=facts(p.feature());Snapshot old=identities.get(key);
            if(old==null)identities.put(key,new Snapshot(facts,p.pointer()));else if(!java.util.Arrays.equals(old.facts,facts))old.conflicting=p.pointer();
            if((request.mode()==Mode.LOOKUP||request.mode()==Mode.NEAR)&&names(p.feature()).contains(nameKey(request.subject()))&&(request.area().isEmpty()||inArea(p.feature(),request.area()))) {
                if(matchedIds.add(key)&&matched.size()<MAX_RESULTS)matched.add(p);
            }
        });
        return new Catalog(identities,List.copyOf(matched),matchedIds.size(),rows);
    }
    static double meters(double lat1,double lon1,double lat2,double lon2) {
        double a=Math.toRadians(lat1),b=Math.toRadians(lat2),dy=b-a,dx=Math.toRadians(lon2-lon1);
        double h=Math.sin(dy/2)*Math.sin(dy/2)+Math.cos(a)*Math.cos(b)*Math.sin(dx/2)*Math.sin(dx/2);
        return 6_371_008.8*2*Math.asin(Math.sqrt(Math.max(0,Math.min(1,h))));
    }
    private static Answer message(String status,String text){return new Answer(status,text,List.of(),0,0);}
    static Answer answer(Library library,String question,List<ChatStore.Turn> history,BooleanSupplier canceled) {
        Request q=parse(question,history);if(q==null)return null;
        if(!q.problem().isEmpty())return message("unsupported",q.problem());
        if(q.mode()==Mode.UNSCOPED)return message("clarify","Which city or reference place should I search? For example, '"+plural(q.category())+" near Central Station' or '"+plural(q.category())+" in Madrid'.");
        try {
            SQLiteDatabase db=library.getReadableDatabase();Catalog data=catalog(db,q,canceled);
            if(data.rows()==0)return message("no_data","No OpenStreetMap place data is imported. Add an OSM XML or Overpass JSON file in Settings, then ask again.");
            Place anchor=null;
            if(q.mode()==Mode.LOOKUP||q.mode()==Mode.NEAR) {
                if(data.names()==0)return message("not_found","I couldn't find '"+display(q.subject())+"'"+(q.area().isEmpty()?"":" with a recorded locality of '"+display(q.area())+"'")+" in the imported place data. Try its recorded name or include the city. This does not mean the place does not exist.");
                for(Place p:data.named())if(data.identities().get(identity(p)).conflicting!=null)return conflict(library,data.identities().get(identity(p)),q.subject());
                if(data.names()>1) {
                    List<ChatStore.Source> sources=new ArrayList<>();StringBuilder text=new StringBuilder("Several imported places match '").append(display(q.subject())).append("'. Include the city in your question:\n");
                    for(Place p:data.named())appendPlace(text,sources,library,p,Double.NaN);
                    if(data.names()>MAX_RESULTS)text.append("More matching places are present; narrow the name or area.\n");
                    return new Answer("ambiguous",text.toString(),List.copyOf(sources),data.names(),0);
                }
                anchor=data.named().get(0);
                if(q.mode()==Mode.LOOKUP) {
                    List<ChatStore.Source> sources=new ArrayList<>();StringBuilder text=new StringBuilder("From the imported place data:\n");appendPlace(text,sources,library,anchor,Double.NaN);text.append("\nSaved OSM information; current conditions are not checked.");
                    return new Answer("found",text.toString(),List.copyOf(sources),1,0);
                }
                if(anchor.feature().latitude()==null)return message("missing_coordinates","The imported record for '"+display(anchor.feature().name())+"' has no usable coordinates. I cannot measure nearby distances from it. Choose another reference place.");
            }
            final Place center=anchor;
            Comparator<Found> order=Comparator.comparingDouble(Found::distance).thenComparing(p->nameKey(p.place().feature().name())).thenComparing(p->identity(p.place()));
            PriorityQueue<Found> closest=new PriorityQueue<>(MAX_RESULTS,order.reversed());Set<String> seen=new HashSet<>(),unknown=new HashSet<>();int[] matched={0};Snapshot[] conflict={null};String[] conflictName={""};
            scan(db,canceled,p->{if(!category(p.feature(),q.category()))return;
                String id=identity(p);double distance=0;
                if(center!=null&&id.equals(identity(center)))return;
                if(q.mode()==Mode.AREA){if(!inArea(p.feature(),q.subject()))return;}
                else {if(p.feature().latitude()==null){unknown.add(id);return;}distance=meters(center.feature().latitude(),center.feature().longitude(),p.feature().latitude(),p.feature().longitude());if(distance>q.radius()+1e-7)return;}
                Snapshot source=data.identities().get(id);if(source.conflicting!=null){conflict[0]=source;conflictName[0]=p.feature().name();return;}
                if(!seen.add(id))return;matched[0]++;closest.add(new Found(p,distance));if(closest.size()>MAX_RESULTS)closest.poll();
            });
            if(conflict[0]!=null)return conflict(library,conflict[0],conflictName[0]);
            List<ChatStore.Source> sources=new ArrayList<>();StringBuilder text=new StringBuilder();
            if(center!=null){int ref=source(sources,library,center);text.append("Within ").append(distance(q.radius())).append(" of the recorded position of ").append(display(center.feature().name())).append(" [").append(ref).append("] (approximate straight-line distance):\n");}
            else text.append("Imported ").append(plural(q.category())).append(" with a recorded locality of '").append(display(q.subject())).append("' (alphabetical order):\n");
            List<Found> sorted=new ArrayList<>(closest);sorted.sort(order);
            for(Found found:sorted)appendPlace(text,sources,library,found.place(),center==null?Double.NaN:found.distance());
            if(matched[0]==0)text.append("No matching places in the imported data. This does not establish that none exist in that area.\n");
            else if(matched[0]>MAX_RESULTS)text.append("Showing ").append(MAX_RESULTS).append(" of ").append(matched[0]).append(" matching places.\n");
            if(!unknown.isEmpty())text.append(unknown.size()).append(unknown.size()==1?" imported matching record has":" imported matching records have").append(" no coordinates and could not be checked for distance.\n");
            if(center!=null&&!center.feature().positionKind().equals("OSM node"))text.append("The reference uses an approximate geometry center, not an entrance.\n");
            text.append("Saved OSM information; coverage may be incomplete and current conditions are not checked.");
            return new Answer(matched[0]==0?"empty":"found",text.toString(),List.copyOf(sources),matched[0],center==null?0:q.radius());
        }catch(Limit limit){return message("limit","The imported OSM collection is too large for this bounded place search (20,000 records / 32 million source characters). Keep a smaller region in Documents and try again.");}
    }
    private static String plural(String category){return category.equals("pharmacy")?"pharmacies":category.equals("drinking water")?"drinking-water points":category+"s";}
    private static String display(String text){return ResearchPrompt.clean(text.replaceAll("\\[[-0-9]+\\]","").replaceAll("\\s+"," "),160);}
    private static String distance(double meters){return meters<1000?String.format(Locale.ROOT,"%.0f m",meters):String.format(Locale.ROOT,"%.2f km",meters/1000);}
    private static int source(List<ChatStore.Source> sources,Library library,Place place) {
        Library.Metadata meta=library.metadata(place.pointer().document());Evidence.Locator locator=new Evidence.Locator(place.pointer().document(),meta.revision(),"element",place.pointer().ordinal(),meta.sha256());Evidence evidence=library.resolve(locator);
        sources.add(new ChatStore.Source(evidence.title(),locator));return sources.size();
    }
    private static void appendPlace(StringBuilder text,List<ChatStore.Source> sources,Library library,Place place,double distance) {
        OsmImporter.Feature f=place.feature();int citation=source(sources,library,place);
        text.append("• ").append(display(f.name())).append(" [").append(citation).append("]");if(Double.isFinite(distance))text.append(" — ").append(distance(distance));text.append('\n');
        String full=f.tags().getOrDefault("addr:full","");List<String> parts=new ArrayList<>();
        if(!full.isBlank())parts.add(full);else {String street=f.tags().getOrDefault("addr:street","");if(!street.isBlank())parts.add((f.tags().getOrDefault("addr:housenumber","")+" "+street).trim());}
        for(String key:List.of("addr:suburb","addr:city","addr:town","addr:village","addr:country")){String value=f.tags().getOrDefault(key,"");if(!value.isBlank()&&!parts.contains(value))parts.add(value);}
        if(!parts.isEmpty())text.append("  ").append(display(String.join(", ",parts))).append('\n');
        if(f.latitude()!=null)text.append(String.format(Locale.ROOT,"  %.6f, %.6f",f.latitude(),f.longitude())).append(f.positionKind().equals("OSM node")?"\n":" (approximate center, not an entrance)\n");
        else text.append("  Coordinates not supplied.\n");
    }
    private static Answer conflict(Library library,Snapshot snapshot,String name) {
        List<ChatStore.Source> sources=new ArrayList<>();StringBuilder text=new StringBuilder("Imported snapshots disagree about '").append(display(name)).append("'. I cannot choose a current version or rank its distance reliably. Compare these sources and remove an outdated extract in Settings → Documents if appropriate:\n");
        for(Pointer p:List.of(snapshot.first,snapshot.conflicting)){OsmStorage.Row row=library.osmFeature(p.document(),p.ordinal());appendPlace(text,sources,library,new Place(p,row.feature()),Double.NaN);}
        return new Answer("conflict",text.toString(),List.copyOf(sources),0,0);
    }
}
