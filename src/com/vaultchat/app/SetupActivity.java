package com.vaultchat.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** First run: creates the master key, protected by a six digit PIN. */
public class SetupActivity extends Activity {

    private PinPad pad;
    private TextView subtitle;
    private String first = null;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        SharedPreferences p = getSharedPreferences("vc", 0);
        if (p.contains("salt")) {
            startActivity(new Intent(this, UnlockActivity.class));
            finish();
            return;
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root = Ui.column(this);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.pad(root, this, 24, 48, 24, 32);

        TextView brand = Ui.title(this, "VaultChat");
        brand.setGravity(Gravity.CENTER);
        root.addView(brand);

        TextView tag = Ui.label(this, "Private vault  ·  Secure chat  ·  Encrypted calls");
        tag.setGravity(Gravity.CENTER);
        root.addView(tag);
        root.addView(Ui.spacer(this, 28));

        subtitle = Ui.text(this, "Create a 6-digit PIN", 17f, Ui.TEXT);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle);
        root.addView(Ui.spacer(this, 6));

        TextView warn = Ui.label(this,
                "This PIN is the only key to your vault. Nothing is uploaded anywhere. "
                        + "If you forget it, the data cannot be recovered.");
        warn.setGravity(Gravity.CENTER);
        root.addView(warn);
        root.addView(Ui.spacer(this, 26));

        pad = new PinPad(this, new PinPad.OnPin() {
            public void onPin(String pin) {
                handle(pin);
            }
        });
        root.addView(pad);

        root.addView(Ui.spacer(this, 20));
        TextView fine = Ui.label(this, "AES-256-GCM · PBKDF2-SHA256 150,000 rounds · Android Keystore");
        fine.setGravity(Gravity.CENTER);
        root.addView(fine);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void handle(String pin) {
        if (first == null) {
            first = pin;
            subtitle.setText("Confirm your PIN");
            pad.setStatus("");
            return;
        }
        if (!first.equals(pin)) {
            first = null;
            subtitle.setText("Create a 6-digit PIN");
            pad.setStatus("PINs did not match. Start again.");
            pad.reset();
            return;
        }
        createVault(pin);
    }

    private void createVault(String pin) {
        try {
            byte[] salt = Crypto.rnd(16);
            int iter = 150000;
            byte[] kek = Crypto.pbkdf2(pin.toCharArray(), salt, iter, 32);
            byte[] mk = Crypto.rnd(32);
            byte[] wrapped = Crypto.encrypt(kek, mk);

            getSharedPreferences("vc", 0).edit()
                    .putString("salt", Crypto.b64(salt))
                    .putString("wrap", Crypto.b64(wrapped))
                    .putInt("iter", iter)
                    .apply();

            Vault v = Vault.get(this);
            v.setKey(mk);
            v.load();

            Bio.enroll(this, mk);

            startActivity(new Intent(this, HomeActivity.class));
            finish();
        } catch (Exception e) {
            pad.setStatus("Could not create vault: " + e.getMessage());
            pad.reset();
            first = null;
        }
    }
}
