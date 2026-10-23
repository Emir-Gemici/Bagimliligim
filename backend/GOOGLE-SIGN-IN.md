# Google ile giriş

Android tarafı Credential Manager 1.6.0 ve googleid 1.1.1 kullanır. googleid 1.2.1, Kotlin 2.4 metadata içerdiği için mevcut AGP 9.0.1 / Kotlin 2.2.10 derleyicisiyle uyumsuzdur. Sürüm kontrolü kapatılmadan uyumlu kararlı sürüm sabitlenmiştir.

Kullanıcı Çevrem ekranındaki açıklamayı kabul edip Google düğmesine dokunduğunda hesap seçici açılır. Her denemede SecureRandom ile 32 bayt nonce üretilir. Google'a SHA-256 özeti; Supabase `/auth/v1/token?grant_type=id_token` adresine Google ID token ve ham nonce gönderilir. İmza, audience ve nonce doğrulaması Supabase'de yapılır. Google tokenı kaydedilmez; yalnızca Supabase oturumu Android Keystore AES-GCM ile saklanır. Başarısız/eksik yanıt oturum oluşturmaz. İptal sessizdir; çıkışta yerel oturum çevrimdışıyken de temizlenir ve Credential Manager oturum temizliği denenir.

Bu akış Google API erişim tokenı istemez. Gmail, Drive, kişiler veya YouTube hesabı kapsamı kullanılmaz. Sigara ve kaydırma özetleri giriş sırasında yüklenmez. Profilde iki paylaşım seçeneği başlangıçta kapalıdır.

## Cloud yapılandırması

- Ayrı proje: `bagimliligim-app`, proje numarası `466882017486`.
- Marka: Bağımlılığım. OAuth politika adımını kullanıcı tamamladı.
- Web istemcisi: `Bagimliligim Android - Supabase`.
- Açık Web client ID: `466882017486-m492fstc73du63ccabvrpepm1839toah.apps.googleusercontent.com`.
- Yetkili dönüş adresi: `https://jgbamatcdxytjqrgkiin.supabase.co/auth/v1/callback`.
- Android paket adı: `com.bagimliligim.app`.
- Yerel release yükleme sertifikası SHA-1: `CE:B7:F1:FC:F9:36:70:AB:25:EE:F0:29:D5:B7:B7:FA:A9:86:6B:99`.
- Yerel release Android client ID: `466882017486-4g14cvb2kb62vb5c6sl93kiegev3n416.apps.googleusercontent.com`.
- Bu bilgisayarın debug sertifikası SHA-1: `DB:24:4B:AC:C5:EC:BF:17:41:3E:CB:E1:10:6A:3E:78:28:80:C7:FB`.
- Debug Android client ID: `466882017486-pcqrqjgpq60vfum7h54g0p88r5l1qt9n.apps.googleusercontent.com`.
- Play dağıtım sertifikası SHA-1: `E6:F5:CF:9A:51:AA:6B:B5:22:37:A5:7E:D6:A9:81:0C:D9:1F:FC:FB`.
- Play Android client ID: `466882017486-014rmep4a7ilakgp6ssl9k7tr1l9u58v.apps.googleusercontent.com`.
- Play sertifikası yereldeki `certificates (1).zip` içindeki `deployment_cert.der` dosyasından okundu. SHA-256 değeri Console'da görülen `9C:4C:63:07:29:C8:4D:86:20:3E:7C:80:1D:3F:9F:0C:B4:29:F4:E0:FB:8B:60:4E:E8:D7:31:93:6A:9D:07:1C` ile eşleşti. Yükleme sertifikası Play dağıtım sertifikasının yerine kullanılmaz. Arşivdeki ayrı deneysel hibrit sertifika mevcut dağıtım sertifikası yerine kaydedilmedi.

18 Ekim 2026'da üç Android istemcisi oluşturuldu ve Data Access bölümüne yalnızca `openid`, `userinfo.email` ve `userinfo.profile` kaydedildi. Audience: External / Testing; test kullanıcı listesi boş. Google'ın [temel kimlik kapsamları istisnasına](https://support.google.com/cloud/answer/15549945) göre bu giriş akışı için kullanıcıları ayrıca OAuth test listesine eklemek gerekmez. Başka kapsam eklenirse istisna geçerli değildir. Play'in 26 kişilik test listesi ayrı bir dağıtım listesidir. Marka sayfası ve doğrulaması genel yayın öncesinde tamamlanmalıdır; Console'daki uygulama adı/logo görünürlüğü doğrulama durumuna bağlıdır.

Her kullanılan paket + imza çifti için aynı Cloud projesinde Android OAuth istemcisi gerekir. `.qa` paketi ayrı bir istemci gerektirir; oluşturulmadıkça Google girişiyle kabul testi için kullanılmaz. Play App Signing anahtarı değişirse yeni parmak izi de kaydedilmelidir.

## Supabase yapılandırması

Authentication > Sign In / Providers > Google altında Web client ID izinli istemcilere eklenir. `Skip nonce checks` ve `Allow users without an email` kapalı kalır. Native ID token akışı OAuth Web client secret kullanmaz; APK'ya veya kaynak kontrolüne gizli anahtar konmaz. Web OAuth ayrıca etkinleştirilecekse secret yalnızca sunucu sağlayıcı ayarında saklanır.

Google sağlayıcısı 18 Ekim 2026'da kaydedildi; Web client ID izinli, nonce kontrolleri açık ve e-postasız giriş kapalı. Native akış için secret alanı boş bırakıldı ve Supabase ayarı kabul etti. `check-live --require-google` canlı uç noktada sağlayıcının açık olduğunu doğruladı; 12 erişim kontrolü başarılı. Bu, gerçek Google tokenıyla oturum açıldığı anlamına gelmez. Yeniden doğrulamak için backend dizininden çalıştır:

```powershell
npm run check-live -- --config ../.local/backend.public.json --require-google
```

Son kontrol; Play'den kurulu uygulamada ilk giriş, hesap seçimini iptal, çıkış/başka hesapla giriş ve gerçek iki hesapla arkadaşlık/paylaşım akışıdır. Yerel sahte taşıma katmanı testleri gerçek Google girişinin yerine geçmez. Yayın öncesi gizlilik sayfası ve Google consent ekranı gerçek veri kullanımını açıklamalıdır.

Kaynaklar: [Android uygulama rehberi](https://developer.android.com/identity/sign-in/credential-manager-siwg-implementation), [Supabase Google](https://supabase.com/docs/guides/auth/social-login/auth-google), [Google giriş markası](https://developers.google.com/identity/branding-guidelines).
