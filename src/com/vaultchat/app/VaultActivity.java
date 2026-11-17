package com.vaultchat.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.InputStream;

/** Grid of encrypted photos and files. */
public class VaultActivity extends Activity {

    private static final int REQ_PHOTO = 11;
    private static final int REQ_FILE = 12;

    private GridView grid;
    private ItemAdapter adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Vault.get(this).unlocked()) {
            finish();
            return;
        }

        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        Ui.pad(root, this, 16, 40, 16, 8);

        LinearLayout head = Ui.row(this);
        Button back = Ui.ghost(this, "<");
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });
        head.addView(back);
        TextView title = Ui.title(this, "  Vault");
        head.addView(title, Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        root.addView(head);

        root.addView(Ui.spacer(this, 12));

        LinearLayout actions = Ui.row(this);
        Button addPhoto = Ui.primary(this, "Add photo");
        addPhoto.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pick(REQ_PHOTO, "image/*");
            }
        });
        LinearLayout.LayoutParams p1 = Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p1.setMargins(0, 0, Ui.dp(this, 6), 0);
        actions.addView(addPhoto, p1);

        Button addFile = Ui.ghost(this, "Add file");
        addFile.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                pick(REQ_FILE, "*/*");
            }
        });
        LinearLayout.LayoutParams p2 = Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        p2.setMargins(Ui.dp(this, 6), 0, 0, 0);
        actions.addView(addFile, p2);
        root.addView(actions);

        root.addView(Ui.spacer(this, 12));

        grid = new GridView(this);
        grid.setNumColumns(3);
        grid.setHorizontalSpacing(Ui.dp(this, 6));
        grid.setVerticalSpacing(Ui.dp(this, 6));
        grid.setStretchMode(GridView.STRETCH_COLUMN_WIDTH);
        adapter = new ItemAdapter();
        grid.setAdapter(adapter);
        grid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id) {
                open(adapter.itemAt(pos));
            }
        });
        grid.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> parent, View view, int pos, long id) {
                confirmDelete(adapter.itemAt(pos));
                return true;
            }
        });
        root.addView(grid, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private void pick(int code, String type) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(type);
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, REQ_PHOTO == code);
        try {
            startActivityForResult(i, code);
        } catch (Exception e) {
            Ui.toast(this, "No file picker available");
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        try {
            Vault v = Vault.get(this);
            if (data.getClipData() != null) {
                int n = data.getClipData().getItemCount();
                for (int i = 0; i < n; i++) {
                    Uri u = data.getClipData().getItemAt(i).getUri();
                    importUri(u, req);
                }
            } else if (data.getData() != null) {
                importUri(data.getData(), req);
            }
            adapter.notifyDataSetChanged();
            Ui.toast(this, "Encrypted into the vault");
        } catch (Exception e) {
            Ui.toast(this, "Import failed: " + e.getMessage());
        }
    }

    private void importUri(Uri u, int req) throws Exception {
        String mime = getContentResolver().getType(u);
        if (mime == null) mime = req == REQ_PHOTO ? "image/jpeg" : "application/octet-stream";
        String name = "item_" + System.currentTimeMillis();
        InputStream in = getContentResolver().openInputStream(u);
        boolean isPhoto = mime.startsWith("image/");
        Vault.get(this).importStream(in, name, mime, isPhoto ? "photo" : "file");
    }

    private void open(JSONObject o) {
        if (o == null) return;
        final String id = o.optString("id");
        String kind = o.optString("kind");
        if ("photo".equals(kind)) {
            try {
                byte[] raw = Vault.get(this).readBytes(id);
                Bitmap bm = BitmapFactory.decodeByteArray(raw, 0, raw.length);
                if (bm == null) {
                    Ui.toast(this, "Cannot display this image");
                    return;
                }
                ImageView iv = new ImageView(this);
                iv.setImageBitmap(bm);
                iv.setAdjustViewBounds(true);
                iv.setBackgroundColor(0xFF000000);
                new AlertDialog.Builder(this)
                        .setView(iv)
                        .setPositiveButton("Close", null)
                        .show();
            } catch (Exception e) {
                Ui.toast(this, "Could not decrypt");
            }
        } else {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW);
                i.setDataAndType(VaultProvider.uriFor(id), o.optString("mime", "*/*"));
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception e) {
                Ui.toast(this, "No app can open this file type");
            }
        }
    }

    private void confirmDelete(final JSONObject o) {
        if (o == null) return;
        new AlertDialog.Builder(this)
                .setTitle("Delete permanently?")
                .setMessage("This removes the encrypted copy from this phone.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        Vault.get(VaultActivity.this).delete(o.optString("id"));
                        adapter.notifyDataSetChanged();
                    }
                })
                .show();
    }

    private class ItemAdapter extends BaseAdapter {
        JSONArray data() {
            JSONArray a = Vault.get(VaultActivity.this).files();
            return a == null ? new JSONArray() : a;
        }

        JSONObject itemAt(int i) {
            return data().optJSONObject(i);
        }

        public int getCount() {
            return data().length();
        }

        public Object getItem(int i) {
            return itemAt(i);
        }

        public long getItemId(int i) {
            return i;
        }

        public View getView(int i, View reuse, ViewGroup parent) {
            LinearLayout box = Ui.column(VaultActivity.this);
            box.setBackground(Ui.round(Ui.CARD, 14, VaultActivity.this));
            box.setGravity(Gravity.CENTER);
            JSONObject o = itemAt(i);
            String id = o == null ? "" : o.optString("id");
            int h = Ui.dp(VaultActivity.this, 108);

            if (o != null && "photo".equals(o.optString("kind"))) {
                ImageView iv = new ImageView(VaultActivity.this);
                Bitmap bm = Vault.get(VaultActivity.this).thumb(id);
                if (bm != null) {
                    iv.setImageBitmap(bm);
                    iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
                } else {
                    iv.setBackgroundColor(Ui.CARD2);
                }
                box.addView(iv, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, h));
            } else {
                TextView t = Ui.text(VaultActivity.this, o == null ? "?" : o.optString("name", "file"), 11f, Ui.SUB);
                t.setGravity(Gravity.CENTER);
                t.setMaxLines(3);
                box.addView(t, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, h));
            }
            box.setLayoutParams(new GridView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, h));
            return box;
        }
    }
}
