package si.photoframe

import android.content.Context
import okhttp3.OkHttpClient
import okhttp3.Protocol
import java.security.KeyStore
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

/**
 * HTTP odjemalec, ki na starih napravah (Android 5-7) zna preveriti tudi novejse certifikate.
 * Najprej poskusi sistemski seznam zaupanja, ce ta odpove, pa vgrajeni seznam korenskih
 * certifikatov Mozille (res/raw/mozilla_roots.pem, izvor: paket certifi).
 * Uporabljata ga predvajalnik radia in nalaganje ikon.
 */
object SecureHttp {

    @Volatile
    private var cached: OkHttpClient? = null

    fun client(context: Context): OkHttpClient =
        cached ?: synchronized(this) {
            cached ?: build(context.applicationContext).also { cached = it }
        }

    /**
     * Odjemalec, ki sprejme KATERIKOLI certifikat (tudi samopodpisanega). Uporabi se samo za vir,
     * pri katerem je uporabnik to izrecno vklopil (npr. NAS z lastnim certifikatom v domacem omrezju).
     */
    fun insecureClient(context: Context): OkHttpClient {
        val trustAll = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
        }
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trustAll), null) }
        return client(context).newBuilder()
            .sslSocketFactory(ssl.socketFactory, trustAll)
            .hostnameVerifier { _, _ -> true }
            .build()
    }

    private fun build(context: Context): OkHttpClient {
        val system = trustManagerFor(null)
        val extra = runCatching {
            val factory = CertificateFactory.getInstance("X.509")
            val certs = context.resources.openRawResource(R.raw.mozilla_roots).use { factory.generateCertificates(it) }
            val store = KeyStore.getInstance(KeyStore.getDefaultType()).apply { load(null, null) }
            certs.forEachIndexed { i, c -> store.setCertificateEntry("extra$i", c) }
            trustManagerFor(store)
        }.getOrNull()

        val trust: X509TrustManager = if (extra != null) FallbackTrustManager(system, extra) else system
        val ssl = SSLContext.getInstance("TLS").apply { init(null, arrayOf<TrustManager>(trust), null) }

        return OkHttpClient.Builder()
            .sslSocketFactory(ssl.socketFactory, trust)
            .protocols(listOf(Protocol.HTTP_1_1))   // zivi tokovi so najbolj zanesljivi prek HTTP/1.1
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private fun trustManagerFor(store: KeyStore?): X509TrustManager {
        val f = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm())
        f.init(store)
        return f.trustManagers.filterIsInstance<X509TrustManager>().first()
    }

    private class FallbackTrustManager(
        private val system: X509TrustManager,
        private val extra: X509TrustManager,
    ) : X509TrustManager {
        override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) =
            system.checkClientTrusted(chain, authType)

        override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
            try {
                system.checkServerTrusted(chain, authType)
            } catch (e: CertificateException) {
                extra.checkServerTrusted(chain, authType)
            }
        }

        override fun getAcceptedIssuers(): Array<X509Certificate> = system.acceptedIssuers + extra.acceptedIssuers
    }
}
