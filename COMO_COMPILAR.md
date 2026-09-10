# Cómo compilar SpeccyOS 1.2.0

Guía práctica para esta carpeta concreta. Escrita el 9 de septiembre de 2026,
verificada contra los ficheros del proyecto, no de memoria.

**Carpeta del proyecto (la buena):**

```
C:\Users\lolac\Desktop\copia falta scraper y retroach\SpeccyOSE5UltraV021b
```

Ojo: hay 6 copias de SpeccyOS en el disco y el nombre de esta engaña. Es la
1.2.0 (versionCode 48) y es la única que compila. La de
`Desktop\SpeccyOSE5UltraV021b` es un tronco muerto de junio que **no compila**.

---

## 1. Antes de lanzar nada, recién reiniciado el PC

Dos cosas, y las dos importan:

1. **No abras Android Studio.** Su Gradle y el de la terminal levantan JVM
   distintas, cada una con hasta 4 GB reservados (`org.gradle.jvmargs=-Xmx4096M`
   en `gradle.properties`). Con dos o tres vivas, el empaquetado de release se
   queda sin memoria y **el sistema mata el proceso**. Eso es exactamente lo que
   pasó el día 8: cinco JVM de Gradle vivas de builds de otros proyectos.

2. **Comprueba que Gradle va a usar el JDK correcto:**

   ```
   echo %JAVA_HOME%
   ```

   Tiene que responder `C:\Program Files\Android\Android Studio\jbr`.

   No te fíes de `java -version`: en este PC el `java` del PATH es un Java 8 de
   2026 que no sirve para Gradle 9.3.1. Lo que manda es `JAVA_HOME`, y ese está
   bien puesto.

---

## 2. El comando normal

Abre una terminal (cmd o PowerShell) y:

```
cd "C:\Users\lolac\Desktop\copia falta scraper y retroach\SpeccyOSE5UltraV021b"
gradlew.bat assemblePlaystoreRelease --offline
```

Cuando termine bien verás `BUILD SUCCESSFUL` y el APK estará en:

```
app\build\outputs\apk\playstore\release\app-playstore-release.apk
```

**Por qué `--offline`:** en este equipo Gradle no puede hablar por TLS con los
repositorios (por eso `gradle.properties` tiene
`systemProp.javax.net.ssl.trustStoreType=Windows-ROOT`, que es el intento de
arreglarlo). Con `--offline` usa las dependencias ya descargadas y no lo
intenta. Es también la razón de que **no se pueda añadir ninguna dependencia
nueva** en esta máquina: eso sí necesita red.

---

## 3. Si se queda sin memoria

Síntoma: el proceso se corta sin decir `BUILD FAILED`, o la terminal informa de
que lo han matado. **No es un fallo del código.** El que se come la memoria es
R8, la fase de minificación del release.

Por orden, de menos a más drástico:

```
gradlew.bat --stop
```

Mata todos los daemons de Gradle vivos. Después relanza el comando normal. Con
esto solo suele bastar.

Si vuelve a pasar, en serie y sin daemon:

```
gradlew.bat assemblePlaystoreRelease --offline --no-daemon --max-workers=1
```

Y si aun así no cabe, el proyecto tiene una salida propia:

```
gradlew.bat assemblePlaystoreRelease --offline -PnoMinify
```

`-PnoMinify` compila el release **sin R8**, que es justo lo que no cabe en
memoria. Sirve para probar la app en la consola y además el logcat sale con los
nombres de clase reales, sin necesitar el `mapping.txt`.

> **Un APK hecho con `-PnoMinify` NO se sube a Play.** Va sin ofuscar y sin
> reducir recursos: pesa más y expone el código. Es para probar, nada más.

---

## 4. Qué tarea lanzar según lo que quieras

| Quiero… | Comando |
|---|---|
| Comprobar que un cambio compila (rápido, ~1 min) | `gradlew.bat compilePlaystoreReleaseKotlin --offline` |
| APK para instalar en la consola | `gradlew.bat assemblePlaystoreRelease --offline` |
| **AAB para subir a Google Play** | `gradlew.bat bundlePlaystoreRelease --offline` |
| APK de sideload (web / GitHub) | `gradlew.bat assembleWebsiteRelease --offline` |
| APK de custom ROM / launcher | `gradlew.bat assembleSystemosRelease --offline` |
| Versión de depuración | `gradlew.bat assemblePlaystoreDebug --offline` |
| Tests unitarios de los tres sabores | `gradlew.bat testDebugUnitTest --offline` |

Los tres sabores no son cosmética:

- **playstore** — OCR *unbundled* (el modelo lo sirve Google Play Services), sin
  el servicio de accesibilidad, que la política de Play no admite.
- **website** y **systemos** — OCR *bundled*, porque son sideload y ROM propia,
  donde no se puede dar por hecho que haya Play Services. `systemos` es el único
  que declara `SpeccyAccessibilityService`.

Para instalar el APK en la consola con el cable conectado:

```
adb install -r "app\build\outputs\apk\playstore\release\app-playstore-release.apk"
```

---

## 5. La clave de firma

La firma de release sale de `local.properties`, que **no se versiona** y ya está
configurado en este equipo:

```
speccy.keystore.path=...
speccy.key.alias=...
speccy.keystore.password=...
speccy.key.password=...
```

Es la **clave de subida registrada en Google Play**, la misma que usabas desde
"Generate Signed Bundle / APK" de Android Studio. Si la cambias, Play rechaza la
actualización. No la toques y no borres ese fichero.

Si falta algo, el build de release **falla a propósito** con un mensaje que dice
exactamente qué falta, en vez de generar un APK sin firmar. No se cae nunca a la
clave de debug.

Alternativa si prefieres no tener las contraseñas en disco: exportarlas antes de
lanzar Gradle como `SPECCY_KEYSTORE_PASSWORD` y `SPECCY_KEY_PASSWORD`.

---

## 6. Errores que ya han salido, y qué significan

**"Killed" / la terminal se corta sin `BUILD FAILED`**
Memoria. Ve a la sección 3. No mires el código.

**`e: ... Unresolved reference 'NeonBlue'`**
Falta `app\src\main\java\com\generacionarcade\speccyos\theme\Theme.kt`. Ahí viven
los colores que usa medio proyecto, aunque el tema `SpeccyOSE5UltraTheme` de ese
mismo fichero no lo use nadie. Recupéralo de `_backup_auditoria\`.

**`@Composable invocations can only happen from the context of a @Composable function`**
Hay un `remember` dentro del contenido de un `LazyColumn`/`LazyVerticalGrid`. Ese
bloque no es composable: el `remember` va **fuera**, antes de la lista.

**"cannot serialize Gradle script object references"**
La caché de configuración (`org.gradle.configuration-cache=true`) chocando con
una lambda declarada en el `.kts`. Ya está resuelto en este proyecto; si vuelve a
aparecer tras tocar `build.gradle.kts`, la pista está comentada al final de ese
fichero.

---

## 7. Ficha técnica

| | |
|---|---|
| Gradle | 9.3.1 (wrapper, no hace falta instalarlo) |
| Android Gradle Plugin | 9.0.1 |
| Kotlin | 2.3.10 |
| compileSdk | 36 (Play lo exige para toda actualización desde 2026) |
| applicationId | `com.generacionarcade.speccyos` |
| Versión actual | versionCode 48 · versionName 1.2.0 |

---

## 8. Recordatorio: esta carpeta no tiene git

No hay historial. Lo único que hay son copias manuales en `_backup_auditoria\`.
Antes de la próxima tanda de cambios conviene un `git init` con un commit
inicial: aquí ya hay trabajo de sobra que perder.

El detalle de lo que se ha cambiado y por qué está en
`REGISTRO_CAMBIOS_AUDITORIA.md`.
