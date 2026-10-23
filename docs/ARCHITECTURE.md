# Mimari ve veri davranışı

Bağımlılığım'ın sayım, yerel kayıt ve paylaşım davranışları. Kurulum için [ana README](../README.md) dosyasına bakın.

## Yapı

- `domain`: saf sayım sezgiseli, zaman dilimleri, bütçe, para hesabı ve analitik.
- `data/local`: Room v2, v1→v2 veri kaybetmeyen migration, ayrı kaydırma/sigara tabloları.
- `data/remote`: HTTPS fiyat kaynakları, şifreli oturum kasası, Supabase istemcisi.
- `presentation`: MVVM; Bugün, İstatistikler, Sigara, Çevrem, Ayarlar.
- `service`: onaya bağlı erişilebilirlik adaptörü, bellekte toplu kayıt ve bütçe hatırlatmaları.
- `widget`: 2×2 / 4×2 sağlayıcılar, yeniden boyutlandırma ve elle yenileme.
- `backend`: RLS ve yetki denetimli SQL RPC'leri, fiyat katalog araçları, gerçek PostgreSQL güvenlik testleri.

## Sayımın sınırı

Sayım yalnızca Instagram, iki TikTok paketi ve YouTube için yapılır. Pencere değişimleri uygulamadan çıkışta tamponu boşaltmak için diğer paketlerden de alınır; bu paketlerin ağaçları incelenmez. Açık uygulama içi onay gelene kadar servisin olay maskesi sıfırdır. Onay geri alınabilir.

`TYPE_VIEW_SCROLLED`, `TYPE_WINDOW_STATE_CHANGED` ve YouTube sayfalayıcıları için `TYPE_VIEW_SELECTED` dinlenir. Metin, içerik açıklaması, mesaj, hesap adı veya video okunmaz. Kaynak sınıfı, indeks, kaydırma miktarı, sınırlar ve en fazla 16 üst düğümün kaynak kimliği incelenir. YouTube'da kaynak düğüm küçük olabildiğinden bilinen Shorts üst kapsayıcısı kullanılır; sınırlı görünür düğüm taraması en fazla 750 ms'de bir yapılır.

Yorum, sohbet, arama ve alt panel kaynakları dışlanır. Tam ekranın en az %65'i boyutunda video kapsayıcısı, ardışık tek indeks geçişi veya 900 ms içinde biriken yeterli dikey hareket aranır. 450 ms sayım aralığı ve yatay hareket reddi vardır. `fromIndex/toIndex` önceki/sonraki video değildir; görünür aralığı temsil eder. İlk indeksli olay başlangıç noktasıdır.

Üçüncü taraf uygulamalar kaynak kimliklerini ve olay davranışlarını değiştirebilir. Bu bir kaydırma tahminidir; izlenme süresini veya videonun izlendiğini kanıtlamaz. Otomatik testler gerçek S25 FE ve güncel Instagram/TikTok/YouTube üzerinde kabul testinin yerini tutmaz. Uygulama başka uygulamalarda tıklama veya hareket gerçekleştirmez.

## Yerel kayıt ve para

Kaydırmalar platform/tarih/saat/kalibrasyon kovalarında tutulur. Her 10 kaydırmada, uygulamadan çıkışta, ekran kapanırken veya elle yenilerken tek sıradan Room'a yazılır. Batch UUID tekrar denemeyi güvenli kılar. Widget yazma tamamlanınca güncellenir. İşlem aniden ölürse henüz bellekteki olaylar kaybolabilir; sıfır pil tüketimi ya da tam çökme dayanıklılığı vaat edilmez.

Sigara kaydı paket fiyatını, paket adedini, kaynağını ve maliyeti kendi satırına kopyalar. Fiyat sonradan değişince eski satır güncellenmez. Kuruşlar tamsayı, tüketim maliyeti liranın milyonda biri olarak tutulur. Geçmiş tarih için kanıtlı fiyat yoksa kullanıcı o tarihte ödediği tutarı girer. Eski güne güncel fiyat uygulanmaz. Boş gün otomatik olarak “içmedim” sayılmaz.

Ürün karşılaştırmaları yalnızca son 24 saatte doğrulanmış, stokta ve TL fiyatlı ürünler içindir. Katalog 62 farklı ürün bağlantısıyla başlar; doğrulanamayan ürünler gösterilmez. Kaynaklar IKEA Türkiye ve Kitapyurdu'dur; ev/yaşam ve kitap alternatifleri içerir. Sigara fiyatları Medyaradar'ın tarihli haber listesinden okunur; resmi satış garantisi değildir ve kullanıcı gerçek ödediği fiyatı değiştirebilir. Kaynak eskiyse yeniymiş gibi işaretlenmez. Ayrıntılar ve sınırlar backend belgesindedir.

## Sosyal gizlilik

Google ile giriş, kullanıcının ekrandaki açıklamayı kabul edip düğmeye dokunmasıyla başlar. Credential Manager'dan alınan kimlik kanıtı, her denemeye özel nonce ile Supabase'de doğrulanır. Yalnızca Supabase oturumu Android Keystore ile şifrelenerek saklanır. Google girişi kayıt özetlerini kendiliğinden paylaşmaz; Gmail, Drive veya YouTube hesabına erişim istemez.

Hesapsız kullanım sürer. Arkadaşlar kodla eklenir ve karşılıklı kabul edilir. Kaydırma ve sigara paylaşımı ayrı ayrı seçilir. Kullanıcı “Paylaş” ile son 14 günlük seçili özetleri gönderir; ham erişilebilirlik olayları aktarılmaz. Sunucu her istekte kimlik, arkadaşlık ve paylaşım tercihini kontrol eder. Opt-out ilgili uzak özetleri siler; engelleme erişimi keser. Hesap silme kullanıcı ve ilişkili uzak verileri kaldırır. Yerel veriler ayrıca cihazda yönetilir.

Azalma derecesi için iki haftanın da yedi gün kaydı gerekir. Eksik günler sıfıra çevrilmez. Harcama ve kaydırmada düşük değer önce gelir; daha çok sigara içmek ödüllendirilmez. Kayıtlar kullanıcı beyanıdır. Canlı projede 365 günlük sunucu saklama süresi ve saatlik `retention.sql` temizleme görevi kuruludur; yeni kurulumlarda aynı görev etkinleştirilmelidir.
