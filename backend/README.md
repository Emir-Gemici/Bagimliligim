# Bağımlılığım V2 sunucu kurulumu

Android uygulaması sunucu olmadan kaydırma/sigara kayıtlarını tutar. İsteğe bağlı fiyat güncelleme, herkese açık mağaza ve haber tablolarını okuyabilir. Bu dizin SQL geçişlerini, yetkilendirme testlerini ve fiyat yayınlama araçlarını içerir.

17 Ekim 2026'da `bagimliligim` projesine (`jgbamatcdxytjqrgkiin`, Free, Tokyo) üç SQL geçişi ve saatlik 365 günlük saklama görevi uygulandı. 8 tabloda RLS açık; doğrudan API erişimleri kapalı. Gerçek PostgreSQL üzerinde sentetik üç hesapla erişim testi başarılı; test işlemi geri alındı ve kalıcı hesap bırakmadı. Mağazalardan o gün yeniden doğrulanan 36 karşılaştırma ürünü canlı kataloğa aktarıldı. 18 Ekim'de Google sağlayıcısı bağlandı; açık HTTP API üzerinden Google etkinlik kontrolü dahil 12 kontrol geçti. Gerçek Google hesabıyla giriş ve arkadaş paylaşımı henüz uçtan uca doğrulanmadı.

## Kurulum

1. [Google giriş kurulumunu](GOOGLE-SIGN-IN.md) tamamla. Android Credential Manager ile alınan Google kimliği Supabase'de doğrulanır. Uygulamada e-posta/şifreyle kayıt yoktur; bu akış SMTP gerektirmez. Google sağlayıcısında nonce denetimini açık tut. Hız sınırlarını gerçek istemci akışıyla test et.
2. SQL Editor veya Supabase CLI ile `supabase/migrations/` dizinindeki dosyaları ad sırasıyla uygula. İkinci geçiş başlangıçtaki saklama süresini 365 güne yükseltir; üçüncü geçiş Supabase'in varsayılan API rolü yetkilerini açıkça sınırlar. Tablo erişimleri kapalıdır; uygulama yalnızca kimliği denetlenen RPC işlevlerini kullanır. `supabase/verify-live.sql`, sentetik hesaplarla erişimi sınar ve bütün test kayıtlarını geri alır. Ayrıca iki ayrı kullanıcı ve üçüncü bir ilgisiz kullanıcıyla canlı uygulama testi yap.
3. Supabase Cron’u açıp `supabase/retention.sql` dosyasını çalıştır. Saatlik görev, 365 günlük tutma sınırını uygular. Bugün ve önceki 364 gün saklanır; daha eski günlük özetler silinir. Paylaşımı kapatma ve hesap silme, bu süreyi beklemeden ilgili verileri siler.
4. Android derlemesinde `BAGIMLILIGIM_BACKEND_URL`, `BAGIMLILIGIM_PUBLIC_KEY` ve `BAGIMLILIGIM_GOOGLE_WEB_CLIENT_ID` ortam değişkenlerini ver. Yalnızca publishable/anon anahtar kullan; `service_role` veya OAuth client secret uygulamaya konmaz. Varsayılan derlemede hesap hizmeti kapalıdır ve arayüz bunu açıkça söyler. Bu bilgisayarda `scripts/build-connected-release-windows.ps1`, Git dışında kalan `.local/backend.public.json` içindeki açık değerleri kullanır; `googleWebClientId` zorunludur. `npm run check-live -- --config ../.local/backend.public.json --require-google`, kişisel kayıt oluşturmadan gerçek API erişimlerini ve Google sağlayıcısını sınar.
5. Hesap açma/doğrulama, arkadaşlık kabul/red/engelleme, ölçüm bazlı paylaşım, paylaşımı kapatınca silme ve hesap silme akışlarını canlı projede doğrula. Play veri güvenliği ve yayımlanmış gizlilik/hesap silme sayfaları gerçek kurulumla eşleşmelidir.

## Fiyat akışı

`npm ci` ve `npm test` PostgreSQL motorunda yetki ve fiyat geçmişi testlerini çalıştırır. Testlerdeki para tutarları açıkça test verisidir; uygulamanın fiyat havuzuna aktarılmaz.

`npm run catalog` 62 gerçek IKEA/Kitapyurdu ürün adresinden Product/Offer JSON-LD okur. Yalnızca stokta, TRY cinsinden, geçerli fiyatı olan teklifler kabul edilir; kaynak tarihi başarılı okumada yenilenir. Sonuç `local-output/comparisons.json` dosyasına yazılır. `npm run catalog -- --publish` doğrulanan satırları bağlı sunucuya gönderir. Gerekli sunucu ortam değişkenleri `BAGIMLILIGIM_BACKEND_URL` ve `BAGIMLILIGIM_SERVICE_ROLE_KEY`’dir. Bunları yalnızca güvenli sunucu/CI secret alanında tut. Görevi 6 saatte bir çalıştır; başarısızlıkta eski kontrol tarihini koru. İstemci 24 saatten eski fiyatı göstermeyi bırakır.

Sigara için `npm run publish-prices -- fiyatlar.json --publish` veya `BAGIMLILIGIM_VERIFIED_PRICE_FEED` ile sağlanan HTTPS JSON akışı kullanılabilir. Her satır `brand_id`, `brand_name`, `family`, `pack_size`, `price_kurus`, `valid_from`, `checked_at`, `source`, `source_url` alanlarını içerir. `valid_from`, kaynakta teyit edilen yürürlük tarihidir; yalnızca gözlem tarihi biliniyorsa geçmiş tarih uydurulmaz. Aynı marka/tarih tekrarları yok sayılır. Yayımlanmış fiyat satırları veritabanı tetikleyicisiyle değiştirilemez. Tüketim kayıtları fiyat ve tutarı ayrıca sakladığından ileriki zamlar geçmiş harcamayı değiştirmez.

Sunucu yapılandırılmamışsa, erişilemiyorsa veya güncel kataloğu yetersizse Android doğrudan IKEA, Kitapyurdu ve Medyaradar kaynaklarını kullanır. Haber listesindeki açık ürün/fiyat eşleşmeleri kabul edilir; 14 günden eski kaynak güncellemesi reddedilir. Bu rakamlar **kaynakta bildirilen fiyat** olarak gösterilir, üreticinin resmi tarifesi gibi sunulmaz. Kullanıcı gerçek ödediği paket fiyatını girebilir. Haberin yapısı değişirse veya kaynak eskirse yeni fiyat üretmek yerine hata gösterilir. Güncel liste adresleri `TobaccoCatalog.INDEX_URL` etiket sayfasından keşfedilir; yalnızca aynı alan adındaki tarihli, doğrulanan tablolar kullanılır.

## Paylaşım sınırları

- Google ile hesap açmak isteğe bağlı; kişisel kayıtlar Room’da kalır.
- Varsayılan olarak hiçbir ölçüm paylaşılmaz. Son 14 günün seçili günlük özetleri yalnızca kullanıcı Paylaş’a basınca gönderilir. İnternetsizken yerel kayıt kaybolmaz; kullanıcı bağlantı gelince yeniden paylaşır. Arka planda sessiz yükleme yoktur.
- Karşılıklı kabul olmadan karşı tarafın ölçümleri okunamaz. Rastgele kullanıcı kimliğiyle okuma/yazma mümkün değildir.
- Marka filtresi, azalma derecesi, harcama ve kaydırma sıralaması sunucuda hesaplanır. Daha fazla sigara/harcama ödüllendirilmez. Azalma için iki haftanın da yedi günü kayıtlı olmalıdır; boş günler sıfır sayılmaz. Markalar arasında geçiş gerçek toplam azalmayla aynı değildir; tüm markalar görünümü toplamı gösterir.
- Tokenlar Android Keystore AES-GCM ile saklanır; şifreler saklanmaz. Özel sunucu anahtarı istemciye verilmez.
- Paylaşımı kapatmak ilgili uzak özetleri siler; hesabı silmek hesap, bağlantılar ve özetleri siler. Yerel kayıtlar korunur. Çıkış yapmak silme değildir.

## Yayın öncesi kalan dış bağımlılıklar

Google girişi seçildi; istemci kodu ve Cloud/Supabase temel giriş yapılandırması tamamlandı. Gerçek hesaplarla uçtan uca kabul ve genel yayın için marka/gizlilik sayfaları bekliyor. Brevo SMTP kullanılmıyor. Karşılaştırma kütüphanesi iki mağazadan ev/yaşam ürünleri ve kitaplar içeriyor; spor, deneyim ve teknoloji sağlayıcıları doğrulanmış fiyat kaynaklarıyla ayrıca eklenmeli. Sayfa okumak bir sürekli fiyat API’si garantisi değildir. Kaynak güncelliği sınırları aşıldığında ürün/fiyat saklanır ama güncel diye sunulmaz.

## Katalog zamanlaması

`.github/workflows/catalog.yml`, 6 saatte bir çalışacak görev tanımını içerir. Etkinleştirmek için depo değişkenleri `BAGIMLILIGIM_CATALOG_ENABLED=true`, `BAGIMLILIGIM_BACKEND_URL` ve sunucu secret alanında `BAGIMLILIGIM_SERVICE_ROLE_KEY` gerekir. İsteğe bağlı doğrulanmış tütün listesi için `BAGIMLILIGIM_VERIFIED_PRICE_FEED` değişkeni kullanılır. Anahtar loglara veya APK'ya yazılmaz. Depoyu GitHub'a yüklemek tek başına bu görevi etkinleştirmez; gerekli değişkenler ve secret ayrıca tanımlanmalıdır. Android'in doğrudan kaynak yenilemesi bağımsız çalışır.
