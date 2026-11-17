package com.vaultchat.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/** Encrypted notes. */
public class NotesActivity extends Activity {

    private ListView list;
    private NoteAdapter adapter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Vault.get(this).unlocked()) {
            finish();
            return;
        }
        LinearLayout root = Ui.column(this);
        root.setBackgroundColor(Ui.BG);
        Ui.pad(root, this, 16, 40, 16, 12);

        LinearLayout head = Ui.row(this);
        Button back = Ui.ghost(this, "<");
        back.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                finish();
            }
        });
        head.addView(back);
        head.addView(Ui.title(this, "  Notes"), Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button add = Ui.primary(this, "New");
        add.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                edit(null);
            }
        });
        head.addView(add);
        root.addView(head);
        root.addView(Ui.spacer(this, 12));

        list = new ListView(this);
        adapter = new NoteAdapter();
        list.setAdapter(adapter);
        list.setDivider(null);
        root.addView(list, Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private void edit(final JSONObject existing) {
        LinearLayout box = Ui.column(this);
        Ui.pad(box, this, 18, 8, 18, 0);
        final EditText title = Ui.input(this, "Title");
        final EditText body = Ui.input(this, "Write something private...");
        body.setMinLines(6);
        body.setGravity(Gravity.TOP);
        if (existing != null) {
            title.setText(existing.optString("title"));
            body.setText(existing.optString("body"));
        }
        box.addView(title);
        box.addView(Ui.spacer(this, 8));
        box.addView(body);

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "New note" : "Edit note")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        try {
                            String id = existing == null ? null : existing.optString("id");
                            String t = title.getText().toString().trim();
                            if (t.isEmpty()) t = "Untitled";
                            Vault.get(NotesActivity.this).saveNote(id, t, body.getText().toString());
                            adapter.notifyDataSetChanged();
                        } catch (Exception e) {
                            Ui.toast(NotesActivity.this, "Save failed");
                        }
                    }
                })
                .show();
    }

    private class NoteAdapter extends BaseAdapter {
        JSONArray data() {
            JSONArray a = Vault.get(NotesActivity.this).notes();
            return a == null ? new JSONArray() : a;
        }

        public int getCount() {
            return data().length();
        }

        public Object getItem(int i) {
            return data().optJSONObject(i);
        }

        public long getItemId(int i) {
            return i;
        }

        public View getView(final int i, View reuse, ViewGroup parent) {
            JSONObject o = data().optJSONObject(i);
            LinearLayout box = Ui.column(NotesActivity.this);
            box.setBackground(Ui.round(Ui.CARD, 14, NotesActivity.this));
            Ui.pad(box, NotesActivity.this, 16, 14, 16, 14);

            TextView t = Ui.text(NotesActivity.this, o == null ? "" : o.optString("title"), 16f, Ui.TEXT);
            t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
            TextView b = Ui.label(NotesActivity.this, o == null ? "" : o.optString("body"));
            b.setMaxLines(2);
            box.addView(t);
            box.addView(Ui.spacer(NotesActivity.this, 4));
            box.addView(b);

            LinearLayout.LayoutParams p = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            p.setMargins(0, 0, 0, Ui.dp(NotesActivity.this, 10));
            box.setLayoutParams(p);

            box.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v) {
                    edit(data().optJSONObject(i));
                }
            });
            box.setOnLongClickListener(new View.OnLongClickListener() {
                public boolean onLongClick(View v) {
                    final JSONObject note = data().optJSONObject(i);
                    new AlertDialog.Builder(NotesActivity.this)
                            .setTitle("Delete note?")
                            .setNegativeButton("Cancel", null)
                            .setPositiveButton("Delete", new DialogInterface.OnClickListener() {
                                public void onClick(DialogInterface d, int w) {
                                    Vault.get(NotesActivity.this).deleteNote(note.optString("id"));
                                    adapter.notifyDataSetChanged();
                                }
                            }).show();
                    return true;
                }
            });
            return box;
        }
    }
}
