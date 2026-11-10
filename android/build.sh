#!/bin/bash
# Builds MahmoudSalem.apk from assets/index.html (the offline build of quote-generator.html).
# Keep keystore.jks: future versions must be signed with the same key to install as updates.
set -e
cd "$(dirname "$0")"
SDK=/usr/lib/android-sdk
JAR=$SDK/platforms/android-23/android.jar
PASS=${KS_PASS:-msalem2026}
rm -rf build && mkdir -p build/gen build/obj
[ -f keystore.jks ] || keytool -genkeypair -keystore keystore.jks -alias msalem -keyalg RSA -keysize 2048 -validity 10000 \
  -storepass "$PASS" -keypass "$PASS" -dname "CN=Mahmoud Salem Office, O=Mahmoud Salem, C=EG" >/dev/null 2>&1
aapt package -f -m -J build/gen -M AndroidManifest.xml -S res -A assets -I "$JAR" -0 arsc -F build/base.apk
javac -nowarn --release 8 -cp "$JAR" -d build/obj $(find src build/gen -name '*.java') 2>&1 | grep -v "warning: \[options\]" || true
dalvik-exchange --dex --output=build/classes.dex build/obj
(cd build && aapt add -f base.apk classes.dex >/dev/null)
zipalign -p -f 4 build/base.apk build/aligned.apk
apksigner sign --ks keystore.jks --ks-key-alias msalem --ks-pass pass:"$PASS" --key-pass pass:"$PASS" --out MahmoudSalem.apk build/aligned.apk
apksigner verify --print-certs MahmoudSalem.apk | head -3
ls -lh MahmoudSalem.apk
