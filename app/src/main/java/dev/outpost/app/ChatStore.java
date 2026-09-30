package dev.outpost.app;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/** One private local conversation. Interrupted drafts remain visible after process recovery. */
final class ChatStore extends SQLiteOpenHelper {
    record Source(String title, Evidence.Locator locator) {}
    record Turn(String id, String question, String answer, String status, List<Source> sources) {
        Turn withAnswer(String value, String state) { return new Turn(id, question, value, state, sources); }
    }
    ChatStore(Context context) { this(context, "chat.db"); }
    ChatStore(Context context, String name) { super(context, name, null, 1); }
    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE turns (sequence INTEGER PRIMARY KEY AUTOINCREMENT,id TEXT NOT NULL UNIQUE,question TEXT NOT NULL,answer TEXT NOT NULL,status TEXT NOT NULL,sources TEXT NOT NULL)");
    }
    @Override public void onUpgrade(SQLiteDatabase db, int old, int next) { throw new IllegalStateException("Unsupported chat migration"); }
    synchronized void save(Turn turn) {
        try {
            JSONArray refs = new JSONArray();
            for (Source source : turn.sources()) refs.put(new JSONObject().put("title", source.title()).put("locator", source.locator().toJson()));
            ContentValues v = new ContentValues(); v.put("id", turn.id()); v.put("question", turn.question());
            v.put("answer", turn.answer()); v.put("status", turn.status()); v.put("sources", refs.toString());
            SQLiteDatabase db = getWritableDatabase();
            if (db.update("turns", v, "id=?", new String[]{turn.id()}) == 0) db.insertOrThrow("turns", null, v);
        } catch (org.json.JSONException e) { throw new IllegalStateException(e); }
    }
    synchronized List<Turn> turns() {
        List<Turn> turns = new ArrayList<>();
        try (Cursor c = getReadableDatabase().rawQuery("SELECT id,question,answer,status,sources FROM turns ORDER BY sequence", null)) {
            while (c.moveToNext()) {
                JSONArray refs = new JSONArray(c.getString(4)); List<Source> sources = new ArrayList<>();
                for (int i=0;i<refs.length();i++) {
                    JSONObject source=refs.getJSONObject(i), l=source.getJSONObject("locator");
                    sources.add(new Source(source.getString("title"),new Evidence.Locator(l.getString("documentId"),l.getInt("revision"),l.getString("kind"),l.getInt("ordinal"),l.getString("contentSha256"))));
                }
                turns.add(new Turn(c.getString(0),c.getString(1),c.getString(2),c.getString(3),List.copyOf(sources)));
            }
        } catch (org.json.JSONException e) { throw new IllegalStateException("Could not read the conversation",e); }
        return List.copyOf(turns);
    }
    synchronized void recover() {
        ContentValues v=new ContentValues(); v.put("status","interrupted");
        getWritableDatabase().update("turns",v,"status=?",new String[]{"pending"});
    }
    synchronized void clear() { getWritableDatabase().delete("turns",null,null); }
    synchronized void remove(String id) { getWritableDatabase().delete("turns","id=?",new String[]{id}); }
}
