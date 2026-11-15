<div align="center">

# 🦁 UC Plus

### کلاینت اندروید با پشتیبانی از چند پروتکل و بروزرسانی خودکار

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

**UC Plus** یک کلاینت VPN برای اندروید است که با Kotlin و Jetpack Compose نوشته شده و از موتور **sing-box** و **Xray** استفاده می‌کند. هدف آن این است که کار اتصال ساده باشد.

<br>

## 🚀 امکانات

| | امکان | توضیح |
|:-:|---|---|
| 🔌 | **چند پروتکل** | VLESS، Shadowsocks، Hysteria 2 و پروتکل‌های مبتنی بر OpenVPN و SSTP |
| 📥 | **ساب‌لینک** | ورود گروهی سرورها از لینک اشتراک، با به‌روزرسانی خودکار |
| 📶 | **پینگ هوشمند** | پینگ گروهی با ترتیب اولویت پروتکل، و امکان فیلتر بر اساس حداکثر پینگ |
| ⭐ | **علاقه‌مندی‌ها** | سرورهای مورد علاقه بالای لیست می‌مانند و از حذف خودکار محافظت می‌شوند |
| 🧭 | **مسیریابی** | قوانین مستقیم، پراکسی و مسدود بر اساس دامنه، IP و دسته‌های آماده (ایران، روسیه، چین) |
| 🌐 | **DNS جداگانه** | DNS مستقیم و DNS از طریق پراکسی، هر کدام با سرورهای جدا |
| 🛡️ | **تونل کامل** | VPN سراسری برای کل گوشی یا انتخاب برنامه‌ها |
| 🎨 | **رابط کاربری ساده** | یک ردیف ابزار فشرده، منوی بلند‌فشرده برای هر سرور و پاپ‌آپ پینگ |

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

1. برنامه را باز کنید.
2. دکمه **پینگ** را بزنید تا سرورها تست شوند.
3. روی سرور مورد نظر بزنید تا اتصال برقرار شود.

<br>

## 📡 پروتکل‌ها و فرمت‌ها

| پروتکل | موتور | نوع لینک |
|---|---|---|
| VLESS | sing-box / Xray | `vless://' |
| Shadowsocks | sing-box | `ss://` |
| Hysteria 2 | sing-box | `hy2://` |
| OpenVPN | OpenVPN | `udp\|tcp:CC:base64` |
| SSTP | SSTP bridge | `host:port:user:pass:CC` |

<br>

## 🔍 نحوه پینگ

- سرورها به ترتیب **Amnezia → OpenVPN → SSTP → بقیه** پینگ می‌شوند.
- سرورهای با آدرس IP به‌صورت گروهی تست می‌شوند تا سرعت بیشتر شود.
- پینگ روی همه سرورها فیلتر پروتکل فعال را رعایت می‌کند.
- سرورهایی که پاسخ ندادند، با همان روش سایر پروتکل‌ها نمایش داده می‌شوند.

<br>

## 🛠️ ساخت از سورس

همه‌چیز در **GitHub Actions** ساخته می‌شود؛ نیازی به ساخت محلی نیست.

### آپلود و انتشار

1. فایل `UC-Plus-source.zip` را در ریشه ریپو آپلود کنید.
2. workflow زیپ را باز می‌کند، فایل‌ها را جایگزین می‌کند، موتورهای native را می‌سازد و APKهای امضاشده را می‌سازد.
3. هر بیلد یک ریلیز جدید با تگ `v<نسخه>-build.<شماره اجرا>` می‌سازد و آن را **Latest** علامت می‌زند.

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

برای ساخت محلی به JDK 17، Android SDK، NDK r28 و Go 1.27.3 نیاز است:

```bash
cd android
bash tools/build_native_libs.sh     # به ANDROID_NDK_HOME نیاز دارد
./gradlew assembleRelease
```

<br>

## 🧩 ساختار پروژه

| مسیر | وظیفه |
|---|---|
| `android/app` | برنامه اصلی، ناوبری و تنظیمات |
| `android/ui` | صفحه‌ها و کامپوننت‌های Compose |
| `android/core/vpn` | سرویس VPN، تونل و پراکسی |
| `android/core/config` | ساخت کانفیگ sing-box و Xray |
| `android/core/engine` | مدیریت موتورهای native |
| `android/tools` | اسکریپت‌های ساخت موتورهای native |
| `.github/workflows` | ساخت و انتشار خودکار |

<br>

## 🙏 تشکر

UC Plus بر پایه پروژه [Lumen](https://github.com/krambovic/Lumen) ساخته شده که از [zapret-kvn](https://github.com/youtubediscord/zapret-kvn) مشتق شده است. موتورهای استفاده‌شده:

- [sing-box-extended](https://github.com/shtorm-7/sing-box-extended)
- [Xray-core](https://github.com/XTLS/Xray-core)
- [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)

<br>

## 📜 مجوز

جزئیات مجوز و اعلان‌های کپی‌رایت در فایل‌های [LICENSE](LICENSE) و [NOTICE.md](NOTICE.md) آمده است.

</div>

---

<div align="center">

### 💬 ارتباط و پشتیبانی

برای دریافت نسخه‌های جدید، آموزش‌ها و خبرهای پروژه به کانال تلگرام ما بپیوندید

[![Join](https://img.shields.io/badge/Join-@UCIRANIR-26A5E4?style=for-the-badge&logo=telegram&logoColor=white)](https://t.me/UCIRANIR)

**ساخته‌شده با ❤️ برای آزادی اینترنت**

</div>
