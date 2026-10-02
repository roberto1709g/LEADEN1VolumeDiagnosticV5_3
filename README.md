# LEADEN1 Volume Diagnostic V5.3

V5.3 conserva la detección de volumen de V5.2 y corrige la comprobación del Notification Listener.
La aplicación consulta directamente `Settings.Secure/enabled_notification_listeners` y usa el ComponentName del listener para `MediaSessionManager`.

Prueba: instalar, activar `LEADEN1 Media Monitor V5.3` en Acceso a notificaciones, volver a la app, pulsar ACTUALIZAR SESIONES, reproducir música y probar botón central, volumen + y volumen -.

No modifica la PWA LEADEN1, VPS ni Nginx.
