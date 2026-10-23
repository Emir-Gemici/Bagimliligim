# Bağımlılığım 2.0.0 — yayın hazırlığı

Paket `com.bagimliligim.app`, sürüm kodu 3. Mevcut yükleme anahtarı korunur. V1 Room veritabanı açık 1→2 geçişiyle yükseltilir; kaydırma geçmişi silinmez.

Bu çalışma V2 test adayıdır. Arkadaş hizmetinin canlı kurulumu ve S25 FE üzerinde gerçek Shorts doğrulaması tamamlanmadan yayımlanmış/tamamlanmış V2 olarak sunulmamalı.

## Eklenenler

- Sıcak koyu renkler, bağlı kalpler kimliğiyle uyumlu tipografi, daha az tekrar eden kart, beş ana bölüm.
- Instagram adı ana ekranda ve iki widget boyutunda tam yazılır.
- Shorts’ta özel kapsayıcılar, küçük dikey olayların birikimi, mutlak kaydırma konumu ve sayfa seçimi olayları için destek. Yorum/mesaj/yatay hareket filtreleri, 450 ms tekrar engeli ve toplu kayıt korunur.
- Marka/seri araması, özel marka ekleme, tarih/adet/paket fiyatıyla sigara kaydı, geri alma, sıfır tüketimli gün kaydı, dönem ve marka bazlı harcamalar.
- Para hesabında sabit hassasiyet. Her kayıt o günkü paket fiyatını saklar. Yeni fiyat eski kayıtları yeniden hesaplamaz.
- Kaynak ve kontrol tarihli fiyat güncelleme. Gözlemlenen yeni fiyat yalnızca gözlem tarihinden sonra kullanılabilir; geçmiş kayıt için kullanıcı fiyatı gerekir.
- 62 ürünlük karşılaştırma adres havuzu, fiyatı canlı doğrulanan teklifler ve son gösterilenleri tekrar etmeyen seçim. 24 saatten eski ürün fiyatları karşılaştırmada kullanılmaz.
- Arkadaş kodu, karşılıklı kabul, marka filtresi, azalma derecesi, harcama/kaydırma sıralaması, seçilebilir paylaşım ve hesap silme için istemci ve test edilen Supabase SQL.

## Gerçek cihaz kabulü

S25 FE’de YouTube sürümünü kaydet. Shorts’ta 20 yavaş geçiş, 20 yorum kaydırması, normal YouTube ana sayfası ve yatay hareket denemesi yap. İlk indeksli olay başlangıç noktasıdır. Ekran kapatma ve başka uygulamaya geçişte widget güncellenmeli. Otomasyon testleri üçüncü taraf YouTube sürümlerinin tamamını kanıtlamaz.

## Yayın kapıları

1. `backend/GOOGLE-SIGN-IN.md` adımlarıyla Google girişini gerçek cihazda doğrula; iki arkadaş ve üçüncü yetkisiz hesapla uçtan uca testi tamamla. Supabase bağlantısı ve SQL erişim testleri tamamlandı.
2. Periyodik fiyat kontrolünü etkinleştir. 365 günlük sunucu saklama görevi kuruldu. Kaynakta olmayan fiyatı uydurma; eskimiş kaynak durumunu kullanıcıya göster.
3. İnternet/hesap/paylaşım davranışını yansıtan gizlilik, hesap silme sayfası ve Play veri güvenliği beyanlarını güncelle. V1’in “internet izni yok” metni V2 için geçersizdir.
4. S25 FE Shorts ve widget kabulünü tamamla. İmzalı AAB’yi mevcut uygulamanın dahili test kanalındaki 26 kişilik listeye sun.

## Sürüm notu metni

Bağımlılığım 2.0: alışkanlıklarına daha yakından bak.

- Yenilenen arayüz ve tam Instagram adıyla ana ekran bileşenleri.
- YouTube Shorts algılamasında geliştirmeler.
- Marka ve tarihe göre sigara kaydı, geçmiş fiyatı koruyan harcama takibi.
- İsteğe bağlı güncel fiyatlar ve harcamanın başka karşılıklarını gösteren kartlar.
- Arkadaşlarınla seçtiğin özetleri paylaşabileceğin Çevrem bölümü.

Son madde yalnızca canlı arkadaş hizmeti bağlı ve doğrulanmış dağıtımda kullanılmalı.

## Gerçek YouTube doğrulaması — 17 Ekim 2026

Android 16 / API 36.1 emülatöründe yüklü YouTube **20.10.41** ile canlı akış denendi. Servis bildirim gecikmesi 50 ms iken kaydırma olayları görünmez alt katman olayıyla birleşiyor ve sayım sıfır kalıyordu. `notificationTimeout=0` ile olaylar hemen alınır; uygulamanın 450 ms sayım engeli korunur.

Senkron dokunma girdileriyle yapılan ileri/geri denemede YouTube 10 tam sayfa geçişi tamamladı ve uygulama **10** saydı. Ardından yorumlarda 10, normal ana sayfada 5 kaydırma yapıldı; ikisinde de yanlış sayım **0**. Rapor: `app/build/reports/shorts-probe-full.json`. Ekran içeriği veya hesap bilgisi içermez. Gözlemlenen 52 olay ayrıca JVM tekrar testine dönüştürüldü. Bu sonuç, kullanıcının S25 FE'sindeki farklı YouTube sürümünün doğrulandığı anlamına gelmez.

Bu dış uygulama testi normal çevrimdışı CI'dan ayrı çalışır:

```powershell
.\scripts\build-windows.ps1 -Tasks ':app:connectedDebugAndroidTest','-Pandroid.testInstrumentationRunnerArguments.class=com.bagimliligim.app.service.YouTubeDeviceAcceptanceTest','-Pandroid.testInstrumentationRunnerArguments.externalFeedProbe=true'
```

Test yalnızca test emülatöründe kullanılmalı; YouTube'un açık ve çevrimiçi olması gerekir. Başlamadan önceki erişilebilirlik ayarını ve uygulama onayını sonunda geri yükler.

## İmzalı yerel test adayı — 17 Ekim 2026

`dist/v2-test/Bagimliligim-2.0.0-test.apk` ve aynı dizindeki AAB, sürüm kodu 3 ile üretildi. SHA-256 dosyası yanlarındadır. Mevcut yükleme sertifikası doğrulandı; anahtar veya şifre değiştirilmedi. Bu adayın Supabase bağlantısı boş olduğundan Çevrem hizmeti açık değildir. Canlı bağlantı tamamlandıktan sonra paket yeniden derlenmeli; bu dosyalar Play'e yüklenmedi.

36 JVM testi ve tam Android paketindeki 11 test başarılı; yalnızca açıkça etkinleştirilmesi gereken dış YouTube testi olağan koşumda atlandı. YouTube kabul testi ayrıca başarılı çalıştı. Son görsel düzenlemelerden sonra iki Compose ekran testi, debug lint, izin kontrolleri, R8 küçültmeli release APK/AAB derlemesi de başarılı. İmzalı APK Android 16 / API 36.1 emülatörüne kuruldu ve soğuk açılış tamamlandı. Son ekran görüntüleri `app/build/reports/screenshots/` dizinindedir.

## Canlı Supabase bağlantısı — 17 Ekim 2026

Kullanıcı `bagimliligim` projesini oluşturdu: `jgbamatcdxytjqrgkiin`, Free, Tokyo. Şema, 365 günlük saklama geçişi, açık RPC yetkileri ve saatlik temizleme görevi kuruldu. Başlangıç geçişi tarihçe için korunur; etkili süre ikinci geçişle 365 gündür. GitHub aktarımı kullanıcı isteğiyle yayın sonrasına alındı.

Canlı PostgreSQL testinde arkadaş kabulü, yabancı hesap yalıtımı, tekrar gönderimde tek kayıt, tarihsel tutar, paylaşımı kapatma, engelleme ve hesap silme başarılı. Sentetik testler geri alındı, kalıcı hesap bırakılmadı. HTTP üzerinden açık katalog erişimi ve kişisel tablolara doğrudan erişimin reddi dahil 11 kontrol başarılı. 36 güncel mağaza teklifi canlı karşılaştırma kataloğuna eklendi.

`scripts/build-connected-release-windows.ps1` ile bağlı APK/AAB üretildi. 36 JVM testi, Android test paketindeki 11 test, lint ve manifest izin kontrolleri başarılı; dış YouTube testi olağan koşumda atlandı. Daha önceki bağımsız gerçek Shorts kabulü geçerlidir. Canlı hesap girişi ve gerçek e-posta teslimi henüz doğrulanmadığından bu durum yayıma hazır sosyal sürüm anlamına gelmez.

Brevo adres adımında duruldu: kullanıcı posta adresinin e-postalarda görünmesini istemiyor. SMTP bağlanmadı. Kullanıcı Google ile girişi seçti; 18 Ekim adayı e-posta/şifre ekranının yerine Credential Manager kullanır. Gizlilik/hesap silme sayfaları, Play beyanları, fiziksel S25 FE kabulü ve gerçek hesaplarla uçtan uca test yayın öncesinde tamamlanmalı.

## Google girişli test adayı — 18 Ekim 2026

`dist/v2-google-test/Bagimliligim-2.0.0-google-test.apk` ve aynı dizindeki AAB, canlı Supabase adresi ve açık Google Web client ID ile üretildi. Mevcut yükleme sertifikası korundu. APK/AAB SHA-256 değerleri yanlarındaki `SHA256SUMS.txt` dosyasındadır. Önceki `v2-test` ve `v2-connected-test` paketleri güncel Google akışını içermez.

Google kimlik kanıtı nonce ile sunucuda doğrulanır; Google tokenı diske yazılmaz. Kullanıcı onayı olmadan giriş başlatılmaz. Hatalı sunucu yanıtı oturum oluşturmaz, çevrimdışı çıkış yerel oturumu temizler. Credential Manager'ın transitif biyometrik izinleri manifestten çıkarıldı; uygulama yalnızca ilan edilen izinleri içerir.

36 JVM testi başarılı. Son Android raporunda 16 testten 15'i başarılı, yalnızca ayrı etkinleştirilen gerçek YouTube kabul testi atlandı. Debug lint: 0 hata, 29 uyarı. Üç varyantın manifest izin kontrolü ve R8 release APK/AAB derlemesi başarılı. Google onay kartı ekran görüntüsü `app/build/reports/screenshots/v2-google-sign-in.png` altındadır.

18 Ekim 08:38 Türkiye saatinde canlı `check-live --require-google` kontrolünde Google sağlayıcısı etkin, anonim sosyal erişim ve sekiz tabloya doğrudan erişim kapalı olarak doğrulandı: 12 kontrol başarılı. Bu sonuç gerçek Google hesap seçimi ve oturum açma testi değildir. Yeni Google ayarlarının yayılması zaman alabilir. Bu aday henüz Play'e yüklenmedi ve son kullanıcıya yayımlanmadı.

Cloud'da mevcut debug, yerel release ve Play dağıtım sertifikalarına ait üç Android istemcisi oluşturuldu. Play SHA-1, indirilen sertifikanın Console'daki SHA-256 ile eşleşmesi doğrulanarak kaydedildi. Google veri kapsamları yalnızca kimlik, e-posta ve temel profildir. Supabase nonce denetimi açık tutuldu. Ayrıntılı açık istemci kimlikleri ve parmak izleri `backend/GOOGLE-SIGN-IN.md` dosyasındadır.

Google girişli imzalı APK ayrıca Android 16 / API 36.1 emülatörüne kuruldu; soğuk açılış `Status: ok` ile tamamlandı. Gerçek Google hesap seçimi bu kontrolde denenmedi.

## Play dahili test taslağı — 18 Ekim 2026

Doğru Play uygulamasında (`4972884662806320072`) dahili test kanalı etkin; **1.0.1 — Beta**, sürüm kodu 2, 16 Ekim 21:18'den beri testçilere açık. V2 için `2.0.0 — Google girişli V2 Beta` adlı yeni taslak ve Türkçe sürüm notları kaydedildi. Kanal `4701722803960339501`, taslak sürüm kimliği `2`.

Chrome uzantısının yerel dosya erişimi kapalı olduğundan AAB yüklemesi gerçekleşmedi. Hazır dosya `dist/v2-google-test/Bagimliligim-2.0.0-google-test.aab`; açık taslaktaki Yükle düğmesinden kullanıcı tarafından seçilmeli. Yükleme sonrası sürüm kodu 3, paket, imza ve Console kontrolleri doğrulanmadan dağıtıma geçilmez. Taslak oluşturmak testçilere V2 dağıtıldığı anlamına gelmez. Dahili testte gerçek Google girişi, arkadaş paylaşımı ve S25 FE kabulü yapılır; genel yayın koşulları ayrıca tamamlanır.
