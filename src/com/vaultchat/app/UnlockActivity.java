package com.vaultchat.app;

import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Intent;
import android.hardware.fingerprint.FingerprintManager;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import javax.crypto.Cipher;

/** PIN or fingerprint unlock. The master key is never stored in the clear. */
public class UnlockActivity extends Activity {

    private PinPad pad;
    private Button bioBtn;
    private TextView note;
    private int attempts = 0;
    private CancellationSignal cancel;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.BG);
        LinearLayout root = Ui.column(this);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.pad(root, this, 24, 64, 24, 32);

        TextView brand = Ui.title(this, "VaultChat");
        brand.setGravity(Gravity.CENTER);
        root.addView(brand);
        root.addView(Ui.spacer(this, 6));
        TextView sub = Ui.label(this, "Enter your PIN to unlock");
        sub.setGravity(Gravity.CENTER);
        root.addView(sub);
        root.addView(Ui.spacer(this, 30));

        pad = new PinPad(this, new PinPad.OnPin() {
            public void onPin(String pin) {
                tryUnlock(pin);
            }
        });
        root.addView(pad);

        root.addView(Ui.spacer(this, 18));
        note = Ui.label(this, "");
        note.setGravity(Gravity.CENTER);
        root.addView(note);

        bioBtn = Ui.primary(this, "Unlock with fingerprint");
        bioBtn.setVisibility(Bio.available(this) && Bio.enrolled(this) ? Button.VISIBLE : Button.GONE);
        bioBtn.setOnClickListener(new android.view.View.OnClickListener() {
            public void onClick(android.view.View v) {
                startFingerprint();
            }
        });
        LinearLayout.LayoutParams p = Ui.lp(LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 52));
        p.setMargins(0, Ui.dp(this, 16), 0, 0);
        root.addView(bioBtn, p);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void tryUnlock(String pin) {
        try {
            android.content.SharedPreferences sp = getSharedPreferences("vc", 0);
            byte[] salt = Crypto.unb64(sp.getString("salt", ""));
            int iter = sp.getInt("iter", 150000);
            byte[] wrapped = Crypto.unb64(sp.getString("wrap", ""));
            byte[] kek = Crypto.pbkdf2(pin.toCharArray(), salt, iter, 32);
            byte[] mk = Crypto.decrypt(kek, wrapped);

            Vault v = Vault.get(this);
            v.setKey(mk);
            v.load();
            open();
        } catch (Exception e) {
            attempts++;
            note.setText("Wrong PIN (" + attempts + " attempt" + (attempts == 1 ? "" : "s") + ")");
            pad.reset();
        }
    }

    private void startFingerprint() {
        try {
            final Cipher cipher = Bio.decryptCipher(this);
            FingerprintManager fm = (FingerprintManager) getSystemService(FINGERPRINT_SERVICE);
            if (fm == null) {
                note.setText("Fingerprint not available");
                return;
            }
            cancel = new CancellationSignal();
            note.setText("Touch the sensor");
            fm.authenticate(new FingerprintManager.CryptoObject(cipher), cancel, 0,
                    new FingerprintManager.AuthenticationCallback() {
                        @Override
                        public void onAuthenticationSucceeded(FingerprintManager.AuthenticationResult result) {
                            try {
                                byte[] mk = Bio.unwrap(UnlockActivity.this, result.getCryptoObject().getCipher());
                                Vault v = Vault.get(UnlockActivity.this);
                                v.setKey(mk);
                                v.load();
                                open();
                            } catch (Exception e) {
                                note.setText("Fingerprint key invalid. Use your PIN.");
                            }
                        }

                        @Override
                        public void onAuthenticationError(int code, CharSequence msg) {
                            note.setText(msg == null ? "Fingerprint error" : msg.toString());
                        }

                        @Override
                        public void onAuthenticationFailed() {
                            note.setText("Not recognised. Try again.");
                        }
                    }, null);
        } catch (Exception e) {
            note.setText("Fingerprint unavailable: " + e.getClass().getSimpleName());
        }
    }

    private void open() {
        startActivity(new Intent(this, HomeActivity.class));
        finish();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (cancel != null) {
            cancel.cancel();
            cancel = null;
        }
    }

    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }
}
