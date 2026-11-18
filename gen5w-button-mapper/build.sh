#!/bin/sh
# Builds Button Mapper into out/: the app (app/ButtonMapper.apk), the hooks (hooks.jar),
# the signed mod zip and the USB zip.
#
# Needs: JDK 8 to 17 (the d8 in build-tools 30 fails on classes from JDK 21's javac), and these environment variables:
#   ANDROID_JAR    android.jar for API 19 (Android 4.4)
#   BUILD_TOOLS    an Android build-tools folder (d8, aapt, zipalign, apksigner)
#   APP_KEYSTORE   keystore (.p12) that signs the app; APP_KEYSTORE_PASS its password
#   HMGAPI_CP      classpath holding hmgapi.engine.Signing (HMG-API's mod SDK)
#   MOD_KEY        the mod's HMG-API author key
#   MKNEMESISMOD   HMG-API's tools/mknemesismod.sh (or the mod SDK's bin/mknemesismod.sh)
# Keys never go in this repository.
set -eu
cd "$(dirname "$0")"
: "${ANDROID_JAR:?}" "${BUILD_TOOLS:?}" "${APP_KEYSTORE:?}" "${APP_KEYSTORE_PASS:?}" "${HMGAPI_CP:?}" "${MOD_KEY:?}" "${MKNEMESISMOD:?}"

V=$(sed -n 's/.*"version": *"\([^"]*\)".*/\1/p' mod.json | head -1)
B=out/build
rm -rf out && mkdir -p $B/stubs $B/hooks $B/app/classes $B/app/dex $B/mod/app $B/usb/HMGAPI/updates

# hooks.jar: runs inside the car's CarPlay app; compiled against stubs of HMG-API's hook API
javac -encoding UTF-8 -source 8 -target 8 -nowarn -cp "$ANDROID_JAR" -d $B/stubs $(find tools/stubs -name '*.java')
javac -encoding UTF-8 -source 8 -target 8 -nowarn -cp "$ANDROID_JAR:$B/stubs" -d $B/hooks $(find src/hooks -name '*.java')
(cd $B/stubs && jar cf ../stubs.jar .) && (cd $B/hooks && jar cf ../hooks-classes.jar .)
"$BUILD_TOOLS/d8" --min-api 19 --lib "$ANDROID_JAR" --classpath $B/stubs.jar --output $B/mod/hooks.jar $B/hooks-classes.jar

# app/ButtonMapper.apk: gets HMG-API's button broadcasts
javac -encoding UTF-8 -source 8 -target 8 -nowarn -cp "$ANDROID_JAR" -d $B/app/classes $(find src/app/java -name '*.java')
(cd $B/app/classes && jar cf ../classes.jar .)
"$BUILD_TOOLS/d8" --min-api 19 --lib "$ANDROID_JAR" --output $B/app/dex $B/app/classes.jar
"$BUILD_TOOLS/aapt" package -f -M src/app/AndroidManifest.xml -S src/app/res -I "$ANDROID_JAR" -F $B/app/unsigned.apk
(cd $B/app/dex && "$BUILD_TOOLS/aapt" add ../unsigned.apk classes.dex >/dev/null)
"$BUILD_TOOLS/zipalign" -f 4 $B/app/unsigned.apk $B/app/aligned.apk
"$BUILD_TOOLS/apksigner" sign --v4-signing-enabled false --ks "$APP_KEYSTORE" --ks-pass "pass:$APP_KEYSTORE_PASS" \
    --out $B/mod/app/ButtonMapper.apk $B/app/aligned.apk

# signed mod zip (out/gen5w-button-mapper-v<version>.zip, the file HMG-API's mod list points at), fixed timestamps so the same sources give the same bytes
cp mod.json settings.json $B/mod/
mkdir -p $B/mod/icon && cp icon/icon*.png $B/mod/icon/   # the app-menu tile
java -cp "$HMGAPI_CP" hmgapi.engine.Signing sign $B/mod "$MOD_KEY"
(cd $B/mod && find . -exec touch -d '2026-01-01 00:00:00' {} + && \
    zip -X -q ../../gen5w-button-mapper-v$V.zip META-HMGAPI/manifest.json META-HMGAPI/signature \
        app/ButtonMapper.apk hooks.jar mod.json settings.json icon/*.png)

# for the HMG-API app: unzip onto a stick, then install from HMG-API's updates
cp out/gen5w-button-mapper-v$V.zip $B/usb/HMGAPI/updates/
(cd $B/usb && zip -r -q ../../gen5w-button-mapper-v$V-hmgapi-usb.zip HMGAPI)

# for NEMESIS: the USB version and the Package Store file, both carrying the signed zip unchanged
sh "$MKNEMESISMOD" "$PWD/out/gen5w-button-mapper-v$V.zip" "$PWD/$B/nemesis" >/dev/null
mv $B/nemesis/*-nemesis.zip out/gen5w-button-mapper-v$V-nemesis-usb.zip
mv $B/nemesis/*-nemesis-package.zip out/gen5w-button-mapper-v$V-nemesis-package.zip

(cd out && sha256sum gen5w-button-mapper-v$V*.zip > SHA256SUMS && cat SHA256SUMS)
