# UltraLauncher

Launcher (pantalla de inicio) para Android 8.0+ con estilo UltraPC.

- Reloj grande y fecha
- Batería con barra y aviso de carga
- Buscador de apps (sin importar tildes; "Ir" abre el primer resultado)
- Cajón de apps en cuadrícula, se actualiza al instalar/desinstalar
- Usa tu fondo de pantalla; mantener presionado el reloj abre el selector de fondo
- Mantener presionada una app abre su información

## Instalar

1. Descarga `UltraLauncher.apk` desde [Releases](../../releases/latest) en el teléfono y ábrelo
   (permite "instalar apps desconocidas" si lo pide).
2. Pulsa Inicio y elige **UltraLauncher** → "Siempre".

Para volver al launcher anterior: Ajustes → Aplicaciones → Aplicaciones predeterminadas → Pantalla de inicio.

## Compilar

Requiere JDK 17 y Android SDK 34 (`local.properties` con `sdk.dir=...`):

```
gradlew assembleRelease
```

El APK queda en `app/build/outputs/apk/release/app-release.apk`.
