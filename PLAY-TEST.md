# Bağımlılığım — arkadaşlarla test

## Mevcut durum

Uygulama: **Bağımlılığım**, paket `com.bagimliligim.app`. V1'in 1.0.1 / kod 2 AAB yüklemesi Console'da tamamlandı; son yayın düğmesinin sonucu bu oturumda doğrulanmadı. V2 **2.0.0 / kod 3** olarak hazırlanıyor ve henüz Console'a gönderilmedi.

[Bağımlılığım kontrol paneli](https://play.google.com/console/u/0/developers/8001120540746946619/app/4972884662806320072/app-dashboard) hazır. **Durak İstanbul Deneyecekler** listesindeki 26 kişi bu uygulamanın dahili test kanalına seçilip kaydedildi; e-postaları yeniden yazmak gerekmiyor. `com.swipemeter.app` boş taslağına yükleme yapılmamalı.

Simge `branding/play-store-icon-512.png`: bağlı kalpler. Aynı yükleme anahtarı sonraki sürümlerde korunur. `.signing/` içindeki P12/DPAPI dosyalarını testçilere göndermeyin. Anahtar yedekleri ve şifreler kaynak kontrolü dışında saklanır.

## V2 dağıtımı

1. [V2 yayın hazırlığındaki](V2-RELEASE.md) dış bağımlılıkları tamamla: canlı sosyal hizmet, fiziksel Shorts/widget testi ve gerçek davranışa uygun gizlilik beyanları.
2. `scripts/build-release-windows.ps1` ile mevcut anahtarı kullanarak AAB üret. `app/build/outputs/bundle/release/app-release.aab` dosyasının sürüm kodunu kontrol et.
3. Mevcut uygulama kaydında Dahili test bölümüne yeni sürüm ekle. AAB'yi yükle; kod 3 daha önce kullanılmışsa kodu artırarak yeniden derle.
4. Sürüm notlarında yalnızca canlı dağıtımda çalışan özellikleri belirt. Console doğrulamalarını tamamla ve dahili test sürümünü yayımla.
5. Aynı 26 kişilik listeyi koru. Katılım bağlantısını Console'dan al; arkadaşlar listede kayıtlı Google hesabıyla katılıp Play Store'dan yükler. AAB telefona doğrudan kurulmaz.

V2'de güncel fiyatlar ve sosyal hizmet için INTERNET izni vardır. Fiyat güncellemesi ve paylaşım başlangıçta kapalıdır. SMS, kişiler, konum, mikrofon veya bildirim okuma izni yoktur. V1'e ait “internet izni yok” mağaza/gizlilik metinleri bu sürümde kullanılamaz.

## Play Protect

S25 FE'de bildirilen kurulum engeli Google Play Protect'ti. İmza veya logo değişikliği bu denetimi geçme garantisi vermez. Arkadaşlara Play Protect'i kapattırmak dağıtım yöntemi değildir; dahili test ve doğru beyanlar kullanılmalıdır. Erişilebilirlik onayı uygulamada ayrıca alınır ve geri çekilebilir.

Kaynaklar: [Play Protect geliştirici rehberi](https://developers.google.com/android/play-protect/warning-dev-guidance), [dahili test kılavuzu](https://support.google.com/googleplay/android-developer/answer/9845334?hl=tr).

## Cihaz kabulü

- Güncel Instagram, TikTok ve YouTube sürümlerini kaydet. Her birinde 20 yavaş ileri/geri video geçişini sayımla karşılaştır. Yorumlar, mesajlar, arama ve yatay galeri sayılmamalı. İlk indeksli olay başlangıç noktasıdır.
- 9 kaydırmadan sonra uygulamadan çık/ekranı kapat: kayıt ve widget güncellenmeli. 10 kaydırmada otomatik yazmayı; iki boyutta Yenile ve ana ekrana geçişi kontrol et.
- V1 üzerine güncellemede geçmiş korunmalı. Eski tarihli sigara kaydını oluştur, sonraki tarihe yeni fiyat ekle; önceki maliyet değişmemeli. Kaydı geri alma ve içilmemiş gün kaydı ayrı davranmalı.
- Fiyat güncellemesi kapalıyken yerel kayıt sürmeli. Kaynak/kontrol tarihi görünmeli; eski ürün fiyatı karşılaştırmada kullanılmamalı.
- İki karşılıklı arkadaş ve üçüncü ilgisiz hesapla paylaşım/engelleme/silme testi yap. Seçilmeyen ölçümler karşı tarafta görünmemeli.

Sürüm notu taslağı ve yayımlamadan önce kalan işler `V2-RELEASE.md` içindedir. Bir testin veya AAB derlemesinin başarılı olması Google Play yayınının tamamlandığı anlamına gelmez.
