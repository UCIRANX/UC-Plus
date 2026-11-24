<div align="center">

# 🦁 UC Plus

### کلاینت اندروید با پشتیبانی از چند پروتکل، ساب‌لینک و بروزرسانی خودکار

[![Telegram](https://img.shields.io/badge/Telegram-@UCIRANIR-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/UCIRANIR)
[![Android](https://img.shields.io/badge/Android-8.0+-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#-نصب)
[![Kotlin](https://img.shields.io/badge/Kotlin-Compose-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](#-ساختار-پروژه)
[![License](https://img.shields.io/badge/License-See%20LICENSE-lightgrey?style=for-the-badge)](LICENSE)

**📣 کانال رسمی: [@UCIRANIR](https://t.me/UCIRANIR)**
برای دریافت آخرین نسخه‌ها، آموزش‌ها و اطلاعیه‌ها عضو شوید

</div>

---

<div dir="rtl">

## ✨ معرفی

**UC Plus** یک کلاینت VPN برای اندروید است که با Kotlin و Jetpack Compose نوشته شده است. از موتورهای **sing-box**، **Xray**، **OpenVPN**، **SSTP** و **Psiphon** استفاده می‌کند و هدفش این است که اتصال ساده باشد: یک ساب‌لینک، یک لیست مرتب، و یک دکمه.

<br>

## 🚀 امکانات

| | امکان | توضیح |
|:-:|---|---|
| 🔌 | **چند پروتکل** | AmneziaWG، Psiphon، Tor، SSTP، OpenConnect، OpenVPN، Shadowsocks، VLESS و Hysteria 2 |
| 📥 | **ساب‌لینک** | ورود گروهی سرورها از لینک اشتراک، با بروزرسانی دستی یا خودکار |
| 🔗 | **ساب‌لینک تودرتو** | اگر داخل یک ساب‌لینک، لینک ساب‌لینک دیگری باشد، تا دو سطح دریافت می‌شود |
| 🧅 | **Tor Auto** | یک ردیف خودکار که بهترین کشور Tor را بر اساس پینگ انتخاب می‌کند |
| 🕊️ | **Psiphon Auto** | یک ردیف خودکار برای Psiphon که بهترین کشور را بر اساس پینگ انتخاب می‌کند |
| 📶 | **پینگ هوشمند** | پینگ گروهی و موازی؛ برای Psiphon و Tor با تونل آزمایشی، برای بقیه با TCP |
| ⭐ | **علاقه‌مندی‌ها** | سرورهای مورد علاقه بالای لیست می‌مانند و از حذف خودکار محافظت می‌شوند |
| 🧭 | **مسیریابی** | قوانین مستقیم، پراکسی و مسدود بر اساس دامنه، IP و دسته‌های آماده (ایران، روسیه، چین) |
| 🌐 | **DNS جداگانه** | DNS مستقیم و DNS از طریق پراکسی، هر کدام با سرورهای جدا |
| 🛡️ | **تونل کامل** | VPN سراسری برای کل گوشی یا انتخاب برنامه‌ها |
| 🔍 | **اسکنر IP برای Psiphon** | اسکن بازه‌های CDN و IPهای دستی برای پیدا کردن IPهای سالم |
| 🎨 | **رابط کاربری ساده** | یک ردیف ابزار فشرده، منوی پروتکل‌ها، و پاپ‌آپ پینگ و اسکن |

<br>

## 📲 نصب

1. از بخش **[Releases](../../releases)** در همین ریپو، آخرین نسخه را دانلود کنید.
2. فایل APK مناسب گوشی خود را انتخاب کنید:

| فایل | مناسب برای |
|---|---|
| `arm64-v8a` | ✅ بیشتر گوشی‌های جدید (۶۴ بیتی) |
| `armeabi-v7a` | 📟 گوشی‌های قدیمی ۳۲ بیتی |
| `x86_64` | 💻 شبیه‌سازها و دستگاه‌های x86 |

3. فایل را نصب کنید. اگر نسخه قبلی با کلید دیگری امضا شده باشد، ابتدا باید حذف شود.

> [!NOTE]
> حداقل نسخه اندروید **۸.۰ (API 26)** است.

<br>

## 🧭 شروع سریع

1. برنامه را باز کنید. لیست سرورها از ساب‌لینک‌های داخلی بارگذاری می‌شود.
2. دکمه **بروزرسانی** را بزنید تا ساب‌لینک‌ها دریافت شوند.
3. از منوی پروتکل‌ها، پروتکل دلخواه را انتخاب کنید (یا **All Protocols** را برای همه).
4. دکمه **پینگ** را بزنید تا سرورها تست شوند.
5. روی سرور مورد نظر بزنید تا اتصال برقرار شود.

<br>

## 📡 ساب‌لینک‌ها

ساب‌لینک‌های داخلی برنامه:

| نام | کاربرد |
|---|---|
| UcAmnezia | سرورهای AmneziaWG |
| CafeWhale | ساب‌لینک CafeWhale |
| UCX | ساب‌لینک UCX |
| UcOvpn | سرورهای OpenVPN |
| UCsstp | سرورهای SSTP |
| Tor | بریج‌های Tor (وب‌تانل، obfs4، vanilla، snowflake، meek_lite) |
| Psiphon | سرورهای Psiphon (هر ۶ ساعت بروزرسانی می‌شود) |
| UcPlus | سرورهای OpenConnect و سایر پروتکل‌های UC Plus |

- ساب‌لینک‌های اضافه‌شده توسط خود کاربر هم پشتیبانی می‌شوند.
- اگر ساب‌لینکی خط خراب داشته باشد، خط‌های خراب نادیده گرفته می‌شوند و بقیه‌ی سرورها بروزرسانی می‌شوند.
- اگر تعداد سرورهای معتبر کمتر از نصف لیست قبلی باشد، لیست قبلی حفظ می‌شود تا با تغییر فرمت، کل لیست پاک نشود.
- آدرس ساب‌لینک داخل ساب‌لینک، به‌عنوان یک سرور نشان داده نمی‌شود؛ کانفیگ‌های داخل آن اضافه می‌شوند.

<br>

## 📡 پروتکل‌ها و فرمت‌ها

| پروتکل | موتور | نوع لینک |
|---|---|---|
| AmneziaWG | AmneziaWG | لینک AWG |
| Psiphon | psiphon-tunnel-core | `psiphon://` |
| Tor | Tor (بریج‌ها) | بریج‌های ساب‌لینک Tor |
| SSTP | SSTP bridge | `host:port:user:pass:CC` |
| OpenConnect | libopenconnect (AnyConnect) | `OPENCONNECT:server:port:user:pass:authgroup:certpin:name:country` |
| OpenVPN | OpenVPN | `udp\|tcp:CC:base64` |
| Shadowsocks | sing-box | `ss://` |
| VLESS | sing-box / Xray | `vless://` |
| Hysteria 2 | sing-box | `hy2://` |

ترتیب نمایش پروتکل‌ها در منو: All Protocols، AWG، Psiphon، Tor، SSTP، OpenConnect، OpenVPN، Shadowsocks، VLESS، Hysteria 2.

## 🔐 OpenConnect

- هر خط ساب‌لینک به این شکل است: `OPENCONNECT:server:port:user:pass:authgroup:certpin:name:country`. فیلدهای خالی، مقدار خالی هستند.
- موتور **libopenconnect** (نسخه 9.21) همراه OpenSSL و libxml2 ساخته می‌شود؛ اسکریپت آن `android/native/build-openconnect.sh` است.
- تونل را خود موتور باز می‌کند؛ بنابراین برای این نوع سرور، sing-box و hev شروع نمی‌شوند.

<br>

## 🕊️ Psiphon

- **Psiphon Auto:** یک ردیف خودکار است. پینگ آن برابر کمترین پینگ بین کشورهای Psiphon است.
- **کشورها:** هر کشور با نام کامل نمایش داده می‌شود.
- **پینگ:** برای هر کشور یک تونل آزمایشی کوتاه ساخته می‌شود و زمان رسیدن تا برقراری تونل ثبت می‌شود. فهرست سرورها یک بار دریافت و برای همه‌ی تست‌ها استفاده می‌شود. تست‌ها موازی‌اند.
- **اسکنر IP** (دکمه **Scan** روی ردیف Psiphon):
  - **Connection Protocol:** Auto، Conduit relays، CDN Fronting یا Direct connection.
  - **CDN edge IPs** و **CDN SNI hostnames** برای CDN Fronting.
  - **IP ranges to scan** با بازه‌های پیش‌فرض Akamai، و **Manual IPs to scan**.
  - دکمه‌های **اسکن بازه‌ها**، **اسکن دستی** و **توقف**.
  - **Beast Mode** همیشه روشن است: روی هر سرور همه‌ی پروتکل‌ها امتحان می‌شوند.

<br>

## 🧅 Tor

- **Tor Auto:** یک ردیف خودکار است که مثل بقیه‌ی کشورها بر اساس پینگ در میان کشورهای Tor قرار می‌گیرد. پینگ آن برابر کمترین پینگ کشورهای Tor است و زیرمجموعه ندارد.
- بریج‌ها از ساب‌لینک Tor خوانده می‌شوند. برنامه برای هر نوع بریج محدودیتی ندارد؛ سقف‌ها در ساخت ساب‌لینک اعمال می‌شوند.

<br>

## 🔍 نحوه پینگ

- سرورهای Psiphon و Tor با تونل آزمایشی تست می‌شوند و زمان رسیدن تا برقراری تونل ثبت می‌شود.
- سرورهای دیگر با TCP تست می‌شوند؛ آدرس‌های IP به‌صورت گروهی تست می‌شوند تا سریع‌تر شود.
- پینگ روی سرورها فیلتر پروتکل فعال را رعایت می‌کند.
- سرورهایی که پاسخ ندادند، پینگ آن‌ها خالی یا `--` نمایش داده می‌شود.

<br>

## 🔔 نوار وضعیت

- آیکون نوار وضعیت گوشی یک «UC» ساده است. اندروید همیشه آیکون‌های نوار وضعیت را سفید نمایش می‌دهد؛ رنگ سبز فسفری روی نوتیفیکیشن دیده می‌شود.
- در صفحه‌ی ورود، آیکون اصلی برنامه بدون انیمیشن نمایش داده می‌شود.

<br>

## 🛠️ ساخت از سورس

همه‌چیز در **GitHub Actions** ساخته می‌شود؛ نیازی به ساخت محلی نیست.

### آپلود و انتشار

1. فایل `UC-Plus-source.zip` را در ریشه ریپو آپلود کنید.
2. workflow زیپ را باز می‌کند، فایل‌ها را جایگزین می‌کند، موتورهای native را می‌سازد و APKهای امضاشده را می‌سازد.
3. هر بیلد یک ریلیز جدید با تگ `v<نسخه>-build.<شماره اجرا>` می‌سازد و آن را **Latest** علامت می‌زند.
4. lint روی بیلد اجرا می‌شود (فقط گزارش): سطح API هر بخش با minSdk مقایسه و گزارش `NewApi` در خلاصه‌ی اجرا و فایل `lint-report` نوشته می‌شود.

### Workflowها

| فایل | کار | زمان اجرا |
|---|---|---|
| `uc-plus-build.yml` | ساخت APK، امضا و انتشار ریلیز | با هر push روی شاخه‌ی اصلی |
| `uc-plus-psiphon.yml` | ساخت `psiphon.txt` از فهرست رسمی Psiphon (شاخه‌ی `psiphon`) | هر ۶ ساعت (دقیقه‌ی ۴۷) |
| `uc-plus-bridges.yml` | ساخت `tor.txt` و `bridges.json` از بریج‌های Tor (شاخه‌ی `bridges`) | هر ۶ ساعت (دقیقه‌ی ۱۷) |

### کلید امضا

کلید امضا در Secret گیت‌هاب با نام `KEYSTORE_BASE64` نگهداری می‌شود. اگر این Secret وجود نداشته باشد، workflow یک بار کلید می‌سازد و آن را ذخیره می‌کند. کلید هیچ‌وقت داخل ریپو قرار نمی‌گیرد.

| Secret | کاربرد |
|---|---|
| `WORKFLOW_TOKEN` | توکن دسترسی برای ذخیره کلید و push کردن تغییرات |
| `KEYSTORE_BASE64` | کلید امضا (base64) |
| `KEYSTORE_PASSWORD` | رمز کلید امضا |

> [!WARNING]
> کلید امضا و رمز آن را جای امن نگه دارید. اگر کلید از دست برود، نسخه‌های جدید روی نسخه قبلی نصب نمی‌شوند.

### ساخت محلی

برای ساخت محلی به JDK 17، Android SDK، NDK r28 و Go نیاز است. `libpsiphon.so` فقط با **Go 1.22** ساخته می‌شود و Go نسخه‌های دیگر آن را نمی‌سازد.

```bash
cd android
PSIPHON_GO=/path/to/go1.22/bin/go bash tools/build_native_libs.sh   # به ANDROID_NDK_HOME نیاز دارد
./gradlew assembleRelease
```

<br>

## 🧩 ساختار پروژه

| مسیر | وظیفه |
|---|---|
| `android/app` | برنامه اصلی، ناوبری، ساب‌لینک‌ها و تنظیمات |
| `android/ui` | صفحه‌ها و کامپوننت‌های Compose، منوی پروتکل‌ها و پاپ‌آپ اسکن Psiphon |
| `android/core/vpn` | سرویس VPN، تونل، پراکسی، Psiphon و Tor |
| `android/core/config` | ساخت کانفیگ sing-box و Xray، تجزیه‌ی لینک‌ها و برنامه‌ریز Tor |
| `android/core/engine` | مدیریت موتورهای native |
| `android/tools` | اسکریپت‌های ساخت موتورهای native |
| `tools/` | اسکریپت‌های جمع‌آوری Psiphon و بریج‌های Tor |
| `.github/workflows` | ساخت، انتشار و جمع‌آوری خودکار |

<br>

## 🙏 تشکر

UC Plus بر پایه پروژه [Lumen](https://github.com/krambovic/Lumen) ساخته شده که از [zapret-kvn](https://github.com/youtubediscord/zapret-kvn) مشتق شده است. موتورهای استفاده‌شده:

- [sing-box-extended](https://github.com/shtorm-7/sing-box-extended)
- [Xray-core](https://github.com/XTLS/Xray-core)
- [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)
- [psiphon-tunnel-core](https://github.com/Psiphon-Labs/psiphon-tunnel-core)

<br>

## 📜 مجوز

جزئیات مجوز و اعلان‌های کپی‌رایت در فایل‌های [LICENSE](LICENSE) و [NOTICE.md](NOTICE.md) آمده است.

</div>
