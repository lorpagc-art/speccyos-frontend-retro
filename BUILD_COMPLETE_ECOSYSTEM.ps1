# SPECCY OS AND ENGINE FORGE SCRIPT v1.2.0
# The Final JDK Wall Breaker

$RELEASE_DIR = "RELEASE_SPECCY_V1_ULTRA"
$RA_PATH = "Sample/RetroArch-1.22.2 (1)/RetroArch-1.22.2/pkg/android/phoenix"

if (!(Test-Path $RELEASE_DIR)) {
    New-Item -ItemType Directory -Path $RELEASE_DIR | Out-Null
}

Write-Host "=================================================="
Write-Host "   SPECCY ECOSYSTEM FORGE STARTING (v1.2.0)"
Write-Host "=================================================="

# 1. Build Speccy OS First (Uses default Java 21)
Write-Host "[STEP 1/2] Building Speccy OS (SystemOS Flavor)..."
./gradlew :app:assembleSystemosRelease

if ($LASTEXITCODE -eq 0) {
    $OS_APK = "app/build/outputs/apk/systemos/release/app-systemos-release.apk"
    Move-Item -Path $OS_APK -Destination "$RELEASE_DIR/SpeccyOS_V1.1.3_SystemOS.apk" -Force
    Write-Host "SUCCESS: Speccy OS is ready." -ForegroundColor Green
} else {
    Write-Host "ERROR: Speccy OS build failed." -ForegroundColor Red
    exit 1
}

# 2. Build Speccy Engine
Write-Host "[STEP 2/2] Building Speccy Engine..."
if (Test-Path $RA_PATH) {
    Push-Location $RA_PATH

    # Isolate project using KTS so it overrides the parent .kts file
    "rootProject.name = `"retroarch-phoenix`"" | Out-File -FilePath "settings.gradle.kts" -Encoding ASCII -Force

    # Stop Gradle daemon to force a clean Java state
    ./gradlew --stop | Out-Null

    # Check for Studio JBR
    $jbrPath = "C:/Program Files/Android/Android Studio/jbr"
    if (Test-Path $jbrPath) {
        Write-Host "Injecting JBR Path into gradle.properties..."
        "org.gradle.java.home=$jbrPath" | Out-File -FilePath "gradle.properties" -Encoding ASCII -Append -Force
    }

    ./gradlew clean assembleSpeccyEngineRelease

    if ($LASTEXITCODE -eq 0) {
        Remove-Item "settings.gradle.kts" -Force -ErrorAction SilentlyContinue

        # Clean the injected property
        if (Test-Path "gradle.properties") {
            $props = Get-Content "gradle.properties"
            $props | Where-Object { $_ -notmatch "org.gradle.java.home" } | Set-Content "gradle.properties"
        }

        $APK_OUT = "build/outputs/apk/speccyEngine/release/phoenix-speccyEngine-release.apk"
        Pop-Location
        Move-Item -Path "$RA_PATH/$APK_OUT" -Destination "$RELEASE_DIR/SpeccyEngine_Release.apk" -Force
        Write-Host "SUCCESS: Speccy Engine is ready." -ForegroundColor Green
    } else {
        Remove-Item "settings.gradle.kts" -Force -ErrorAction SilentlyContinue
        if (Test-Path "gradle.properties") {
            $props = Get-Content "gradle.properties"
            $props | Where-Object { $_ -notmatch "org.gradle.java.home" } | Set-Content "gradle.properties"
        }
        Pop-Location
        Write-Host "ERROR: Speccy Engine build failed." -ForegroundColor Red
        Write-Host "Please open Android Studio, go to Settings -> Build -> Build Tools -> Gradle."
        Write-Host "Set the 'Gradle JDK' to JDK 17 (or jbr) specifically."
        exit 1
    }
}

Write-Host "=================================================="
Write-Host "   FORGE COMPLETE! CHECK THE RELEASE FOLDER"
Write-Host "=================================================="
