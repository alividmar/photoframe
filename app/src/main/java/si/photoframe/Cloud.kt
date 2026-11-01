package si.photoframe

import android.content.Context
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.delay
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

// ======================================================================================
// Predpomnilnik slik z omrezja: slike se prenesejo enkrat, nato jih beremo z diska.
// ======================================================================================
object RemoteCache {
    @Volatile
    var limitMb: Int = 300

    private fun hash(key: String): String =
        MessageDigest.getInstance("SHA-1").digest(key.toByteArray()).joinToString("") { "%02x".format(it) }

    private fun dir(context: Context): File = File(context.cacheDir, "remote").apply { mkdirs() }

    fun stream(context: Context, key: String, loader: () -> InputStream): InputStream {
        val d = dir(context)
        val f = File(d, hash(key))
        if (f.exists() && f.length() > 0) {
            f.setLastModified(System.currentTimeMillis())
            return FileInputStream(f)
        }
        val tmp = File(d, f.name + ".tmp" + Thread.currentThread().id)
        try {
            loader().use { input -> tmp.outputStream().use { out -> input.copyTo(out) } }
            f.delete()
            if (!tmp.renameTo(f)) throw IOException("Predpomnilnik: shranjevanje ni uspelo")
        } finally {
            if (tmp.exists()) tmp.delete()
        }
        trim(d)
        return FileInputStream(f)
    }

    private fun trim(d: File) {
        val files = d.listFiles()?.filter { it.isFile && !it.name.contains(".tmp") } ?: return
        var total = files.sumOf { it.length() }
        val max = limitMb * 1024L * 1024L
        if (total <= max) return
        for (f in files.sortedBy { it.lastModified() }) {
            if (total <= max * 9 / 10) break
            total -= f.length()
            f.delete()
        }
    }

    fun sizeMb(context: Context): Long =
        (dir(context).listFiles()?.sumOf { it.length() } ?: 0L) / (1024 * 1024)

    fun clear(context: Context) {
        dir(context).listFiles()?.forEach { it.delete() }
    }
}

// ======================================================================================
// Pomozne funkcije
// ======================================================================================
private fun urlB64Decode(s: String): ByteArray = Base64.decode(s, Base64.URL_SAFE)
private fun urlB64Encode(b: ByteArray): String = Base64.encodeToString(b, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

private fun errorText(body: String?): String {
    if (body.isNullOrBlank()) return ""
    return runCatching {
        val o = JSONObject(body)
        o.optString("error_summary").ifBlank {
            o.optJSONObject("error")?.optString("message").orEmpty().ifBlank { o.optString("error_description") }
        }
    }.getOrDefault("").take(160)
}

private fun httpFail(code: Int, body: String?): IOException {
    val extra = errorText(body)
    return IOException("HTTP $code" + if (extra.isNotBlank()) ": $extra" else "")
}

private fun isImage(name: String, mime: String?): Boolean =
    mime?.startsWith("image/") == true || NasClient.isImageName(name)

private fun nasUri(srcId: String, vararg segments: String): Uri {
    val b = Uri.Builder().scheme("nas").authority(srcId)
    for (s in segments) b.appendPath(s)
    return b.build()
}

// ======================================================================================
// Prijava: Dropbox (PKCE, ročni vnos kode) in OneDrive (koda za napravo)
// ======================================================================================
object CloudAuth {
    private class Tok(val access: String, val expiresAt: Long)

    private val tokens = ConcurrentHashMap<String, Tok>()

    // ---- Dropbox ----
    fun newVerifier(): String {
        val b = ByteArray(48)
        SecureRandom().nextBytes(b)
        return urlB64Encode(b)
    }

    fun dropboxAuthUrl(appKey: String, verifier: String): String {
        val challenge = urlB64Encode(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()))
        return "https://www.dropbox.com/oauth2/authorize".toHttpUrl().newBuilder()
            .addQueryParameter("client_id", appKey)
            .addQueryParameter("response_type", "code")
            .addQueryParameter("code_challenge", challenge)
            .addQueryParameter("code_challenge_method", "S256")
            .addQueryParameter("token_access_type", "offline")
            .build().toString()
    }

    /** Zamenja kodo, ki jo je uporabnik prepisal iz Dropboxa, za trajni zeton (refresh token). */
    fun dropboxExchange(client: OkHttpClient, appKey: String, code: String, verifier: String): String {
        val form = FormBody.Builder()
            .add("code", code.trim()).add("grant_type", "authorization_code")
            .add("code_verifier", verifier).add("client_id", appKey).build()
        client.newCall(Request.Builder().url("https://api.dropboxapi.com/oauth2/token").post(form).build()).execute().use { r ->
            val text = r.body?.string()
            if (!r.isSuccessful) throw httpFail(r.code, text)
            val rt = JSONObject(text.orEmpty()).optString("refresh_token")
            if (rt.isBlank()) throw IOException("Dropbox ni vrnil trajnega zetona (preveri, da je aplikacija nastavljena za offline dostop)")
            return rt
        }
    }

    // ---- OneDrive (Microsoft) ----
    class DeviceCode(val userCode: String, val verificationUri: String, val deviceCode: String, val interval: Int)

    private const val MS = "https://login.microsoftonline.com/consumers/oauth2/v2.0"
    private const val MS_SCOPE = "Files.Read offline_access"

    fun msDeviceStart(client: OkHttpClient, clientId: String): DeviceCode {
        val form = FormBody.Builder().add("client_id", clientId).add("scope", MS_SCOPE).build()
        client.newCall(Request.Builder().url("$MS/devicecode").post(form).build()).execute().use { r ->
            val text = r.body?.string()
            if (!r.isSuccessful) throw httpFail(r.code, text)
            val o = JSONObject(text.orEmpty())
            return DeviceCode(
                o.getString("user_code"),
                o.optString("verification_uri", "https://microsoft.com/devicelogin"),
                o.getString("device_code"),
                o.optInt("interval", 5),
            )
        }
    }

    /** Ceka, da uporabnik potrdi prijavo v brskalniku; vrne trajni zeton. */
    suspend fun msDevicePoll(client: OkHttpClient, clientId: String, dc: DeviceCode): String {
        var interval = dc.interval.coerceAtLeast(2)
        repeat(180) {
            delay(interval * 1000L)
            val form = FormBody.Builder()
                .add("grant_type", "urn:ietf:params:oauth:grant-type:device_code")
                .add("client_id", clientId).add("device_code", dc.deviceCode).build()
            client.newCall(Request.Builder().url("$MS/token").post(form).build()).execute().use { r ->
                val text = r.body?.string().orEmpty()
                val o = runCatching { JSONObject(text) }.getOrNull()
                if (r.isSuccessful && o != null) {
                    val rt = o.optString("refresh_token")
                    if (rt.isBlank()) throw IOException("Microsoft ni vrnil trajnega zetona")
                    return rt
                }
                when (o?.optString("error")) {
                    "authorization_pending" -> Unit
                    "slow_down" -> interval += 5
                    else -> throw IOException("Prijava ni uspela: " + (o?.optString("error_description")?.take(160) ?: "HTTP ${r.code}"))
                }
            }
        }
        throw IOException("Cas za prijavo je potekel")
    }

    // ---- Dostopni zetoni (kratkotrajni; osvezujemo jih po potrebi) ----
    fun accessToken(client: OkHttpClient, src: PhotoSource): String {
        val cached = tokens[src.id]
        if (cached != null && cached.expiresAt > System.currentTimeMillis() + 60_000L) return cached.access
        if (src.refreshToken.isBlank()) throw IOException("Ni prijave: odpri vir in se prijavi")
        val (url, form) = when (src.type) {
            SourceType.DROPBOX -> "https://api.dropboxapi.com/oauth2/token" to FormBody.Builder()
                .add("grant_type", "refresh_token").add("refresh_token", src.refreshToken).add("client_id", src.clientId).build()
            else -> "$MS/token" to FormBody.Builder()
                .add("grant_type", "refresh_token").add("refresh_token", src.refreshToken)
                .add("client_id", src.clientId).add("scope", MS_SCOPE).build()
        }
        client.newCall(Request.Builder().url(url).post(form).build()).execute().use { r ->
            val text = r.body?.string()
            if (!r.isSuccessful) throw httpFail(r.code, text)
            val o = JSONObject(text.orEmpty())
            val access = o.getString("access_token")
            tokens[src.id] = Tok(access, System.currentTimeMillis() + o.optLong("expires_in", 3600L) * 1000L)
            // Microsoft lahko izda nov trajni zeton; shranimo ga
            val newRefresh = o.optString("refresh_token")
            if (newRefresh.isNotBlank() && newRefresh != src.refreshToken) {
                NasRegistry.onUpdate?.invoke(src.copy(refreshToken = newRefresh))
            }
            return access
        }
    }
}

// ======================================================================================
// Dropbox
// ======================================================================================
object DropboxClient {
    private fun asciiJson(s: String): String = buildString {
        for (c in s) if (c.code in 32..126) append(c) else append("\\u%04x".format(c.code))
    }

    private fun normPath(p: String): String {
        val t = p.trim().trim('/')
        return if (t.isEmpty()) "" else "/$t"
    }

    fun scan(context: Context, src: PhotoSource, limit: Int): List<Uri> {
        val client = SecureHttp.client(context)
        val token = CloudAuth.accessToken(client, src)
        val out = ArrayList<Uri>()
        var url = "https://api.dropboxapi.com/2/files/list_folder"
        var body = JSONObject().put("path", normPath(src.path)).put("recursive", true).put("limit", 2000)
        while (out.size < limit) {
            val rb = Request.Builder().url(url).header("Authorization", "Bearer $token")
                .post(body.toString().toRequestBody("application/json".toMediaType())).build()
            client.newCall(rb).execute().use { r ->
                val text = r.body?.string()
                if (!r.isSuccessful) throw httpFail(r.code, text)
                val o = JSONObject(text.orEmpty())
                val entries = o.getJSONArray("entries")
                for (i in 0 until entries.length()) {
                    val e = entries.getJSONObject(i)
                    if (e.optString(".tag") != "file") continue
                    val name = e.optString("name")
                    if (!NasClient.isImageName(name)) continue
                    val path = e.optString("path_display").trim('/')
                    out.add(nasUri(src.id, *path.split('/').toTypedArray()))
                }
                if (!o.optBoolean("has_more")) return out
                url = "https://api.dropboxapi.com/2/files/list_folder/continue"
                body = JSONObject().put("cursor", o.getString("cursor"))
            }
        }
        return out
    }

    fun open(context: Context, src: PhotoSource, data: Uri): InputStream {
        val client = SecureHttp.client(context)
        val token = CloudAuth.accessToken(client, src)
        val path = "/" + data.pathSegments.joinToString("/")
        val arg = asciiJson(JSONObject().put("path", path).toString())
        val req = Request.Builder().url("https://content.dropboxapi.com/2/files/download")
            .header("Authorization", "Bearer $token").header("Dropbox-API-Arg", arg)
            .post("".toRequestBody(null)).build()
        val r = client.newCall(req).execute()
        if (!r.isSuccessful) {
            val e = httpFail(r.code, r.body?.string())
            r.close()
            throw e
        }
        return r.body!!.byteStream()
    }
}

// ======================================================================================
// OneDrive (Microsoft Graph)
// ======================================================================================
object OneDriveClient {
    private const val GRAPH = "https://graph.microsoft.com/v1.0"

    fun scan(context: Context, src: PhotoSource, limit: Int): List<Uri> {
        val client = SecureHttp.client(context)
        val token = CloudAuth.accessToken(client, src)
        val out = ArrayList<Uri>()

        fun get(url: String): JSONObject {
            val req = Request.Builder().url(url).header("Authorization", "Bearer $token").build()
            client.newCall(req).execute().use { r ->
                val text = r.body?.string()
                if (!r.isSuccessful) throw httpFail(r.code, text)
                return JSONObject(text.orEmpty())
            }
        }

        fun walk(childrenUrl: String, depth: Int) {
            if (depth > 6) return
            var next: String? = childrenUrl
            while (next != null && out.size < limit) {
                val o = get(next)
                val items = o.getJSONArray("value")
                for (i in 0 until items.length()) {
                    if (out.size >= limit) return
                    val it = items.getJSONObject(i)
                    val id = it.getString("id")
                    if (it.has("folder")) {
                        walk("$GRAPH/me/drive/items/${Uri.encode(id)}/children?\$top=200&\$select=id,name,folder,file", depth + 1)
                    } else if (it.has("file")) {
                        val mime = it.optJSONObject("file")?.optString("mimeType")
                        if (isImage(it.optString("name"), mime)) out.add(nasUri(src.id, id))
                    }
                }
                next = if (o.has("@odata.nextLink")) o.getString("@odata.nextLink") else null
            }
        }

        val p = src.path.trim().trim('/')
        val start = if (p.isEmpty()) {
            "$GRAPH/me/drive/root/children?\$top=200&\$select=id,name,folder,file"
        } else {
            "$GRAPH/me/drive/root:/${Uri.encode(p, "/")}:/children?\$top=200&\$select=id,name,folder,file"
        }
        walk(start, 0)
        return out
    }

    fun open(context: Context, src: PhotoSource, data: Uri): InputStream {
        val client = SecureHttp.client(context)
        val token = CloudAuth.accessToken(client, src)
        val id = data.lastPathSegment ?: throw IOException("Neveljaven naslov slike")
        val req = Request.Builder().url("$GRAPH/me/drive/items/${Uri.encode(id)}/content")
            .header("Authorization", "Bearer $token").build()
        val r = client.newCall(req).execute()   // Graph preusmeri na kratkotrajen naslov za prenos
        if (!r.isSuccessful) {
            val e = httpFail(r.code, r.body?.string())
            r.close()
            throw e
        }
        return r.body!!.byteStream()
    }
}

// ======================================================================================
// Google Drive: javno deljena mapa + API kljuc (brez prijave)
// ======================================================================================
object DriveClient {
    private const val API = "https://www.googleapis.com/drive/v3/files"

    fun folderId(address: String): String =
        Regex("folders/([A-Za-z0-9_-]+)").find(address)?.groupValues?.get(1)
            ?: Regex("[?&]id=([A-Za-z0-9_-]+)").find(address)?.groupValues?.get(1)
            ?: address.trim()

    fun scan(context: Context, src: PhotoSource, limit: Int): List<Uri> {
        val client = SecureHttp.client(context)
        if (src.clientId.isBlank()) throw IOException("Manjka API kljuc")
        val out = ArrayList<Uri>()

        fun walk(folder: String, depth: Int) {
            if (depth > 6) return
            var pageToken: String? = null
            do {
                val pt = pageToken
                val url = API.toHttpUrl().newBuilder()
                    .addQueryParameter("q", "'$folder' in parents and trashed = false")
                    .addQueryParameter("fields", "nextPageToken,files(id,name,mimeType)")
                    .addQueryParameter("pageSize", "1000")
                    .addQueryParameter("key", src.clientId)
                    .apply { if (pt != null) addQueryParameter("pageToken", pt) }
                    .build()
                client.newCall(Request.Builder().url(url).build()).execute().use { r ->
                    val text = r.body?.string()
                    if (!r.isSuccessful) throw httpFail(r.code, text)
                    val o = JSONObject(text.orEmpty())
                    val files = o.optJSONArray("files") ?: JSONArray()
                    for (i in 0 until files.length()) {
                        if (out.size >= limit) return
                        val f = files.getJSONObject(i)
                        val mime = f.optString("mimeType")
                        if (mime == "application/vnd.google-apps.folder") walk(f.getString("id"), depth + 1)
                        else if (isImage(f.optString("name"), mime)) out.add(nasUri(src.id, f.getString("id")))
                    }
                    pageToken = if (o.has("nextPageToken")) o.getString("nextPageToken") else null
                }
            } while (pageToken != null && out.size < limit)
        }
        walk(folderId(src.address), 0)
        return out
    }

    fun open(context: Context, src: PhotoSource, data: Uri): InputStream {
        val id = data.lastPathSegment ?: throw IOException("Neveljaven naslov slike")
        val url = "$API/${Uri.encode(id)}".toHttpUrl().newBuilder()
            .addQueryParameter("alt", "media").addQueryParameter("key", src.clientId).build()
        val r = SecureHttp.client(context).newCall(Request.Builder().url(url).build()).execute()
        if (!r.isSuccessful) {
            val e = httpFail(r.code, r.body?.string())
            r.close()
            throw e
        }
        return r.body!!.byteStream()
    }
}

// ======================================================================================
// MEGA: javna povezava do mape (https://mega.nz/folder/<id>#<kljuc>), brez prijave v racun
// ======================================================================================
object MegaClient {
    private class Link(val handle: String, val key: ByteArray)

    private fun parse(address: String): Link {
        val m = Regex("mega\\.nz/folder/([A-Za-z0-9_-]{8})#([A-Za-z0-9_-]+)").find(address)
            ?: Regex("mega\\.nz/#F!([A-Za-z0-9_-]{8})!([A-Za-z0-9_-]+)").find(address)
            ?: throw IOException("Neveljavna povezava (pricakujem https://mega.nz/folder/...#kljuc)")
        val key = urlB64Decode(m.groupValues[2])
        if (key.size != 16) throw IOException("Kljuc v povezavi ni veljaven (to ni povezava do mape?)")
        return Link(m.groupValues[1], key)
    }

    private fun api(client: OkHttpClient, folder: String, payload: String): Any {
        val id = (SecureRandom().nextInt(1_000_000_000))
        val req = Request.Builder().url("https://g.api.mega.co.nz/cs?id=$id&n=$folder")
            .post(payload.toRequestBody("application/json".toMediaType())).build()
        client.newCall(req).execute().use { r ->
            val text = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw IOException("HTTP ${r.code}")
            val v = runCatching { JSONArray(text).get(0) }.getOrElse {
                // Odgovor je lahko tudi samo stevilka napake
                throw IOException("MEGA napaka " + text.trim())
            }
            if (v is Int) throw IOException("MEGA napaka $v" + if (v == -9) " (mapa ne obstaja ali povezava ni vec veljavna)" else "")
            return v
        }
    }

    private fun aesEcbDecrypt(key: ByteArray, data: ByteArray): ByteArray {
        val c = Cipher.getInstance("AES/ECB/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"))
        return c.doFinal(data)
    }

    private fun foldKey(k: ByteArray): ByteArray = ByteArray(16) { (k[it].toInt() xor k[it + 16].toInt()).toByte() }

    fun scan(context: Context, src: PhotoSource, limit: Int): List<Uri> {
        val client = SecureHttp.client(context)
        val link = parse(src.address)
        val resp = api(client, link.handle, "[{\"a\":\"f\",\"c\":1,\"r\":1}]") as JSONObject
        val nodes = resp.getJSONArray("f")
        val out = ArrayList<Uri>()
        for (i in 0 until nodes.length()) {
            if (out.size >= limit) break
            val n = nodes.getJSONObject(i)
            if (n.optInt("t", -1) != 0) continue   // samo datoteke
            runCatching {
                val enc = urlB64Decode(n.getString("k").substringBefore('/').substringAfter(':'))
                val fileKey = aesEcbDecrypt(link.key, enc)
                if (fileKey.size != 32) return@runCatching
                val c = Cipher.getInstance("AES/CBC/NoPadding")
                c.init(Cipher.DECRYPT_MODE, SecretKeySpec(foldKey(fileKey), "AES"), IvParameterSpec(ByteArray(16)))
                val attrs = String(c.doFinal(urlB64Decode(n.getString("a"))), Charsets.UTF_8).trimEnd('\u0000')
                if (!attrs.startsWith("MEGA{")) return@runCatching
                val name = JSONObject(attrs.substring(4)).optString("n")
                if (NasClient.isImageName(name)) {
                    out.add(
                        Uri.Builder().scheme("nas").authority(src.id).appendPath(n.getString("h"))
                            .appendQueryParameter("k", urlB64Encode(fileKey)).build(),
                    )
                }
            }
        }
        return out
    }

    fun open(context: Context, src: PhotoSource, data: Uri): InputStream {
        val client = SecureHttp.client(context)
        val link = parse(src.address)
        val handle = data.lastPathSegment ?: throw IOException("Neveljaven naslov slike")
        val fileKey = urlB64Decode(data.getQueryParameter("k") ?: throw IOException("Manjka kljuc slike"))
        val resp = api(client, link.handle, "[{\"a\":\"g\",\"g\":1,\"n\":\"$handle\"}]") as JSONObject
        val url = resp.getString("g")
        val iv = ByteArray(16)
        System.arraycopy(fileKey, 16, iv, 0, 8)   // nonce = 8 bajtov, nato stevec (8 bajtov, zacne pri 0)
        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(foldKey(fileKey), "AES"), IvParameterSpec(iv))
        val r = client.newCall(Request.Builder().url(url).build()).execute()
        if (!r.isSuccessful) {
            val code = r.code
            r.close()
            throw IOException("HTTP $code")
        }
        return CipherInputStream(r.body!!.byteStream(), cipher)
    }
}
