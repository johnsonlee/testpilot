#!/bin/bash
#
# Setup script for layoutlib integration tests.
# Downloads the required data files (fonts, native libs, icu).
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
DATA_DIR="$PROJECT_DIR/data"

# Layoutlib version to download
LAYOUTLIB_VERSION="16.1.1"

echo "Setting up layoutlib data for TestPilot..."
echo "Project dir: $PROJECT_DIR"
echo "Data dir: $DATA_DIR"

# Create data directory
mkdir -p "$DATA_DIR"

# Detect OS and architecture
OS=$(uname -s | tr '[:upper:]' '[:lower:]')
ARCH=$(uname -m)

case "$OS" in
    darwin)
        if [[ "$ARCH" == "arm64" ]]; then
            NATIVE_DIR="mac-arm/lib64"
        else
            NATIVE_DIR="mac/lib64"
        fi
        ;;
    linux)
        NATIVE_DIR="linux/lib64"
        ;;
    *)
        echo "Unsupported OS: $OS"
        exit 1
        ;;
esac

echo "Detected platform: $OS/$ARCH -> $NATIVE_DIR"

# Download layoutlib-native data from Maven
MAVEN_BASE="https://repo1.maven.org/maven2/com/android/tools/layoutlib"

download_and_extract() {
    local artifact=$1
    local target_dir=$2

    echo "Downloading $artifact..."

    local url="$MAVEN_BASE/$artifact/$LAYOUTLIB_VERSION/$artifact-$LAYOUTLIB_VERSION.jar"
    local temp_file=$(mktemp)

    if curl -fsSL "$url" -o "$temp_file"; then
        echo "Extracting to $target_dir..."
        mkdir -p "$target_dir"
        unzip -qo "$temp_file" -d "$target_dir" 2>/dev/null || true
        rm "$temp_file"
        echo "Done: $artifact"
    else
        echo "Failed to download: $url"
        rm -f "$temp_file"
        return 1
    fi
}

# Download native libraries
echo ""
echo "=== Downloading native libraries ==="
if [[ "$OS" == "darwin" && "$ARCH" == "arm64" ]]; then
    download_and_extract "layoutlib-native-macarm" "$DATA_DIR"
elif [[ "$OS" == "darwin" ]]; then
    download_and_extract "layoutlib-native-macosx" "$DATA_DIR"
else
    download_and_extract "layoutlib-native-linux" "$DATA_DIR"
fi

# Download fonts
echo ""
echo "=== Downloading fonts ==="
download_and_extract "layoutlib-runtime" "$DATA_DIR"

# Check for Android SDK
echo ""
echo "=== Checking Android SDK ==="
if [ -n "$ANDROID_SDK_ROOT" ]; then
    ANDROID_HOME="$ANDROID_SDK_ROOT"
elif [ -n "$ANDROID_HOME" ]; then
    : # already set
elif [ -d "$HOME/Library/Android/sdk" ]; then
    ANDROID_HOME="$HOME/Library/Android/sdk"
elif [ -d "$HOME/Android/Sdk" ]; then
    ANDROID_HOME="$HOME/Android/Sdk"
else
    echo "Warning: Android SDK not found!"
    echo "Please install Android SDK and set ANDROID_HOME or ANDROID_SDK_ROOT"
fi

if [ -n "$ANDROID_HOME" ]; then
    echo "Android SDK: $ANDROID_HOME"

    # Check for platform-31
    if [ -d "$ANDROID_HOME/platforms/android-31" ]; then
        echo "Platform 31: Found"
    else
        echo "Platform 31: NOT FOUND"
        echo "Install with: sdkmanager --install 'platforms;android-31'"
    fi
fi

echo ""
echo "=== Setup complete ==="
echo ""
echo "Data directory contents:"
ls -la "$DATA_DIR" 2>/dev/null || echo "(empty)"

echo ""
echo "To run integration tests:"
echo "  ./gradlew :renderer:test --tests '*IntegrationTest'"
