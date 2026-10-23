# Bağımlılığım — ürün yönü

## V2: kendi alışkanlığını gör, küçük bir alan aç

Tasarımın odağı her şeyi karta koymak değil, kişinin bir bakışta bugünkü bütçesini ve harcamasını görmesi. Bağlı kalpler simgesinin krem/pembe paleti, geniş boşluklar, belirgin sayılar ve kısa Türkçe metinler kullanılır. Sosyal karşılaştırma daha fazla tüketimi ödüllendirmez.

Uygulananlar: Shorts algılamasında kapsayıcı ve biriken hareket desteği; tam Instagram adı; tarihsel fiyatı kilitlenen marka bazlı sigara kayıtları; opt-in güncel fiyatlar; dönen ürün karşılaştırmaları; arkadaş kabulü, paylaşım seçimi, marka filtresi, azalma/harcama/kaydırma sıralamaları ve hesap silme.

Supabase şeması ve 365 günlük saklama görevi kuruldu, canlı erişim testleri geçti. Canlıya geçmeden kalanlar: hesap girişinin tamamlanması ve gerçek kullanıcı testi, güncel katalog zamanlaması; fiziksel S25 FE ve arkadaş cihazlarında sayım/widget kabulü; gerçek hizmet davranışıyla uyumlu Play ve gizlilik beyanları. Bunlar tamamlanmadan sosyal hizmet çalışıyor veya V2 yayımlandı denmez.

## İlk kullanım geri bildirimiyle sonraki adımlar

Android kaynakları, SQL geçişleri ve testler bu depoda birlikte sürümlenir. İmza anahtarları, DPAPI şifre dosyası, ortam sırları, yerel çıktılar ve hesap ekran görüntüleri Git dışında tutulur; mevcut imza kimliği korunur. GitHub'a aktarım, Play yayını ve canlı hizmet kabulünden ayrı bir adımdır.

1. YouTube/Instagram/TikTok sürümüne göre yanlış ve kaçan sayımları ölçmek. Ekran içeriği toplamadan yalnızca sayısal tanı göstergeleriyle sorun çözmek.
2. Sigara kaydını daha az dokunuşla tamamlamak; ayrı widget ve kullanıcının belirlediği kişisel azaltma hedefini değerlendirmek.
3. Ürün alternatiflerini kitap, ulaşım, etkinlik ve eğitim gibi farklı alanlara genişletmek. Her yeni kaynak için erişim koşulları, stok/fiyat doğrulaması ve yenileme süresi tanımlanmalı; sabit rakam “güncel” diye sunulmamalı.
4. İsteğe bağlı gönderim kuyruğunu hesap ve silme durumlarıyla güvenli biçimde eklemek. İlk sosyal sürümde paylaşım açıkça elle yapılır; görünmez sürekli senkronizasyon yoktur.
5. Arkadaşlarla ortak azaltma hedefleri ve destek mesajları değerlendirmek. Genel herkese açık tüketim yarışı veya daha fazla tüketimi özendiren dereceler eklememek.

Öncelikler test sonuçlarıyla değişir. Sağlık sonucu, kusursuz ölçüm veya üçüncü taraf fiyatların değişmezliği vaat edilmez. Mevcut geliştirme/yayın durumu [V2-RELEASE.md](V2-RELEASE.md) içinde tutulur.
