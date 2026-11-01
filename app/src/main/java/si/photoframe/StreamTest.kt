package si.photoframe

import android.content.Context
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.cert.X509Certificate
import java.util.Date

/**
 * Diagnostika postaje: poskusi odpreti tok (in ikono) z istim HTTP odjemalcem kot predvajalnik
 * in izpise, kaj se je zgodilo (koda, TLS, izdajatelj certifikata, napaka). Rezultat je mogoce kopirati.
 */
object StreamTest {
    suspend fun run(context: Context, st: Station): String = withContext(Dispatchers.IO) {
        val client = SecureHttp.client(context)
        val sb = StringBuilder()
        sb.append("Naprava: Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\n")
        sb.append("Cas naprave: ${Date()}\n\n")
        sb.append(probe(client, "Tok", st.url, true)).append('\n')
        when {
            st.iconUrl.startsWith("http", ignoreCase = true) -> sb.append(probe(client, "Ikona", st.iconUrl, false))
            st.iconUrl.isBlank() -> sb.append("Ikona: ni nastavljena\n")
            else -> sb.append("Ikona: lokalna datoteka\n")
        }
        sb.toString()
    }

    private fun probe(client: OkHttpClient, label: String, url: String, stream: Boolean): String = try {
        client.newCall(Request.Builder().url(url).build()).execute().use { resp ->
            val hs = resp.handshake
            val issuer = (hs?.peerCertificates?.firstOrNull() as? X509Certificate)?.issuerX500Principal?.name
            val bytes = if (stream) resp.body?.byteStream()?.read(ByteArray(8192)) ?: -1 else -1
            buildString {
                append("$label: HTTP ${resp.code} ${resp.message}\n")
                append("  naslov: ${resp.request.url}\n")
                append("  Content-Type: ${resp.header("Content-Type")}\n")
                if (hs != null) append("  TLS: ${hs.tlsVersion} / ${hs.cipherSuite}\n  izdajatelj: $issuer\n")
                if (stream) append("  prebrano bajtov: $bytes\n")
            }
        }
    } catch (e: Exception) {
        var root: Throwable = e
        while (root.cause != null && root.cause !== root) root = root.cause!!
        "$label: NAPAKA ${e.javaClass.simpleName}: ${e.message}\n  vzrok: ${root.javaClass.simpleName}: ${root.message}\n"
    }
}
