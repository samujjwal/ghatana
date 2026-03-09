#!/bin/bash

# Phase 3 Week 1 - Cleanup Script
# This script identifies files that should be deleted after moving to minimal bridge pattern
# RUN: chmod +x cleanup-redundant-code.sh && ./cleanup-redundant-code.sh

set -e

ANDROID_PATH="products/dcmaar/apps/guardian/apps/agent-react-native/android/app/src/main/java/com/yappc/guardian/agent"

echo "═══════════════════════════════════════════════════════════════"
echo "Guardian Phase 3 Week 1 - Redundant Code Cleanup"
echo "═══════════════════════════════════════════════════════════════"
echo ""

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# Track deletions
TOTAL_FILES=0
TOTAL_LINES=0

# Function to check and report file
check_file() {
    local file=$1
    local description=$2
    
    if [ -f "$file" ]; then
        local lines=$(wc -l < "$file")
        echo -e "${RED}❌ DELETE${NC}: $description"
        echo "   File: $file"
        echo "   Lines: $lines"
        echo ""
        
        TOTAL_FILES=$((TOTAL_FILES + 1))
        TOTAL_LINES=$((TOTAL_LINES + lines))
    fi
}

# 1. Database Layer Files
echo -e "${YELLOW}1. DATABASE LAYER (Room ORM)${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
check_file "$ANDROID_PATH/persistence/AppActivityDatabase.kt" "App Activity Database"
check_file "$ANDROID_PATH/persistence/daos/AppActivityDao.kt" "App Activity DAO"
check_file "$ANDROID_PATH/persistence/daos/UsageStatsDao.kt" "Usage Stats DAO"
check_file "$ANDROID_PATH/persistence/daos/ScreenTimeDao.kt" "Screen Time DAO"
check_file "$ANDROID_PATH/persistence/daos/AppInfoDao.kt" "App Info DAO"
check_file "$ANDROID_PATH/persistence/daos/PermissionDao.kt" "Permission DAO"
check_file "$ANDROID_PATH/persistence/entities/AppActivityEntity.kt" "App Activity Entity"
check_file "$ANDROID_PATH/persistence/entities/UsageStatsEntity.kt" "Usage Stats Entity"
check_file "$ANDROID_PATH/persistence/entities/ScreenTimeEntity.kt" "Screen Time Entity"
check_file "$ANDROID_PATH/persistence/entities/AppInfoEntity.kt" "App Info Entity"
check_file "$ANDROID_PATH/persistence/entities/PermissionEntity.kt" "Permission Entity"
echo ""

# 2. API Client Layer
echo -e "${YELLOW}2. API CLIENT LAYER (Retrofit)${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
check_file "$ANDROID_PATH/api/GuardianApiService.kt" "Guardian API Service"
check_file "$ANDROID_PATH/api/models/ApiModels.kt" "API Models"
check_file "$ANDROID_PATH/api/models/UsageStatsModel.kt" "Usage Stats Model"
check_file "$ANDROID_PATH/api/models/AppInfoModel.kt" "App Info Model"
check_file "$ANDROID_PATH/api/models/PolicyModel.kt" "Policy Model"
echo ""

# 3. Auth Layer
echo -e "${YELLOW}3. AUTH LAYER (JWT Token Management)${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
check_file "$ANDROID_PATH/auth/JwtTokenManager.kt" "JWT Token Manager"
echo ""

# 4. Repository Layer
echo -e "${YELLOW}4. REPOSITORY LAYER (Data Access Abstraction)${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
check_file "$ANDROID_PATH/repository/UsageRepository.kt" "Usage Repository"
echo ""

# 5. Hilt DI Modules
echo -e "${YELLOW}5. HILT DI CONFIGURATION${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
check_file "$ANDROID_PATH/di/DatabaseModule.kt" "Database Hilt Module"
check_file "$ANDROID_PATH/di/NetworkModule.kt" "Network Hilt Module"
check_file "$ANDROID_PATH/di/RepositoryModule.kt" "Repository Hilt Module"
echo ""

# 6. Services
echo -e "${YELLOW}6. SERVICES (Old Comprehensive Implementation)${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
check_file "$ANDROID_PATH/services/AppTrackingAccessibilityService.kt" "App Tracking Accessibility Service (OLD)"
check_file "$ANDROID_PATH/services/AppUsageStatsManager.kt" "App Usage Stats Manager"
echo ""

# 7. Background Workers
echo -e "${YELLOW}7. BACKGROUND WORKERS (Old WorkManager)${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
check_file "$ANDROID_PATH/workers/SyncWorker.kt" "Old Sync Worker"
echo ""

# 8. Device Admin Receiver (Old)
echo -e "${YELLOW}8. DEVICE ADMIN RECEIVER (Old Implementation)${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
check_file "$ANDROID_PATH/admin/GuardianDeviceAdminReceiver.kt" "Old Device Admin Receiver"
echo ""

# 9. Boot Receiver
echo -e "${YELLOW}9. BOOT RECEIVER (No Longer Needed)${NC}"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
check_file "$ANDROID_PATH/receivers/BootCompletedReceiver.kt" "Boot Completed Receiver"
echo ""

# Summary
echo "═══════════════════════════════════════════════════════════════"
echo -e "${YELLOW}SUMMARY${NC}"
echo "═══════════════════════════════════════════════════════════════"
echo -e "Total files to delete: ${RED}$TOTAL_FILES${NC}"
echo -e "Total lines to remove: ${RED}$TOTAL_LINES${NC}"
echo ""
echo "These files should be deleted manually or via:"
echo ""
echo -e "${YELLOW}Option 1: Manual Deletion${NC}"
for dir in persistence api auth repository di services workers admin receivers; do
    if [ -d "$ANDROID_PATH/$dir" ]; then
        echo "rm -rf $ANDROID_PATH/$dir"
    fi
done
echo ""
echo -e "${YELLOW}Option 2: Via Git${NC}"
echo "git rm -r $ANDROID_PATH/{persistence,api,auth,repository,di,services,workers,admin,receivers}"
echo ""

# Check build.gradle for unused dependencies
echo "═══════════════════════════════════════════════════════════════"
echo -e "${YELLOW}GRADLE CLEANUP RECOMMENDATIONS${NC}"
echo "═══════════════════════════════════════════════════════════════"
echo "Edit: $ANDROID_PATH/../../../build.gradle"
echo ""
echo "Remove these dependencies:"
echo "  ❌ androidx.room:room-runtime"
echo "  ❌ androidx.room:room-compiler"
echo "  ❌ com.squareup.retrofit2:retrofit"
echo "  ❌ com.squareup.retrofit2:converter-gson"
echo "  ❌ com.squareup.okhttp3:okhttp"
echo "  ❌ com.squareup.okhttp3:logging-interceptor"
echo "  ❌ androidx.datastore:datastore-preferences"
echo "  ❌ androidx.security:security-crypto"
echo "  ❌ androidx.lifecycle:lifecycle-process"
echo ""

# Verification
echo "═══════════════════════════════════════════════════════════════"
echo -e "${YELLOW}AFTER CLEANUP - RUN THESE COMMANDS${NC}"
echo "═══════════════════════════════════════════════════════════════"
echo ""
echo "1. Verify compilation:"
echo "   cd products/dcmaar/apps/guardian/apps/agent-react-native/android"
echo "   ./gradlew clean build"
echo ""
echo "2. Check for unused imports:"
echo "   ./gradlew build --warning-mode all"
echo ""
echo "3. Build APK:"
echo "   ./gradlew assembleDebug"
echo ""
echo "4. View logs:"
echo "   adb logcat | grep Guardian"
echo ""

echo -e "${GREEN}✅ Cleanup analysis complete${NC}"
echo ""
