package si.photoframe

import android.content.Context
import android.net.Uri
import android.util.Xml
import jcifs.CIFSContext
import jcifs.config.PropertyConfiguration
import jcifs.context.BaseContext
import jcifs.smb.NtlmPasswordAuthenticator
import jcifs.smb.SmbFile
import jcifs.smb.SmbFileInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.IOException
import java.io.InputStream
import java.io.StringReader
import java.util.Properties
import java.util.concurrent.ConcurrentHashMap

enum class SourceType { SMB, WEBDAV, MEGA, GDRIVE, DROPBOX, ONEDRIVE }

/**
 * Vir slik v omrezju. address: SMB = streznik (npr. 192.168.1.10), WebDAV = celoten naslov (https://...).
 * path (samo SMB): skupna raba in mapa, npr. "slike/dopust".
 * Slike iz virov se v aplikaciji predstavijo z naslovi oblike nas://<id vira>/<pot>.
 */
data class PhotoSource(
    val id: String,
    val type: SourceType,
    val name: String,
    val address: String,
    val path: String = "",
    val domain: String = "",
    val user: String = "",
    val password: String = "",
    val smb1: Boolean = false,
    val insecureTls: Boolean = false,
    val enabled: Boolean = true,
    val clientId: String = "",       // Dropbox app key, OneDrive client ID, Google Drive API kljuc
    val refreshToken: String = "",   // trajni zeton po prijavi (Dropbox, OneDrive)
)

object SourceCodec {
    fun encode(list: List<PhotoSource>): String {
        val arr = JSONArray()
        for (s in list) {
            arr.put(
                JSONObject().put("id", s.id).put("type", s.type.name).put("name", s.name).put("address", s.address)
                    .put("path", s.path).put("domain", s.domain).put("user", s.user).put("password", s.password)
                    .put("smb1", s.smb1).put("insecure", s.insecureTls).put("on", s.enabled)
                    .put("client", s.clientId).put("refresh", s.refreshToken),
            )
        }
        return arr.toString()
    }

    fun decode(raw: String): List<PhotoSource> = runCatching {
        val arr = JSONArray(raw)
        val out = ArrayList<PhotoSource>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val type = SourceType.values().firstOrNull { it.name == o.optString("type") } ?: continue
            out.add(
                PhotoSource(
                    id = o.optString("id").ifBlank { newStationId() },
                    type = type,
                    name = o.optString("name"),
                    address = o.optString("address"),
                    path = o.optString("path"),
                    domain = o.optString("domain"),
                    user = o.optString("user"),
                    password = o.optString("password"),
                    smb1 = o.optBoolean("smb1", false),
                    insecureTls = o.optBoolean("insecure", false),
                    enabled = o.optBoolean("on", true),
                    clientId = o.optString("client"),
                    refreshToken = o.optString("refresh"),
                ),
            )
        }
        out
    }.getOrDefault(emptyList())
}

/** Seznam trenutno znanih virov; bralniki slik (Coil) iz njega preberejo prijavne podatke. */
object NasRegistry {
    private val map = ConcurrentHashMap<String, PhotoSource>()

    fun update(list: List<PhotoSource>) {
        map.keys.retainAll(list.map { it.id }.toSet())
        for (s in list) map[s.id] = s
    }

    fun get(id: String): PhotoSource? = map[id]

    /** Klice ga prijava, ko dobi nov trajni zeton; aplikacija ga shrani v nastavitve. */
    @Volatile
    var onUpdate: ((PhotoSource) -> Unit)? = null
}

object NasClient {
    private const val MAX_DEPTH = 6
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "heic")
    private val smbContexts = ConcurrentHashMap<String, CIFSContext>()

    fun isImageName(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in IMAGE_EXT

    /** Poisce vse slike v viru (z mapami do globine 6). */
    suspend fun scan(context: Context, src: PhotoSource, limit: Int = 50_000): List<Uri> =
        withContext(Dispatchers.IO) {
            when (src.type) {
                SourceType.WEBDAV -> scanWebDav(context, src, limit)
                SourceType.SMB -> scanSmb(src, limit)
                SourceType.MEGA -> MegaClient.scan(context, src, limit)
                SourceType.GDRIVE -> DriveClient.scan(context, src, limit)
                SourceType.DROPBOX -> DropboxClient.scan(context, src, limit)
                SourceType.ONEDRIVE -> OneDriveClient.scan(context, src, limit)
            }
        }

    /** Preizkus povezave za uporabnika: izpise stevilo slik ali napako. */
    suspend fun test(context: Context, src: PhotoSource): String = try {
        val t0 = System.currentTimeMillis()
        val list = scan(context, src, 300)
        val more = if (list.size >= 300) "+" else ""
        "Povezava uspela. Najdenih slik: ${list.size}$more (${System.currentTimeMillis() - t0} ms)"
    } catch (e: Throwable) {
        "NAPAKA: " + describe(e)
    }

    fun describe(e: Throwable): String {
        var root: Throwable = e
        while (root.cause != null && root.cause !== root) root = root.cause!!
        val head = "${e.javaClass.simpleName}: ${e.message ?: ""}".trim()
        val tail = if (root !== e) " (vzrok: ${root.javaClass.simpleName}: ${root.message ?: ""})" else ""
        return (head + tail).take(300)
    }

    /** Odpre eno sliko za branje (kliče ga nalagalnik slik). */
    fun open(context: Context, src: PhotoSource, data: Uri): InputStream = when (src.type) {
        SourceType.WEBDAV -> {
            val url = src.address.trim().toHttpUrl().newBuilder().encodedPath(data.encodedPath ?: "/").build()
            val rb = Request.Builder().url(url)
            addAuth(rb, src)
            val resp = davClient(context, src).newCall(rb.build()).execute()
            if (!resp.isSuccessful) {
                val code = resp.code
                resp.close()
                throw IOException("HTTP $code")
            }
            resp.body!!.byteStream()
        }
        SourceType.SMB -> {
            val url = "smb://" + smbHost(src) + "/" + data.pathSegments.joinToString("/")
            SmbFileInputStream(SmbFile(url, smbContext(src)))
        }
        SourceType.MEGA -> MegaClient.open(context, src, data)
        SourceType.GDRIVE -> DriveClient.open(context, src, data)
        SourceType.DROPBOX -> DropboxClient.open(context, src, data)
        SourceType.ONEDRIVE -> OneDriveClient.open(context, src, data)
    }

    // ---------------- WebDAV ----------------

    private fun davClient(context: Context, src: PhotoSource) =
        if (src.insecureTls) SecureHttp.insecureClient(context) else SecureHttp.client(context)

    private fun addAuth(rb: Request.Builder, src: PhotoSource) {
        if (src.user.isNotBlank()) rb.header("Authorization", Credentials.basic(src.user, src.password))
    }

    private class DavEntry(val path: String, val isDir: Boolean, val type: String?)

    private fun scanWebDav(context: Context, src: PhotoSource, limit: Int): List<Uri> {
        val client = davClient(context, src)
        val base = src.address.trim().toHttpUrl()
        val out = ArrayList<Uri>()
        val visited = HashSet<String>()

        fun walk(url: HttpUrl, depth: Int) {
            if (depth > MAX_DEPTH || out.size >= limit) return
            if (!visited.add(Uri.decode(url.encodedPath).trimEnd('/'))) return
            val self = Uri.decode(url.encodedPath).trimEnd('/')
            for (e in propfind(client, url, src)) {
                if (out.size >= limit) return
                if (Uri.decode(e.path).trimEnd('/') == self) continue   // sama mapa je prvi zapis odgovora
                if (e.isDir) {
                    walk(url.newBuilder().encodedPath(e.path).build(), depth + 1)
                } else if (e.type?.startsWith("image/") == true || isImageName(e.path.substringAfterLast('/'))) {
                    out.add(Uri.Builder().scheme("nas").authority(src.id).encodedPath(e.path).build())
                }
            }
        }
        walk(base, 0)
        return out
    }

    private fun propfind(client: okhttp3.OkHttpClient, url: HttpUrl, src: PhotoSource): List<DavEntry> {
        val body = ("<?xml version=\"1.0\"?><d:propfind xmlns:d=\"DAV:\"><d:prop>" +
            "<d:resourcetype/><d:getcontenttype/></d:prop></d:propfind>")
            .toRequestBody("application/xml".toMediaType())
        val rb = Request.Builder().url(url).method("PROPFIND", body).header("Depth", "1")
        addAuth(rb, src)
        client.newCall(rb.build()).execute().use { resp ->
            if (resp.code == 401) throw IOException("Napacno uporabnisko ime ali geslo (HTTP 401)")
            if (!resp.isSuccessful) throw IOException("Streznik je vrnil HTTP ${resp.code}")
            return parseMultistatus(resp.body?.string().orEmpty())
        }
    }

    private fun parseMultistatus(xml: String): List<DavEntry> {
        val out = ArrayList<DavEntry>()
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        p.setInput(StringReader(xml))
        var href: String? = null
        var dir = false
        var type: String? = null
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            if (ev == XmlPullParser.START_TAG) {
                when (p.name) {
                    "response" -> { href = null; dir = false; type = null }
                    "href" -> href = p.nextText().trim()
                    "collection" -> dir = true
                    "getcontenttype" -> type = p.nextText().trim()
                }
            } else if (ev == XmlPullParser.END_TAG && p.name == "response") {
                val h = href
                if (h != null) {
                    var path = if (h.startsWith("http", ignoreCase = true)) java.net.URI(h).rawPath else h
                    if (!path.startsWith("/")) path = "/$path"
                    out.add(DavEntry(path, dir, type))
                }
            }
            ev = p.next()
        }
        return out
    }

    // ---------------- SMB (jcifs-ng) ----------------

    private fun smbHost(src: PhotoSource): String = src.address.trim().removePrefix("smb://").trim('/')

    private fun smbContext(src: PhotoSource): CIFSContext {
        val key = "${src.id}|${src.address}|${src.domain}|${src.user}|${src.password.hashCode()}|${src.smb1}"
        return smbContexts.getOrPut(key) {
            val props = Properties().apply {
                setProperty("jcifs.smb.client.minVersion", if (src.smb1) "SMB1" else "SMB202")
                setProperty("jcifs.smb.client.maxVersion", "SMB311")
                setProperty("jcifs.smb.client.connTimeout", "10000")
                setProperty("jcifs.smb.client.responseTimeout", "20000")
                setProperty("jcifs.smb.client.soTimeout", "20000")
            }
            val base = BaseContext(PropertyConfiguration(props))
            if (src.user.isBlank()) {
                base.withAnonymousCredentials()
            } else {
                base.withCredentials(NtlmPasswordAuthenticator(src.domain.ifBlank { null }, src.user, src.password))
            }
        }
    }

    private fun scanSmb(src: PhotoSource, limit: Int): List<Uri> {
        val ctx = smbContext(src)
        val rootSegs = src.path.replace('\\', '/').split('/').filter { it.isNotBlank() }
        val url = "smb://" + smbHost(src) + "/" + rootSegs.joinToString("/") + (if (rootSegs.isEmpty()) "" else "/")
        val out = ArrayList<Uri>()

        fun walk(dir: SmbFile, segs: List<String>, depth: Int) {
            if (depth > MAX_DEPTH) return
            for (f in dir.listFiles()) {
                if (out.size >= limit) return
                val name = f.name.trimEnd('/')
                if (f.isDirectory) {
                    walk(f, segs + name, depth + 1)
                } else if (isImageName(name)) {
                    val b = Uri.Builder().scheme("nas").authority(src.id)
                    for (seg in segs + name) b.appendPath(seg)
                    out.add(b.build())
                }
            }
        }

        SmbFile(url, ctx).use { root -> walk(root, rootSegs, 0) }
        return out
    }
}
