package dev.outpost.app;
import android.content.Context;
import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import org.json.JSONArray;
import org.json.JSONObject;
import java.nio.charset.StandardCharsets;
final class TestLibrary {
    static Library seeded(Context context) {return seeded(context,null);}
    static Library seeded(Context context,String name) {
        Library library=new Library(context,name);
        try(java.io.InputStream input=context.createPackageContext("dev.outpost.app.test",0).getAssets().open("library.json")) {
            JSONArray items=new JSONArray(new String(input.readAllBytes(),StandardCharsets.UTF_8));
            SQLiteDatabase db=library.getWritableDatabase();
            for(int i=0;i<items.length();i++) {
                JSONObject item=items.getJSONObject(i);ContentValues d=new ContentValues();
                for(String key:new String[]{"id","title","category","source","url","date","body"})d.put(key,item.getString(key));
                d.put("content_sha256",Evidence.sha256(item.getString("body")));d.put("language","en");d.put("imported_at",item.getString("date"));
                if(db.insertWithOnConflict("documents",null,d,SQLiteDatabase.CONFLICT_IGNORE)<0)continue;
                int part=0;for(String text:Library.chunks(item.getString("body"))) {
                    ContentValues p=new ContentValues();p.put("doc_id",item.getString("id"));p.put("part",++part);p.put("content",text);p.put("search_text",Library.normalize(item.getString("title")+" "+text));db.insertOrThrow("passages",null,p);
                }
            }
            return library;
        }catch(Exception e){library.close();throw new IllegalStateException(e);}
    }
}
