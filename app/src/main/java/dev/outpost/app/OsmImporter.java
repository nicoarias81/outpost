package dev.outpost.app;

import android.util.JsonReader;
import android.util.JsonToken;
import android.util.Xml;
import org.xmlpull.v1.XmlPullParser;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** Bounded offline OSM XML / Overpass JSON ingestion. No network, XML DTD, or live-place claims. */
final class OsmImporter {
    static final int MAX_BYTES=32*1024*1024, MAX_OBJECTS=100_000, MAX_FEATURES=5_000;
    static final String ATTRIBUTION="© OpenStreetMap contributors · ODbL 1.0";
    static final String LICENSE_URL="https://www.openstreetmap.org/copyright";
    record Feature(String type,long id,int version,String edited,Double latitude,Double longitude,
                   String positionKind,Map<String,String> tags) {
        Feature {tags=java.util.Collections.unmodifiableMap(new java.util.TreeMap<>(tags));}
        String key(){return type+"/"+id;}
        String name(){String name=tags.getOrDefault("name",tags.getOrDefault("name:en",""));return name.isBlank()?key():name;}
        String url(){return id>0?"https://www.openstreetmap.org/"+key():"";}
        String text(String snapshot) {
            StringBuilder b=new StringBuilder(name()).append("\nOSM feature: ").append(key()).append("\nObject version: ").append(version==0?"unknown":Integer.toString(version))
                .append("\nObject last edited: ").append(edited.isEmpty()?"unknown":edited)
                .append("\nExtract timestamp: ").append(snapshot.isEmpty()?"unknown":snapshot)
                .append("\nCoordinates: ").append(latitude==null?"not supplied or not derived":latitude+", "+longitude+" ("+positionKind+")").append('\n');
            for(Map.Entry<String,String> tag:tags.entrySet())b.append(tag.getKey()).append(": ").append(tag.getValue()).append('\n');
            b.append("Imported snapshot; missing tags are unknown. Opening hours are recorded tags, not a live open/closed check.\n").append(ATTRIBUTION);
            return b.toString();
        }
        JSONObject json() {
            try{return new JSONObject().put("type",type).put("id",id).put("version",version).put("edited",edited)
                .put("lat",latitude==null?JSONObject.NULL:latitude).put("lon",longitude==null?JSONObject.NULL:longitude)
                .put("positionKind",positionKind).put("tags",new JSONObject(tags));}
            catch(org.json.JSONException e){throw new IllegalStateException(e);}
        }
        static Feature fromJson(String value) {
            try{JSONObject j=new JSONObject(value),t=j.getJSONObject("tags");Map<String,String> tags=new LinkedHashMap<>();var keys=t.keys();while(keys.hasNext()){String key=keys.next();tags.put(key,t.getString(key));}
                return new Feature(j.getString("type"),j.getLong("id"),j.getInt("version"),j.getString("edited"),j.isNull("lat")?null:j.getDouble("lat"),j.isNull("lon")?null:j.getDouble("lon"),j.getString("positionKind"),tags);}
            catch(org.json.JSONException e){throw new IllegalArgumentException("Invalid stored OSM feature",e);}
        }
    }
    record Extract(List<Feature> features,int objects,int omitted,String sourceFormat,String generator,String snapshot,String bounds) {
        String summary(){return "Imported OSM features: "+features.size()+"\nObjects read: "+objects+"\nUntagged or deleted objects not indexed: "+omitted
            +"\nFormat: "+sourceFormat+"\nGenerator: "+(generator.isEmpty()?"unknown":generator)
            +"\nExtract timestamp: "+(snapshot.isEmpty()?"unknown":snapshot)+"\nDeclared bounds: "+(bounds.isEmpty()?"not supplied":bounds)
            +"\nCoverage may be incomplete. Coordinates are not the device location.\n"+ATTRIBUTION+"\n"+LICENSE_URL;}
    }
    private static final class ObjectData {
        String type,edited="",positionKind="";long id;int version;Double lat,lon;boolean deleted,directPosition,centerPosition;
        final Map<String,String> tags=new LinkedHashMap<>();final List<Long> refs=new ArrayList<>();
    }
    private final BooleanSupplier canceled;
    private final List<ObjectData> tagged=new ArrayList<>();
    private final Map<Long,double[]> nodes=new HashMap<>();
    private final Set<String> identities=new HashSet<>();
    private int objects,omitted,refs;
    private String generator="",snapshot="",bounds="",sourceFormat;
    private OsmImporter(BooleanSupplier canceled){this.canceled=canceled;}
    static Extract parse(File file,BooleanSupplier canceled)throws Exception {
        if(file.length()==0||file.length()>MAX_BYTES)throw new IllegalArgumentException("Choose an OSM extract up to 32 MiB.");
        OsmImporter parser=new OsmImporter(canceled);char first=parser.preflight(file);
        if(first=='<'){parser.sourceFormat="OSM XML";parser.xml(file);}else if(first=='{'){parser.sourceFormat="Overpass JSON";parser.json(file);}else throw new IllegalArgumentException("Use OSM XML or Overpass JSON. PBF and GeoJSON are not supported.");
        List<Feature> features=new ArrayList<>();int characters=0;
        for(ObjectData item:parser.tagged) {
            parser.check();
            if(item.lat==null&&item.type.equals("way")&&!item.refs.isEmpty()) {
                double south=90,north=-90,west=180,east=-180;boolean all=true;
                for(long ref:item.refs){double[] p=parser.nodes.get(ref);if(p==null){all=false;break;}south=Math.min(south,p[0]);north=Math.max(north,p[0]);west=Math.min(west,p[1]);east=Math.max(east,p[1]);}
                // Avoid inventing a center for missing geometry or a dateline-spanning bounding box.
                if(all&&east-west<=180){item.lat=(south+north)/2;item.lon=(west+east)/2;item.positionKind="derived bounding-box center, not an entrance";}
            }
            Feature feature=new Feature(item.type,item.id,item.version,item.edited,item.lat,item.lon,item.positionKind,Map.copyOf(item.tags));
            int length=feature.text(parser.snapshot).length();characters+=length;
            if(length>16_384||characters>6_000_000)throw new IllegalArgumentException("OSM index text exceeds the supported size. Export a smaller area.");
            features.add(feature);
        }
        if(features.isEmpty())throw new IllegalArgumentException("No tagged OSM features were found in this extract.");
        return new Extract(List.copyOf(features),parser.objects,parser.omitted,parser.sourceFormat,parser.generator,parser.snapshot,parser.bounds);
    }
    private static Reader reader(File file)throws Exception {return new BufferedReader(new InputStreamReader(new FileInputStream(file),StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)));}
    private void check(){if(canceled.getAsBoolean()||Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException("OSM import canceled.");}
    private char preflight(File file)throws Exception {
        char first=0;boolean quoted=false,escaped=false,inTag=false;char quote=0;int length=0,depth=0,tokens=0;String tail="";
        try(Reader input=reader(file)){char[] block=new char[4096];int size;
            while((size=input.read(block))!=-1){check();String chunk=new String(block,0,size);
                for(int i=0;i<size;i++){char c=block[i];if(first==0&&!Character.isWhitespace(c)&&c!='\ufeff')first=c;
                    if(first=='{') {
                        if(quoted){if(++length>16_384)throw new IllegalArgumentException("OSM JSON string is too long.");if(escaped)escaped=false;else if(c=='\\')escaped=true;else if(c=='"')quoted=false;}
                        else if(c=='"'){quoted=true;length=0;}else if(c=='{'||c=='['){if(++depth>32)throw new IllegalArgumentException("OSM JSON nesting is too deep.");if(++tokens>2_000_000)throw new IllegalArgumentException("OSM JSON is too complex.");}else if(c=='}'||c==']')depth--;
                    }else if(first=='<') {
                        if(++length>16_384)throw new IllegalArgumentException("OSM XML token is too long.");
                        if(inTag){if(quote!=0){if(c==quote)quote=0;}else if(c=='\''||c=='"')quote=c;else if(c=='>'){inTag=false;length=0;}}
                        else if(c=='<'){inTag=true;length=1;}
                    }
                }
                String scan=tail+chunk;if(first=='<'&&scan.contains("<!DOCTYPE"))throw new IllegalArgumentException("XML document types and entities are not accepted.");tail=scan.substring(Math.max(0,scan.length()-12));
            }
        }
        return first;
    }
    private static String string(String s,int max){if(s==null)return "";if(s.length()>max)throw new IllegalArgumentException("OSM field is too long.");for(int i=0;i<s.length();i++){char c=s.charAt(i);if(c==0)throw new IllegalArgumentException("Invalid OSM text.");if(Character.isHighSurrogate(c)){if(++i>=s.length()||!Character.isLowSurrogate(s.charAt(i)))throw new IllegalArgumentException("Invalid Unicode in OSM text.");}else if(Character.isLowSurrogate(c))throw new IllegalArgumentException("Invalid Unicode in OSM text.");}return s;}
    private static long id(String text){try{if(text==null||!text.matches("-?[0-9]{1,19}"))throw new NumberFormatException();long id=Long.parseLong(text);if(id==0)throw new NumberFormatException();return id;}catch(NumberFormatException e){throw new IllegalArgumentException("OSM object IDs must be nonzero integers.");}}
    private static int version(String value){if(value==null||value.isEmpty())return 0;try{int v=Integer.parseInt(value);if(v<1)throw new NumberFormatException();return v;}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid OSM object version.");}}
    private static String timestamp(String s){if(s==null||s.isEmpty())return "";string(s,40);try{Instant.parse(s);return s;}catch(Exception e){throw new IllegalArgumentException("Invalid OSM timestamp.");}}
    private static Double coordinate(String s,boolean latitude){if(s==null)return null;try{double v=Double.parseDouble(s);if(!Double.isFinite(v)||Math.abs(v)>(latitude?90:180))throw new NumberFormatException();return v;}catch(Exception e){throw new IllegalArgumentException("Invalid OSM coordinates.");}}
    private void tag(ObjectData object,String key,String value){if(key==null||value==null)throw new IllegalArgumentException("OSM tags require both key and value.");key=string(key,256);value=string(value,2048);if(key.isBlank()||object.tags.containsKey(key)||object.tags.size()>=128)throw new IllegalArgumentException("OSM tags are empty, duplicated or too numerous.");object.tags.put(key,value);}
    private void accept(ObjectData o){check();if(++objects>MAX_OBJECTS)throw new IllegalArgumentException("Too many OSM objects. Export a smaller area.");if(!identities.add(o.type+"/"+o.id))throw new IllegalArgumentException("Duplicate OSM object IDs. Use a current extract, not history or change files.");if(o.directPosition&&o.centerPosition||o.directPosition&&!o.type.equals("node")||o.centerPosition&&o.type.equals("node"))throw new IllegalArgumentException("Ambiguous OSM coordinate representation.");if((o.lat==null)!=(o.lon==null))throw new IllegalArgumentException("Incomplete OSM coordinates.");if(o.deleted){omitted++;return;}if(o.type.equals("node")&&o.lat!=null)nodes.put(o.id,new double[]{o.lat,o.lon});if(o.tags.isEmpty()){omitted++;return;}if(tagged.size()>=MAX_FEATURES)throw new IllegalArgumentException("Too many tagged OSM features. Export a smaller area.");tagged.add(o);}
    private static boolean type(String value){return value.equals("node")||value.equals("way")||value.equals("relation");}
    private static String attr(XmlPullParser p,String key){return p.getAttributeValue(null,key);}
    private void xml(File file)throws Exception {
        XmlPullParser p=Xml.newPullParser();p.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL,false);boolean root=false;ObjectData object=null;
        try(Reader input=reader(file)){p.setInput(input);int event;
            while((event=p.nextToken())!=XmlPullParser.END_DOCUMENT){check();if(p.getDepth()>16)throw new IllegalArgumentException("OSM XML nesting is too deep.");if(event==XmlPullParser.DOCDECL)throw new IllegalArgumentException("XML document types are not accepted.");
                if(event==XmlPullParser.START_TAG){String name=p.getName();int depth=p.getDepth();
                    if(depth==1){if(root||!name.equals("osm")||!"0.6".equals(attr(p,"version")))throw new IllegalArgumentException("Expected an OSM XML 0.6 extract.");root=true;generator=string(attr(p,"generator"),256);}
                    else if(depth==2&&type(name)){object=new ObjectData();object.type=name;object.id=id(attr(p,"id"));object.version=version(attr(p,"version"));object.edited=timestamp(attr(p,"timestamp"));object.deleted="false".equals(attr(p,"visible"))||"delete".equals(attr(p,"action"));if(name.equals("node")){object.directPosition=attr(p,"lat")!=null||attr(p,"lon")!=null;object.lat=coordinate(attr(p,"lat"),true);object.lon=coordinate(attr(p,"lon"),false);object.positionKind="OSM node";}}
                    else if(depth==2&&name.equals("bounds")){Double a=coordinate(attr(p,"minlat"),true),b=coordinate(attr(p,"minlon"),false),c=coordinate(attr(p,"maxlat"),true),d=coordinate(attr(p,"maxlon"),false);if(a==null||b==null||c==null||d==null||a>c||b>d)throw new IllegalArgumentException("Invalid extract bounds.");bounds=a+", "+b+" to "+c+", "+d;}
                    else if(depth==2&&name.equals("meta"))snapshot=timestamp(attr(p,"osm_base"));
                    else if(depth==2&&name.equals("remark"))throw new IllegalArgumentException("The OSM export contains an error/remark. Export a complete smaller area.");
                    else if(depth==3&&object!=null){if(name.equals("tag"))tag(object,attr(p,"k"),attr(p,"v"));else if(name.equals("nd")&&object.type.equals("way")){if(++refs>200_000)throw new IllegalArgumentException("Too many OSM geometry references.");object.refs.add(id(attr(p,"ref")));}else if(name.equals("center")){object.centerPosition=true;object.lat=coordinate(attr(p,"lat"),true);object.lon=coordinate(attr(p,"lon"),false);object.positionKind="exported geometry center, not an entrance";}}
                }else if(event==XmlPullParser.END_TAG&&p.getDepth()==2&&object!=null&&p.getName().equals(object.type)){accept(object);object=null;}
            }
        }
        if(!root)throw new IllegalArgumentException("No OSM XML root was found.");
    }
    private void json(File file)throws Exception {
        boolean elements=false,version=false;try(JsonReader r=new JsonReader(reader(file))){r.setLenient(false);r.beginObject();Set<String> keys=new HashSet<>();
            while(r.hasNext()){check();String key=r.nextName();if(!keys.add(key))throw new IllegalArgumentException("Duplicate Overpass JSON field.");switch(key){
                case "version"->{if(!r.nextString().equals("0.6"))throw new IllegalArgumentException("Expected Overpass JSON version 0.6.");version=true;}
                case "generator"->generator=string(r.nextString(),256);
                case "remark"->throw new IllegalArgumentException("Overpass reported an incomplete/error response. Export a smaller area.");
                case "osm3s"->{r.beginObject();Set<String> metaKeys=new HashSet<>();while(r.hasNext()){String metaKey=r.nextName();if(!metaKeys.add(metaKey))throw new IllegalArgumentException("Duplicate Overpass metadata field.");if(metaKey.equals("timestamp_osm_base"))snapshot=timestamp(r.nextString());else skip(r);}r.endObject();}
                case "elements"->{elements=true;r.beginArray();while(r.hasNext()){check();jsonObject(r);}r.endArray();}
                default->skip(r);
            }}r.endObject();if(r.peek()!=JsonToken.END_DOCUMENT)throw new IllegalArgumentException("Unexpected data after Overpass JSON.");
        }
        if(!elements||!version)throw new IllegalArgumentException("Expected an Overpass JSON export with version 0.6 and elements.");
    }
    private void jsonObject(JsonReader r)throws Exception {
        ObjectData o=new ObjectData();Set<String> keys=new HashSet<>();r.beginObject();
        while(r.hasNext()){check();String key=r.nextName();if(!keys.add(key))throw new IllegalArgumentException("Duplicate OSM element field.");switch(key){
            case "type"->o.type=string(r.nextString(),16);case "id"->o.id=id(r.nextString());case "version"->o.version=version(r.nextString());case "timestamp"->o.edited=timestamp(r.nextString());
            case "visible"->o.deleted=!r.nextBoolean();case "lat"->{o.directPosition=true;o.lat=coordinate(r.nextString(),true);o.positionKind="OSM node";}case "lon"->{o.directPosition=true;o.lon=coordinate(r.nextString(),false);}
            case "center"->{o.centerPosition=true;r.beginObject();Set<String> coords=new HashSet<>();while(r.hasNext()){String k=r.nextName();if(!coords.add(k))throw new IllegalArgumentException("Duplicate OSM center field.");if(k.equals("lat"))o.lat=coordinate(r.nextString(),true);else if(k.equals("lon"))o.lon=coordinate(r.nextString(),false);else skip(r);}r.endObject();o.positionKind="exported geometry center, not an entrance";}
            case "tags"->{r.beginObject();while(r.hasNext()){String k=r.nextName();if(r.peek()!=JsonToken.STRING)throw new IllegalArgumentException("OSM tag values must be strings.");tag(o,k,r.nextString());}r.endObject();}
            case "nodes"->{r.beginArray();while(r.hasNext()){if(++refs>200_000)throw new IllegalArgumentException("Too many OSM geometry references.");o.refs.add(id(r.nextString()));}r.endArray();}
            default->skip(r);
        }}r.endObject();if(o.type==null||!type(o.type)||o.id==0)throw new IllegalArgumentException("Expected an OSM node, way or relation with an ID.");accept(o);
    }
    private void skip(JsonReader r)throws Exception {check();r.skipValue();}
}
