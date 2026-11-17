package com.vaultchat.app;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Dashboard. */
public class HomeActivity extends Activity {

    private TextView photosSub;
    private TextView notesSub;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (!Vault.get(this).unlocked()) {
            startActivity(new Intent(this, UnlockActivity.class));
            finish();
            return;
        }
        build();
    }

    private void build() {
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root = Ui.column(this);
        Ui.pad(root, this, 22, 44, 22, 32);

        LinearLayout head = Ui.row(this);
        TextView t = Ui.title(this, "VaultChat");
        head.addView(t, Ui.lp(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        Button lock = Ui.ghost(this, "Lock");
        lock.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Vault.get(HomeActivity.this).lock();
                startActivity(new Intent(HomeActivity.this, UnlockActivity.class));
                finish();
            }
        });
        head.addView(lock);
        root.addView(head);

        root.addView(Ui.spacer(this, 4));
        root.addView(Ui.label(this, "Everything stays encrypted on this phone."));
        root.addView(Ui.spacer(this, 24));

        photosSub = Ui.label(this, "");
        root.addView(card("Photos & files", photosSub, new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, VaultActivity.class));
            }
        }));

        notesSub = Ui.label(this, "");
        root.addView(card("Private notes", notesSub, new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(HomeActivity.this, NotesActivity.class));
            }
        }));

        LinearLayout chatCard = card("Secure chat", Ui.label(this, "Direct link over your Wi-Fi · end-to-end encrypted"),
                new View.OnClickListener() {
                    public void onClick(View v) {
                        startActivity(new Intent(HomeActivity.this, ChatActivity.class));
                    }
                });
        root.addView(chatCard);

        LinearLayout callCard = card("Voice & video call",
                Ui.label(this, "Runs inside the same encrypted link"),
                new View.OnClickListener() {
                    public void onClick(View v) {
                        startActivity(new Intent(HomeActivity.this, ChatActivity.class));
                    }
                });
        root.addView(callCard);

        root.addView(Ui.spacer(this, 18));
        TextView foot = Ui.label(this,
                "Your PIN derives the vault key locally with PBKDF2 (150,000 rounds). "
                        + "Chat and calls use a fresh ECDH key pair per session, so keys are never reused "
                        + "and no server ever sees plaintext.");
        root.addView(foot);

        scroll.addView(root);
        setContentView(scroll);
    }

    private LinearLayout card(String title, TextView sub, View.OnClickListener click) {
        LinearLayout box = Ui.column(this);
        box.setBackground(Ui.round(Ui.CARD, 18, this));
        Ui.pad(box, this, 18, 16, 18, 16);
        TextView h = Ui.text(this, title, 17f, Ui.TEXT);
        h.setTypeface(h.getTypeface(), android.graphics.Typeface.BOLD);
        box.addView(h);
        box.addView(Ui.spacer(this, 3));
        box.addView(sub);
        LinearLayout.LayoutParams p = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        p.setMargins(0, 0, 0, Ui.dp(this, 12));
        box.setLayoutParams(p);
        box.setOnClickListener(click);
        box.setClickable(true);
        return box;
    }

    @Override
    protected void onResume() {
        super.onResume();
        Vault v = Vault.get(this);
        if (!v.unlocked()) {
            startActivity(new Intent(this, UnlockActivity.class));
            finish();
            return;
        }
        try {
            if (v.files() == null) v.load();
        } catch (Exception ignored) {
        }
        if (photosSub != null) {
            int n = v.files().length();
            long bytes = v.totalBytes();
            photosSub.setText(n + " item" + (n == 1 ? "" : "s") + " · " + readable(bytes) + " encrypted");
        }
        if (notesSub != null) {
            int n = v.notes().length();
            notesSub.setText(n + " note" + (n == 1 ? "" : "s"));
        }
    }

    private static String readable(long b) {
        if (b < 1024) return b + " B";
        if (b < 1024 * 1024) return (b / 1024) + " KB";
        if (b < 1024L * 1024 * 1024) return String.format("%.1f MB", b / 1048576.0);
        return String.format("%.2f GB", b / 1073741824.0);
    }
}
