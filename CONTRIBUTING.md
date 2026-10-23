# Katkı rehberi

Kurulum için [README](README.md), cihaz testleri ve imzalama için [geliştirme rehberi](docs/DEVELOPMENT.md) ile başla.

## Değişiklik hazırlama

1. `main` üzerinden amaca uygun bir dal aç.
2. Değişikliği tek bir sorun veya özellik çevresinde tut. Kotlin kodunda mevcut Compose/MVVM yapısını ve Kotlin stilini izle.
3. Davranış değiştiğinde ilgili testleri güncelle. Room veya Supabase şema değişikliklerini migration dosyasıyla birlikte yap.
4. Kullanıcıya görünen davranış ve kurulum değişikliklerini ilgili belgelere yansıt.
5. Pull request'te çözülen sorunu, yeni davranışı ve çalıştırılan kontrolleri açıkla. Arayüz değişiklikleri için kişisel veri içermeyen görüntüler ekle.

## Doğrulama

Android değişiklikleri için:

```powershell
.\scripts\build-windows.ps1 -Tasks ':app:assembleDebug',':app:testDebugUnitTest',':app:lintDebug',':app:verifyPrivacyManifests'
```

macOS/Linux'ta aynı görevler `./gradlew` ile çalışır. Room, Compose, widget veya servis yaşam döngüsü değişikliklerinde ilgili cihaz testlerini de çalıştır; ayrıntılar [geliştirme rehberindedir](docs/DEVELOPMENT.md).

Backend değişiklikleri için:

```bash
cd backend
npm ci --ignore-scripts
npm test
```

## Veri ve kaynak kontrolü

Anahtarlar, tokenlar, `.env`, `.signing/`, `.local/`, `local.properties`, kişisel veri dökümleri ve derleme çıktıları repoya eklenmez. Örneklerde yer tutucular veya sentetik test verileri kullan. Hata raporlarına hesap bilgileri ve gizli anahtarlar koyma.

Tarihsel fiyatlar ve kayıtlar yeni fiyatla yeniden hesaplanmaz. Eksik günler sıfır tüketim sayılmaz. Erişilebilirlik olaylarının içeriği kaydedilmez veya sunucuya gönderilmez. Bu davranışları etkileyen değişikliklerde [mimari belgesini](docs/ARCHITECTURE.md) de güncelle.

Üçüncü taraf görsel veya yazı tipi eklerken kaynak ve lisans bilgisini [bildirimlere](THIRD-PARTY-NOTICES.md) ekle.
