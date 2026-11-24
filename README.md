<div align="center">

# 🦁 UC Plus

### کلاینت VPN برای اندروید با چند پروتکل، ساب‌لینک و بروزرسانی خودکار

[![Telegram](https://img.shields.io/badge/Telegram-@UCIRANIR-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/UCIRANIR)
[![Android](https://img.shields.io/badge/Android-8.0+-3DDC84?style=for-the-badge&logo=android&logoColor=white)](#-نصب)

**📣 کانال رسمی: [@UCIRANIR](https://t.me/UCIRANIR)**

</div>

---

<div dir="rtl">

## ✨ معرفی

**UC Plus** یک کلاینت VPN برای اندروید است که با Kotlin و Jetpack Compose نوشته شده. هدف آن اتصال ساده است: یک ساب‌لینک، یک لیست مرتب و یک دکمه.

<br>

## 🚀 امکانات اصلی

- **چند پروتکل:** AmneziaWG، **OpenConnect**، Psiphon، Tor، SSTP، OpenVPN، Shadowsocks، VLESS و Hysteria 2
- **ساب‌لینک:** ورود گروهی سرورها، با بروزرسانی دستی یا خودکار
- **پینگ:** تست گروهی و موازی سرورها
- **علاقه‌مندی‌ها:** سرورهای مورد علاقه بالای لیست می‌مانند
- **مسیریابی و DNS:** قوانین مستقیم، پراکسی و مسدود بر اساس دامنه، IP یا دسته‌های آماده؛ DNS جداگانه برای مستقیم و پراکسی
- **تونل کامل:** VPN برای کل گوشی یا انتخاب برنامه‌ها (Split Tunneling)
- **Psiphon:** اسکنر IP برای پیدا کردن IPهای سالم CDN
- **Tor:** انتخاب خودکار بهترین کشور بر اساس پینگ

<br>

## 📲 نصب

1. از بخش **[Releases](../../releases)** آخرین نسخه را دانلود کنید.
2. فایل APK مناسب گوشی را انتخاب کنید:
   - `arm64-v8a`: بیشتر گوشی‌های جدید
   - `armeabi-v7a`: گوشی‌های قدیمی ۳۲ بیتی
   - `x86_64`: شبیه‌سازها

> [!NOTE]
> حداقل نسخه‌ی اندروید **۸.۰ (API 26)** است.

<br>

## 🧭 شروع سریع

1. برنامه را باز کنید؛ لیست سرورها از ساب‌لینک‌های داخلی بارگذاری می‌شود.
2. دکمه **بروزرسانی** را بزنید.
3. پروتکل مورد نظر را از منو انتخاب کنید.
4. دکمه **پینگ** را بزنید.
5. روی سرور بزنید تا وصل شوید.

<br>

## 📡 ساب‌لینک‌ها

- ساب‌لینک‌های داخلی برنامه: UcAmnezia، CafeWhale، UCX، UcOvpn، UCsstp، Tor، Psiphon و **UcPlus** (شامل سرورهای OpenConnect).
- ساب‌لینک‌های دلخواه کاربر هم پشتیبانی می‌شوند.
- خط خراب در ساب‌لینک نادیده گرفته می‌شود و بقیه‌ی سرورها بروزرسانی می‌شوند.
- اگر تعداد سرورهای معتبر کمتر از نصف لیست قبلی شود، لیست قبلی حفظ می‌شود تا با تغییر فرمت، کل لیست پاک نشود.
- آدرس ساب‌لینک داخل ساب‌لینک به‌عنوان سرور نمایش داده نمی‌شود.

<br>

## 🔐 OpenConnect

پروتکل **OpenConnect (AnyConnect)** با موتور **libopenconnect 9.21** پشتیبانی می‌شود.

- فرمت خط ساب‌لینک:
  `OPENCONNECT:server:port:user:pass:authgroup:certpin:name:country`
  فیلد خالی یعنی مقدار خالی.
- موتور همراه OpenSSL و libxml2 ساخته می‌شود؛ اسکریپت آن `android/native/build-openconnect.sh` است.
- تونل را خود libopenconnect باز می‌کند، پس برای این سرورها sing-box شروع نمی‌شود.
- آمار سرعت و حجم در نوتیفیکیشن مثل بقیه‌ی پروتکل‌ها نمایش داده می‌شود.

<br>

## 🔗 پروتکل‌ها

| پروتکل | موتور |
|---|---|
| AmneziaWG | AmneziaWG |
| OpenConnect | libopenconnect |
| Psiphon | psiphon-tunnel-core |
| Tor | Tor و بریج‌ها (وب‌تانل، obfs4، vanilla، snowflake، meek_lite) |
| SSTP | SSTP bridge |
| OpenVPN | OpenVPN |
| Shadowsocks / VLESS / Hysteria 2 | sing-box و Xray |

<br>

## 🛠️ ساخت و انتشار

همه‌چیز در **GitHub Actions** ساخته می‌شود؛ ساخت محلی لازم نیست.

- فایل `UC-Plus-source.zip` را در ریشه‌ی ریپو آپلود کنید. workflow فایل‌ها را جایگزین می‌کند، موتورهای native را می‌سازد و APKهای امضاشده را منتشر می‌کند.
- هر بیلد یک ریلیز با تگ `v<نسخه>-build.<شماره اجرا>` می‌سازد.
- workflowهای `uc-plus-psiphon.yml` و `uc-plus-bridges.yml` هر ۶ ساعت فهرست Psiphon و بریج‌های Tor را بروزرسانی می‌کنند.

### کلید امضا

کلید امضا در Secret گیت‌هاب با نام `KEYSTORE_BASE64` نگهداری می‌شود و هیچ‌وقت داخل ریپو قرار نمی‌گیرد. Secretهای لازم: `WORKFLOW_TOKEN`، `KEYSTORE_BASE64` و `KEYSTORE_PASSWORD`.

> [!WARNING]
> اگر کلید امضا از دست برود، نسخه‌های جدید روی نسخه‌ی قبلی نصب نمی‌شوند. کلید و رمز آن را جای امن نگه دارید.

### ساخت محلی

نیاز به JDK 17، Android SDK، NDK r28 و **Go 1.22** (برای `libpsiphon.so`).

```bash
cd android
PSIPHON_GO=/path/to/go1.22/bin/go bash tools/build_native_libs.sh
bash native/build-openconnect.sh
./gradlew assembleRelease
```

<br>

## 🧩 ساختار پروژه

| مسیر | وظیفه |
|---|---|
| `android/app` | برنامه اصلی، ساب‌لینک‌ها و تنظیمات |
| `android/ui` | صفحه‌ها و منوی پروتکل‌ها |
| `android/core/vpn` | سرویس VPN، OpenConnect، Psiphon و Tor |
| `android/core/config` | ساخت کانفیگ و تجزیه‌ی لینک‌ها |
| `android/core/engine` | مدیریت موتورهای native |
| `android/native` | اسکریپت ساخت libopenconnect |
| `tools/` | جمع‌آوری فهرست Psiphon و بریج‌های Tor |

<br>

## 🙏 تشکر و مجوز

UC Plus بر پایه‌ی پروژه‌ی [Lumen](https://github.com/krambovic/Lumen) ساخته شده است. موتورهای استفاده‌شده: [sing-box-extended](https://github.com/shtorm-7/sing-box-extended)، [Xray-core](https://github.com/XTLS/Xray-core)، [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)، [psiphon-tunnel-core](https://github.com/Psiphon-Labs/psiphon-tunnel-core) و [OpenConnect](https://www.infradead.org/openconnect/) (LGPL-2.1).

جزئیات مجوزها و کپی‌رایت در فایل‌های [NOTICE.md](NOTICE.md) و پوشه‌ی `licenses/` آمده است.

</div>
