#!/bin/bash
# Builds VaultChat.apk with aapt2 + javac + d8 + apksigner.
# No Gradle, no plugins, no third-party libraries.
#
# Needs: JDK 17, Android SDK with build-tools 34+ and any platform (android-34 or newer).
#   export ANDROID_HOME=/path/to/Android/Sdk
set -e

export JAVA_HOME="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(command -v javac)")")")}"
export PATH="$JAVA_HOME/bin:$PATH"
# Keep the JVM small; the build needs well under 1 GB.
export JAVA_TOOL_OPTIONS="${JAVA_TOOL_OPTIONS:--Xmx512m -XX:MaxMetaspaceSize=256m}"

SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
BT="$(ls -d "$SDK"/build-tools/*/ 2>/dev/null | sort -V | tail -1)"
AJ="$(ls -d "$SDK"/platforms/*/ 2>/dev/null | sort -V | tail -1)android.jar"
P="$(cd "$(dirname "$0")" && pwd)"
B="$P/build"

if [ ! -f "$AJ" ]; then
  echo "android.jar not found. Set ANDROID_HOME to your SDK path." >&2
  exit 1
fi
BT="${BT%/}"

rm -rf "$B"
mkdir -p "$B/gen" "$B/classes" "$B/dex"

echo "[1/6] resources"
"$BT/aapt2" compile --dir "$P/res" -o "$B/res.zip"

echo "[2/6] link"
"$BT/aapt2" link \
  -o "$B/base.apk" \
  -I "$AJ" \
  --manifest "$P/AndroidManifest.xml" \
  -R "$B/res.zip" \
  --java "$B/gen" \
  --min-sdk-version 24 \
  --target-sdk-version 34 \
  --version-code 1 \
  --version-name 1.0 \
  --auto-add-overlay

echo "[3/6] compile java"
find "$P/src" "$B/gen" -name '*.java' > "$B/srcs.txt"
javac -nowarn -source 8 -target 8 -bootclasspath "$AJ" -classpath "$AJ" \
  -d "$B/classes" @"$B/srcs.txt"

echo "[4/6] dex"
find "$B/classes" -name '*.class' > "$B/classes.txt"
java -Xmx700m -cp "$BT/lib/d8.jar" com.android.tools.r8.D8 \
  --release --min-api 24 --lib "$AJ" --output "$B/dex" @"$B/classes.txt"

echo "[5/6] package"
python3 - "$B/base.apk" "$B/dex/classes.dex" <<'PY'
import sys, zipfile
apk, dex = sys.argv[1], sys.argv[2]
with zipfile.ZipFile(apk, 'a', zipfile.ZIP_DEFLATED) as z:
    z.write(dex, "classes.dex")
PY

echo "[6/6] align + sign"
"$BT/zipalign" -f 4 "$B/base.apk" "$B/aligned.apk"
if [ ! -f "$B/ks.jks" ]; then
  keytool -genkeypair -v -keystore "$B/ks.jks" -storepass vaultchat -keypass vaultchat \
    -alias vaultchat -keyalg RSA -keysize 2048 -validity 10950 \
    -dname "CN=VaultChat, O=VaultChat, C=IN" > /dev/null 2>&1
fi
java -Xmx512m -jar "$BT/lib/apksigner.jar" sign \
  --ks "$B/ks.jks" --ks-pass pass:vaultchat --key-pass pass:vaultchat \
  --out "$B/VaultChat.apk" "$B/aligned.apk"

ls -la "$B/VaultChat.apk"
echo "BUILD OK"
