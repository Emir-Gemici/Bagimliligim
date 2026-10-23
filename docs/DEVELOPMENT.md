# Geliştirme rehberi

Temel kurulum komutları [README](../README.md) dosyasındadır. Uygulama paketi `com.bagimliligim.app`, minimum Android sürümü API 26, hedef API 36'dır.

## Yerel ve sosyal derlemeler

Backend değişkenleri verilmezse temel takip özellikleri çevrimdışı çalışır. Çevrem ekranı hizmetin henüz yapılandırılmadığını gösterir. Fiyatları doğrudan kaynaklardan yenilemek sosyal hizmet yapılandırmasından bağımsızdır.

Sosyal derleme için üç ortam değişkeni gerekir:

- `BAGIMLILIGIM_BACKEND_URL`: HTTPS Supabase proje adresi.
- `BAGIMLILIGIM_PUBLIC_KEY`: yalnızca publishable veya anon istemci anahtarı.
- `BAGIMLILIGIM_GOOGLE_WEB_CLIENT_ID`: herkese açık Google Web OAuth istemci kimliği.

[Sunucu kurulumunu](../backend/README.md) ve [Google giriş adımlarını](../backend/GOOGLE-SIGN-IN.md) tamamla. `service_role` ve OAuth client secret yalnızca sunucu tarafında saklanır.

Windows'ta bağlı release yardımcısı için [örnek JSON](backend.public.example.json) dosyasını, proje kökünde oluşturacağın `.local/backend.public.json` yoluna kopyala ve yer tutucuları kendi açık istemci değerlerinle değiştir. Betik bu alanları doğrular; örnek dosya doğrudan çalıştırılacak bir canlı yapılandırma değildir. `.local/` Git dışında kalır.

## Cihaz testleri ve QA

Çalışan bir emülatör veya ADB ile bağlı cihaz için:

```powershell
.\scripts\build-windows.ps1 -Tasks ':app:connectedDebugAndroidTest',':app:assembleQa'
```

macOS/Linux'ta aynı görevleri `./gradlew` ile çalıştır. Testler Room migration, fiyat geçmişi, Compose akışları, widget, Google onayı ve servis yaşam döngüsünü kapsar. Dış YouTube kabul testi ayrıca etkinleştirilir; [yayın belgesindeki](../V2-RELEASE.md) cihaz yönergelerini izle.

QA APK, `app/build/outputs/apk/qa/app-qa.apk` yolunda oluşur. Release gibi R8 ile küçültülür, debug anahtarıyla imzalanır ve `.qa` paket eki kullanır. Ayrı yerel veriye ve erişilebilirlik ayarına sahiptir. QA'da Google girişi için bu pakete özel OAuth istemcisi gerekir.

## İmzalı dağıtım

Yayımlanmış bir uygulamayı güncellerken mevcut yükleme anahtarı korunmalıdır. `scripts/build-release-windows.ps1`, `.signing/bagimliligim-upload.p12` ve `password.dpapi.xml` dosyalarını kullanır. İki dosya da yoksa yeni anahtar oluşturabildiğinden, mevcut uygulama için önce doğru imza dosyalarını geri yükle.

```powershell
.\scripts\build-release-windows.ps1
# Sosyal hizmet yapılandırmasıyla:
.\scripts\build-connected-release-windows.ps1
```

DPAPI dosyası Windows kullanıcısına ve makineye bağlıdır; tek başına taşınabilir anahtar yedeği değildir. Anahtarları, şifre dosyalarını ve sertifika arşivlerini Git'e ekleme.

Başka bir ortamda Gradle, aşağıdaki dört ortam değişkeniyle imzalama yapar:

- `SWIPEMETER_KEYSTORE`
- `SWIPEMETER_STORE_PASSWORD`
- `SWIPEMETER_KEY_ALIAS`
- `SWIPEMETER_KEY_PASSWORD`

Bu adlar mevcut imzalama yapılandırmasından gelir. Uygulama adı ve paketi Bağımlılığım'dır. Dört değer birlikte verilmelidir. CI'da bunlar yokken oluşturulan release bundle imzasızdır ve doğrudan Play'e yüklenemez.

APK/AAB çıktıları `app/build/outputs/` altındadır. Google Play dağıtımı için [test rehberini](../PLAY-TEST.md) ve [yayın kapılarını](../V2-RELEASE.md) takip et.

## Otomatik kontroller

Android CI, JDK 17 ve SDK 36.1 üzerinde derleme/test/lint görevlerini çalıştırır. Backend CI, Node.js 22 kullanır. Android raporları `app/build/reports/` altında üretilir; CI bunları artifact olarak saklar.

`verifyPrivacyManifests`, tüm varyantların birleştirilmiş manifestlerini izin listesine göre denetler. `check` görevi de bu denetimi çalıştırır. Yeni izin veya veri paylaşım davranışı ekleyen değişikliklerde açıklamaları ve kabul testlerini birlikte güncelle.
