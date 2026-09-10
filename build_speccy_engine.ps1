# 🚀 SPECCY ENGINE - RELEASE FORGE SCRIPT
# Generacion Arcade - Speccy OS E5 Ultra

$RA_PATH = "Sample/RetroArch-1.22.2 (1)/RetroArch-1.22.2/pkg/android/phoenix"
$DIST_DIR = "Release_SpeccyEngine"

Write-Host "--------------------------------------------------" -ForegroundColor Cyan
Write-Host "⚡ INICIANDO FORJA DEL SPECCY ENGINE (RETROARCH CUSTOM) ⚡" -ForegroundColor Cyan
Write-Host "--------------------------------------------------" -ForegroundColor Cyan

# 1. Crear directorio de distribución
if (!(Test-Path $DIST_DIR)) {
    New-Item -ItemType Directory -Path $DIST_DIR
}

# 2. Navegar al proyecto de RetroArch
Push-Location $RA_PATH

Write-Host "[1/4] Limpiando buffers de compilación..." -ForegroundColor Yellow
./gradlew clean

Write-Host "[2/4] Inyectando Identidad Visual (Icono GA)..." -ForegroundColor Yellow
./gradlew copySpeccyAssets

Write-Host "[3/4] Compilando Speccy Engine Release (arm64-v8a)..." -ForegroundColor Yellow
./gradlew assembleSpeccyEngineRelease

# 3. Mover el resultado
$APK_SOURCE = "build/outputs/apk/speccyEngine/release/phoenix-speccyEngine-release.apk"

if (Test-Path $APK_SOURCE) {
    Pop-Location
    Move-Item -Path "$RA_PATH/$APK_SOURCE" -Destination "$DIST_DIR/SpeccyEngine_V1.22.2_Release.apk" -Force
    Write-Host "--------------------------------------------------" -ForegroundColor Green
    Write-Host "✅ ¡FORJA COMPLETADA EXITOSAMENTE!" -ForegroundColor Green
    Write-Host "📦 APK LISTO EN: $DIST_DIR/SpeccyEngine_V1.22.2_Release.apk" -ForegroundColor Green
    Write-Host "--------------------------------------------------" -ForegroundColor Green
} else {
    Pop-Location
    Write-Host "❌ ERROR: No se pudo generar el APK. Revisa los logs de Gradle." -ForegroundColor Red
}
