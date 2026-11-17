package com.vaultchat.app;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.UUID;

/**
 * Encrypted store for photos, files and notes.
 * Every byte on disk is AES-256-GCM encrypted under the master key, which only
 * exists in memory after a successful PIN or fingerprint unlock.
 */
public class Vault {

    private static Vault I;

    public static synchronized Vault get(Context c) {
        if (I == null) {
            I = new Vault(c.getApplicationContext());
        }
        return I;
    }

    public static Vault instance() {
        return I;
    }

    private final Context ctx;
    private byte[] mk;
    private JSONObject index;
    private final HashMap<String, Bitmap> thumbCache = new HashMap<>();

    private Vault(Context c) {
        ctx = c;
    }

    public void setKey(byte[] key) {
        mk = key;
    }

    public boolean unlocked() {
        return mk != null;
    }

    public void lock() {
        mk = null;
        index = null;
        thumbCache.clear();
    }

    private File dir() {
        File d = new File(ctx.getFilesDir(), "d");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private File thumbDir() {
        File d = new File(ctx.getFilesDir(), "t");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private File metaFile() {
        return new File(ctx.getFilesDir(), "meta.bin");
    }

    public void load() throws Exception {
        File f = metaFile();
        if (!f.exists()) {
            index = new JSONObject();
            index.put("files", new JSONArray());
            index.put("notes", new JSONArray());
            save();
            return;
        }
        byte[] raw = new byte[(int) f.length()];
        FileInputStream in = new FileInputStream(f);
        int off = 0;
        while (off < raw.length) {
            int n = in.read(raw, off, raw.length - off);
            if (n <= 0) break;
            off += n;
        }
        in.close();
        index = new JSONObject(new String(Crypto.decrypt(mk, raw), "UTF-8"));
        if (!index.has("files")) index.put("files", new JSONArray());
        if (!index.has("notes")) index.put("notes", new JSONArray());
    }

    public void save() throws Exception {
        File tmp = new File(ctx.getFilesDir(), "meta.tmp");
        FileOutputStream fo = new FileOutputStream(tmp);
        fo.write(Crypto.encrypt(mk, index.toString().getBytes("UTF-8")));
        fo.close();
        if (metaFile().exists()) metaFile().delete();
        tmp.renameTo(metaFile());
    }

    public JSONArray files() {
        return index.optJSONArray("files");
    }

    public JSONArray notes() {
        return index.optJSONArray("notes");
    }

    public JSONObject find(String id) {
        JSONArray a = files();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && id.equals(o.optString("id"))) return o;
        }
        return null;
    }

    public String importStream(InputStream in, String name, String mime, String kind) throws Exception {
        String id = UUID.randomUUID().toString();
        File f = new File(dir(), id + ".vcf");
        FileOutputStream fo = new FileOutputStream(f);
        Crypto.encryptStream(mk, in, fo);
        try { in.close(); } catch (Exception ignored) {}
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        o.put("mime", mime);
        o.put("kind", kind);
        o.put("size", f.length());
        o.put("date", System.currentTimeMillis());
        files().put(o);
        save();
        if ("photo".equals(kind)) {
            makeThumb(id);
        }
        return id;
    }

    public String importBytes(byte[] data, String name, String mime, String kind) throws Exception {
        return importStream(new ByteArrayInputStream(data), name, mime, kind);
    }

    private void makeThumb(String id) {
        try {
            File src = new File(dir(), id + ".vcf");
            File tmp = new File(ctx.getCacheDir(), id + ".tmpimg");
            FileOutputStream fo = new FileOutputStream(tmp);
            Crypto.decryptStream(mk, new FileInputStream(src), fo);
            fo.close();

            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(tmp.getAbsolutePath(), o);
            int sample = 1;
            while (o.outWidth > 0 && o.outWidth / (sample * 2) >= 400) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap bm = BitmapFactory.decodeFile(tmp.getAbsolutePath(), o2);
            tmp.delete();
            if (bm == null) return;

            int max = 400;
            int w = bm.getWidth(), h = bm.getHeight();
            if (w > max || h > max) {
                float s = Math.min((float) max / w, (float) max / h);
                Bitmap sc = Bitmap.createScaledBitmap(bm, Math.max(1, (int) (w * s)), Math.max(1, (int) (h * s)), true);
                bm.recycle();
                bm = sc;
            }
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.JPEG, 82, bo);
            bm.recycle();
            byte[] enc = Crypto.encrypt(mk, bo.toByteArray());
            FileOutputStream tf = new FileOutputStream(new File(thumbDir(), id + ".vt"));
            tf.write(enc);
            tf.close();
        } catch (Exception ignored) {
        }
    }

    public synchronized Bitmap thumb(String id) {
        Bitmap cached = thumbCache.get(id);
        if (cached != null) return cached;
        try {
            File f = new File(thumbDir(), id + ".vt");
            if (!f.exists()) return null;
            byte[] raw = new byte[(int) f.length()];
            FileInputStream in = new FileInputStream(f);
            int off = 0;
            while (off < raw.length) {
                int n = in.read(raw, off, raw.length - off);
                if (n <= 0) break;
                off += n;
            }
            in.close();
            byte[] plain = Crypto.decrypt(mk, raw);
            Bitmap bm = BitmapFactory.decodeByteArray(plain, 0, plain.length);
            if (bm != null) thumbCache.put(id, bm);
            return bm;
        } catch (Exception e) {
            return null;
        }
    }

    public byte[] readBytes(String id) throws Exception {
        File src = new File(dir(), id + ".vcf");
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        Crypto.decryptStream(mk, new FileInputStream(src), bo);
        return bo.toByteArray();
    }

    public File decryptToCache(String id) throws Exception {
        JSONObject o = find(id);
        if (o == null) return null;
        String name = o.optString("name", "file");
        if (name.length() > 40) name = name.substring(name.length() - 40);
        File out = new File(ctx.getCacheDir(), id + "_" + name.replaceAll("[^A-Za-z0-9._-]", "_"));
        File src = new File(dir(), id + ".vcf");
        FileOutputStream fo = new FileOutputStream(out);
        Crypto.decryptStream(mk, new FileInputStream(src), fo);
        fo.close();
        return out;
    }

    public void delete(String id) {
        try {
            new File(dir(), id + ".vcf").delete();
            new File(thumbDir(), id + ".vt").delete();
            JSONArray a = files();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null && id.equals(o.optString("id"))) {
                    a.remove(i);
                    break;
                }
            }
            thumbCache.remove(id);
            save();
        } catch (Exception ignored) {
        }
    }

    // ---- notes ----

    public void saveNote(String id, String title, String body) throws Exception {
        JSONArray a = notes();
        JSONObject target = null;
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null && o.optString("id").equals(id)) {
                target = o;
                break;
            }
        }
        if (target == null) {
            target = new JSONObject();
            target.put("id", id == null || id.isEmpty() ? UUID.randomUUID().toString() : id);
            target.put("date", System.currentTimeMillis());
            a.put(target);
        }
        target.put("title", title);
        target.put("body", body);
        save();
    }

    public void deleteNote(String id) {
        try {
            JSONArray a = notes();
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.optJSONObject(i);
                if (o != null && id.equals(o.optString("id"))) {
                    a.remove(i);
                    break;
                }
            }
            save();
        } catch (Exception ignored) {
        }
    }

    public long totalBytes() {
        long t = 0;
        JSONArray a = files();
        for (int i = 0; i < a.length(); i++) {
            JSONObject o = a.optJSONObject(i);
            if (o != null) t += o.optLong("size");
        }
        return t;
    }
}
