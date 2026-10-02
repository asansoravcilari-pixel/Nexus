# NEXUS Tabs

Minimal Android WebView shell for a fixed set of user-defined pages.

## Davranış
- Adres çubuğu ve tarayıcı başlığı yok.
- Üstte yalnızca ince sekmeler ve `+` düğmesi var.
- `+` ile sayfa adı, URL ve isteğe bağlı kullanıcı adı/şifre girilir.
- Sekmeye uzun basınca sayfa silinir.
- Her sekme kendi WebView oturumunu korur.
- Kayıtlı kimlik bilgileri yalnızca eklenen alan adında otomatik doldurulur.
- Form otomatik gönderilmez.
- Başka alan adları Android'in normal tarayıcısında açılır.
- HTTP Basic Auth desteklenir.
- Kimlik bilgileri Android Keystore ile AES-GCM şifrelenerek cihazda tutulur.
- HTTP adreslerinde kimlik bilgisi kaydına izin verilmez.

## Derleme
JDK 17, Android SDK 37 ve Gradle 9.6.0 ile:
```
gradle :app:assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`
