package com.mehdi.analogclock

import android.content.Context
import android.os.Handler
import android.os.Looper
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Diagnostic-only direct HTTPS tests for IRIMO.
 * Uses Android's normal TLS certificate validation; no trust-all or certificate bypass.
 */
object IrimoConnectionTest {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun run(context: Context, onUpdate: (String) -> Unit) {
        executor.execute {
            val report = StringBuilder()
            fun log(s: String) {
                report.append(s).append("\n\n")
                val snapshot = report.toString()
                main.post { onUpdate(snapshot) }
            }

            log("شروع آزمایش مستقیم IRIMO\nزمان گوشی: ${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date())}")

            // Control endpoints: compare the phone's trusted TLS path with services
            // that have previously worked in the user's TGJU widget.
            val tgju = request("https://call1.tgju.org/ajax.json", browserHeaders = true)
            log(format("کنترل ۱) منبع TGJU (اتصال مقایسه‌ای)", tgju))

            val tsetmc = request("https://cdn.tsetmc.com/api/ClosingPrice/GetClosingPrice", browserHeaders = true)
            log(format("کنترل ۲) دامنه TSETMC (اتصال مقایسه‌ای)", tsetmc))

            val base = "https://www.irimo.ir/far/index.php"
            val first = request(base, browserHeaders = false)
            log(format("۱) HTTPS ساده (بدون هدر مرورگر)", first))

            val browser = request(base, browserHeaders = true)
            log(format("۲) HTTPS با هدر شبیه Chrome و Referer", browser))

            val http = request("http://www.irimo.ir/far/index.php", browserHeaders = true)
            log(format("۳) HTTP (فقط برای تشخیص تغییر مسیر به HTTPS)", http))

            val alternate = request("https://irimo.ir/far/index.php", browserHeaders = true)
            log(format("۴) دامنه بدون www", alternate))

            // Capture the server's presented certificate chain while still delegating
            // trust decisions to Android's normal system trust manager. An untrusted
            // chain is recorded for diagnosis and then rejected; it is never accepted.
            val certificateTrace = requestWithCertificateTrace("https://www.irimo.ir/far/index.php")
            log(certificateTrace)

            if (browser.code in 200..299 && browser.body.isNotBlank()) {
                val id = findTehranId(browser.body)
                if (id != null) {
                    val detail = request(
                        "https://www.irimo.ir/far/index.php?module=web_directory&wd_id=701&id=$id",
                        browserHeaders = true
                    )
                    val visibility = Regex("دید\\s*افقی").containsMatchIn(detail.body)
                    log(format("۵) صفحه جزئیات تهران (شناسه $id)", detail) +
                        "وجود عبارت «دید افقی»: ${if (visibility) "بله" else "خیر"}\n" +
                        "نشانه صفحه هواشناسی: ${if (detail.body.contains("divCurrentWeather")) "یافت شد" else "یافت نشد"}")
                } else {
                    log("۵) صفحه جزئیات تهران\nشناسه تهران از صفحه اصلی پیدا نشد؛ متن صفحه برای بررسی در گزارش بالا خلاصه نشده است.")
                }
            } else {
                log("۵) صفحه جزئیات تهران اجرا نشد؛ دریافت صفحه اصلی موفق نبود.")
            }

            log("پایان آزمایش\nنکته: این آزمایش با اتصال بومی اندروید انجام می‌شود؛ محدودیت CORS مرورگر وب در این روش وجود ندارد. اعتبارسنجی گواهی SSL خاموش نشده است.")
        }
    }

    private data class Result(
        val code: Int,
        val finalUrl: String,
        val contentType: String,
        val bytes: Int,
        val elapsedMs: Long,
        val body: String,
        val error: String?
    )

    private fun request(url: String, browserHeaders: Boolean): Result {
        val start = System.currentTimeMillis()
        var conn: HttpURLConnection? = null
        return try {
            conn = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json;q=0.9,*/*;q=0.8")
                if (browserHeaders) {
                    setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36")
                    setRequestProperty("Accept-Language", "fa-IR,fa;q=0.9,en;q=0.8")
                    setRequestProperty("Referer", "https://www.irimo.ir/far/index.php")
                    setRequestProperty("Cache-Control", "no-cache")
                } else {
                    setRequestProperty("User-Agent", "AnalogClockWidget-IRIMO-Test/1.0")
                }
            }
            val code = conn.responseCode
            val stream = if (code in 200..399) conn.inputStream else conn.errorStream
            val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText().take(2_000_000) } ?: ""
            Result(code, conn.url.toString(), conn.contentType ?: "نامشخص", body.toByteArray(Charsets.UTF_8).size,
                System.currentTimeMillis() - start, body, null)
        } catch (e: Exception) {
            Result(-1, url, "—", 0, System.currentTimeMillis() - start, "",
                "${e.javaClass.simpleName}: ${e.message ?: "بدون جزئیات"}")
        } finally {
            conn?.disconnect()
        }
    }


    private fun requestWithCertificateTrace(url: String): String {
        val lines = StringBuilder()
        val captured = mutableListOf<java.security.cert.X509Certificate>()
        return try {
            val tmf = javax.net.ssl.TrustManagerFactory.getInstance(
                javax.net.ssl.TrustManagerFactory.getDefaultAlgorithm()
            )
            tmf.init(null as java.security.KeyStore?)
            val systemTrust = tmf.trustManagers.filterIsInstance<javax.net.ssl.X509TrustManager>().first()
            val tracingTrust = object : javax.net.ssl.X509TrustManager {
                override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> =
                    systemTrust.acceptedIssuers

                override fun checkClientTrusted(
                    chain: Array<java.security.cert.X509Certificate>, authType: String
                ) = systemTrust.checkClientTrusted(chain, authType)

                override fun checkServerTrusted(
                    chain: Array<java.security.cert.X509Certificate>, authType: String
                ) {
                    captured.clear()
                    captured.addAll(chain)
                    systemTrust.checkServerTrusted(chain, authType)
                }
            }
            val ssl = javax.net.ssl.SSLContext.getInstance("TLS")
            ssl.init(null, arrayOf(tracingTrust), null)
            val connection = (URL(url).openConnection() as javax.net.ssl.HttpsURLConnection).apply {
                sslSocketFactory = ssl.socketFactory
                connectTimeout = 15000
                readTimeout = 15000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                setRequestProperty("Accept", "text/html,application/xhtml+xml,*/*;q=0.8")
            }
            try {
                val code = connection.responseCode
                lines.append("۵) تشخیص زنجیره گواهی IRIMO\\nHTTP: ").append(code)
            } finally {
                connection.disconnect()
            }
            if (captured.isEmpty()) lines.append("\\nهیچ گواهی‌ای از سرور دریافت نشد.")
            else lines.append("\\n").append(formatCertificates(captured))
            lines.append("\\nنتیجه: اعتبارسنجی با گواهی‌های سیستمی اندروید موفق بود.")
            lines.toString()
        } catch (e: Exception) {
            if (captured.isNotEmpty()) {
                lines.append("۵) تشخیص زنجیره گواهی IRIMO\\n")
                    .append(formatCertificates(captured))
                    .append("\\nنتیجه اعتبارسنجی: رد شد؛ ")
            } else {
                lines.append("۵) تشخیص زنجیره گواهی IRIMO\\n")
                    .append("گواهی سرور قبل از تکمیل TLS قابل ثبت نبود؛ ")
            }
            lines.append(e.javaClass.simpleName).append(": ").append(e.message ?: "بدون جزئیات")
            lines.toString()
        }
    }

    private fun formatCertificates(
        chain: List<java.security.cert.X509Certificate>
    ): String = buildString {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        chain.forEachIndexed { index, cert ->
            val fingerprint = digest.digest(cert.encoded).joinToString(":") {
                "%02X".format(java.util.Locale.US, it)
            }
            append("گواهی ").append(index + 1).append("\\n")
            append("Subject: ").append(cert.subjectX500Principal.name).append("\\n")
            append("Issuer: ").append(cert.issuerX500Principal.name).append("\\n")
            append("اعتبار از: ").append(cert.notBefore).append("\\n")
            append("اعتبار تا: ").append(cert.notAfter).append("\\n")
            append("SHA-256: ").append(fingerprint).append("\\n")
            val sans = cert.subjectAlternativeNames
                ?.mapNotNull { item -> item.getOrNull(1)?.toString() }
                ?.joinToString(", ")
            if (!sans.isNullOrBlank()) append("نام‌های دامنه: ").append(sans).append("\\n")
            append("\\n")
        }
    }

    private fun findTehranId(html: String): String? {
        val patterns = listOf(
            Regex("""<a[^>]+href=["'][^"']*[?&]id=(\d+)[^"']*["'][^>]*>\s*(?:<[^>]+>\s*)*تهران"""),
            Regex("""تهران.{0,300}?[?&]id=(\d+)""", RegexOption.DOT_MATCHES_ALL),
            Regex("""[?&]id=(\d+)[^>]{0,200}>[^<]*تهران""")
        )
        return patterns.firstNotNullOfOrNull { it.find(html)?.groupValues?.getOrNull(1) }
    }

    private fun format(title: String, r: Result): String {
        val body = r.body
        val markers = listOf("دید افقی", "وضع هوای", "divCurrentWeather", "تهران")
            .filter { body.contains(it, ignoreCase = true) }
        return buildString {
            append(title).append("\n")
            if (r.error != null) append("خطا: ").append(r.error).append("\n")
            else {
                append("HTTP: ").append(r.code).append("\n")
                append("آدرس نهایی: ").append(r.finalUrl).append("\n")
                append("نوع پاسخ: ").append(r.contentType).append("\n")
                append("حجم پاسخ: ").append(r.bytes).append(" بایت\n")
                append("زمان: ").append(r.elapsedMs).append(" ms\n")
                append("عبارت‌های پیدا شده: ").append(if (markers.isEmpty()) "هیچ‌کدام" else markers.joinToString("، "))
            }
        }
    }
}
