#!/bin/bash

# Script to build iOS distribution XCFrameworks
# This creates production-ready frameworks that iOS developers can integrate into their projects

set -e  # Exit on error

echo "📦 Building iOS Distribution Frameworks..."
echo ""

# Colors for output
GREEN='\033[0;32m'
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# Get the script directory (project root)
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$PROJECT_ROOT"

# Define module paths, Gradle project names, and their framework names
MODULE_PATHS=("consultation" "119602-data-model" "119602-consultation" "1196x2-signum")
GRADLE_PROJECTS=("etsi-1196x2-consultation" "etsi-119602-data-model" "etsi-119602-consultation" "etsi-1196x2-signum")
FRAMEWORK_NAMES=("etsi_1196x2_consultation" "etsi_119602_data_model" "etsi_119602_consultation" "etsi_1196x2_signum")

# Define iOS targets (capitalized for Gradle task names)
GRADLE_TARGETS=("IosArm64" "IosX64" "IosSimulatorArm64")
BUILD_TARGETS=("iosArm64" "iosX64" "iosSimulatorArm64")

# Destination directory for distribution frameworks
DISTRIBUTION_DIR="$PROJECT_ROOT/ios-distribution"
FRAMEWORKS_DIR="$DISTRIBUTION_DIR/Frameworks"

# Create distribution directory structure
echo -e "${BLUE}Step 0: Preparing distribution directory...${NC}"
mkdir -p "$FRAMEWORKS_DIR"
echo "Distribution directory: $DISTRIBUTION_DIR"
echo ""

echo -e "${BLUE}Step 1: Cleaning previous builds...${NC}"
./gradlew clean
echo ""

echo -e "${BLUE}Step 2: Building XCFrameworks for all targets...${NC}"
echo ""

# Build frameworks for each target
for i in "${!GRADLE_TARGETS[@]}"; do
    gradle_target="${GRADLE_TARGETS[$i]}"
    build_target="${BUILD_TARGETS[$i]}"

    echo -e "${YELLOW}Building ${build_target} frameworks...${NC}"
    ./gradlew \
        :etsi-1196x2-consultation:linkDebugFramework${gradle_target} \
        :etsi-119602-data-model:linkDebugFramework${gradle_target} \
        :etsi-119602-consultation:linkDebugFramework${gradle_target} \
        :etsi-1196x2-signum:linkDebugFramework${gradle_target}
    echo ""
done

echo -e "${BLUE}Step 3: Creating XCFrameworks...${NC}"
echo ""

# Create XCFramework for each module
for i in "${!MODULE_PATHS[@]}"; do
    module_path="${MODULE_PATHS[$i]}"
    framework_name="${FRAMEWORK_NAMES[$i]}"
    xcframework_path="$FRAMEWORKS_DIR/${framework_name}.xcframework"

    echo -e "${YELLOW}Creating ${framework_name}.xcframework...${NC}"

    # Remove old XCFramework if exists
    rm -rf "$xcframework_path"

    # Paths to individual frameworks
    device_fw="$PROJECT_ROOT/${module_path}/build/bin/iosArm64/debugFramework/${framework_name}.framework"
    sim_x64_fw="$PROJECT_ROOT/${module_path}/build/bin/iosX64/debugFramework/${framework_name}.framework"
    sim_arm64_fw="$PROJECT_ROOT/${module_path}/build/bin/iosSimulatorArm64/debugFramework/${framework_name}.framework"

    # Verify frameworks exist
    if [ ! -d "$device_fw" ] || [ ! -d "$sim_x64_fw" ] || [ ! -d "$sim_arm64_fw" ]; then
        echo -e "${RED}✗ Error: Framework files not found for ${framework_name}${NC}"
        echo "  Expected locations:"
        echo "    - $device_fw"
        echo "    - $sim_x64_fw"
        echo "    - $sim_arm64_fw"
        exit 1
    fi

    # Create a temporary directory for the fat simulator framework
    temp_sim_fw="$PROJECT_ROOT/${module_path}/build/bin/simulator-fat/${framework_name}.framework"
    rm -rf "$temp_sim_fw"
    mkdir -p "$(dirname "$temp_sim_fw")"

    # Copy one of the simulator frameworks as base
    cp -R "$sim_arm64_fw" "$temp_sim_fw"

    # Use lipo to create fat binary combining both simulator architectures
    lipo -create \
        "$sim_x64_fw/${framework_name}" \
        "$sim_arm64_fw/${framework_name}" \
        -output "$temp_sim_fw/${framework_name}"

    # Build XCFramework from device and fat simulator frameworks
    xcodebuild -create-xcframework \
        -framework "$device_fw" \
        -framework "$temp_sim_fw" \
        -output "$xcframework_path"

    # Clean up temp framework
    rm -rf "$temp_sim_fw"

    echo -e "${GREEN}✓ Created ${framework_name}.xcframework${NC}"
    echo ""
done

echo -e "${GREEN}✅ iOS Distribution Build Complete!${NC}"
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo -e "${BLUE}📦 Distribution Package Location:${NC}"
echo "   $DISTRIBUTION_DIR"
echo ""
echo -e "${BLUE}📚 XCFrameworks created:${NC}"
for framework_name in "${FRAMEWORK_NAMES[@]}"; do
    echo "   ✓ ${framework_name}.xcframework"
done
echo ""

# Show framework sizes
echo -e "${BLUE}📊 Framework sizes:${NC}"
for framework_name in "${FRAMEWORK_NAMES[@]}"; do
    size=$(du -sh "$FRAMEWORKS_DIR/${framework_name}.xcframework" | cut -f1)
    echo "   • ${framework_name}.xcframework: ${size}"
done
echo ""
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo -e "${GREEN}📖 Next Steps for iOS Developers:${NC}"
echo "   1. Navigate to: $DISTRIBUTION_DIR"
echo "   2. Read the README.md for integration instructions"
echo "   3. Copy the Frameworks folder to your Xcode project"
echo "   4. Add the XCFrameworks to your target"
echo ""
echo -e "${YELLOW}⚠️  Note: These are DEBUG builds. For production, use Release configuration.${NC}"
echo ""
