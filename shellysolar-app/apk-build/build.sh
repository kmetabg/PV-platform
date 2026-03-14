#!/bin/bash
set -e

# ShellySolar APK Build Script
# Builds APK using Android SDK command-line tools (no Android Studio needed)

BUILD_DIR="$(cd "$(dirname "$0")" && pwd)"
ANDROID_JAR="/usr/lib/android-sdk/platforms/android-23/android.jar"
AAPT2="/usr/lib/android-sdk/build-tools/29.0.3/aapt2"
AAPT="/usr/lib/android-sdk/build-tools/29.0.3/aapt"
DX="/usr/bin/dalvik-exchange"
ZIPALIGN="/usr/bin/zipalign"
APKSIGNER="/usr/bin/apksigner"

OUT="$BUILD_DIR/out"
GEN="$OUT/gen"
OBJ="$OUT/obj"
DEX="$OUT/dex"
APK_UNSIGNED="$OUT/shellysolar-unsigned.apk"
APK_ALIGNED="$OUT/shellysolar-aligned.apk"
APK_FINAL="$BUILD_DIR/../ShellySolar.apk"

echo "=== ShellySolar APK Builder ==="
echo ""

# Clean
rm -rf "$OUT"
mkdir -p "$GEN" "$OBJ" "$DEX" "$OUT/compiled_res"

echo "[1/6] Compiling resources..."
# Compile each resource file with aapt2
for f in $(find "$BUILD_DIR/res" -type f -name "*.xml"); do
    "$AAPT2" compile "$f" -o "$OUT/compiled_res/" 2>&1
done

echo "[2/6] Linking resources..."
"$AAPT2" link \
    -I "$ANDROID_JAR" \
    --manifest "$BUILD_DIR/AndroidManifest.xml" \
    --java "$GEN" \
    --auto-add-overlay \
    -o "$APK_UNSIGNED" \
    "$OUT/compiled_res/"*.flat

echo "[3/6] Compiling Java sources..."
# Find all java files
JAVA_FILES=$(find "$BUILD_DIR/src" "$GEN" -name "*.java" 2>/dev/null)
javac \
    -source 1.8 \
    -target 1.8 \
    -bootclasspath "$ANDROID_JAR" \
    -classpath "$ANDROID_JAR" \
    -d "$OBJ" \
    $JAVA_FILES \
    2>&1

echo "[4/6] Converting to DEX..."
"$DX" --dex --output="$DEX/classes.dex" "$OBJ"

echo "[5/6] Packaging APK..."
# Add DEX to the APK
cd "$DEX"
# Rebuild APK: copy the linked APK and add the dex file
cp "$APK_UNSIGNED" "$APK_UNSIGNED.tmp"
# Use zip to add classes.dex
zip -j "$APK_UNSIGNED.tmp" "$DEX/classes.dex"
mv "$APK_UNSIGNED.tmp" "$APK_UNSIGNED"

echo "[6/6] Aligning and signing..."
"$ZIPALIGN" -f 4 "$APK_UNSIGNED" "$APK_ALIGNED"

# Generate a debug keystore if needed
KEYSTORE="$BUILD_DIR/debug.keystore"
if [ ! -f "$KEYSTORE" ]; then
    keytool -genkeypair \
        -keystore "$KEYSTORE" \
        -storepass android \
        -keypass android \
        -alias androiddebugkey \
        -keyalg RSA \
        -keysize 2048 \
        -validity 10000 \
        -dname "CN=Android Debug,O=Android,C=US" \
        2>&1
fi

"$APKSIGNER" sign \
    --ks "$KEYSTORE" \
    --ks-pass pass:android \
    --key-pass pass:android \
    --ks-key-alias androiddebugkey \
    --out "$APK_FINAL" \
    "$APK_ALIGNED"

echo ""
echo "=== BUILD SUCCESS ==="
echo "APK: $APK_FINAL"
SIZE=$(du -h "$APK_FINAL" | cut -f1)
echo "Size: $SIZE"
echo ""
