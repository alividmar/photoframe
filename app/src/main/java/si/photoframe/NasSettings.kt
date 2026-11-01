package si.photoframe

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Viri slik v omrezju: SMB in WebDAV. Slike se pridruzijo slikam iz mape v napravi. */
@Composable
internal fun NasSettingsSection(s: AppSettings, onChange: Change) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<PhotoSource?>(null) }
    var adding by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    Title("Viri v omrezju in oblaku")
    Text(
        "NAS (SMB, WebDAV) in oblak (MEGA, Google Drive, Dropbox, OneDrive); slike se pridruzijo slikam iz mape v napravi. " +
            "Gesla in zetoni so shranjeni v pomnilniku aplikacije brez sifriranja.",
        color = Color.LightGray,
        fontSize = 12.sp,
    )

    for (src in s.sources) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(src.name, color = Color.White)
                    val kind = when (src.type) {
                        SourceType.SMB -> "SMB"
                        SourceType.WEBDAV -> "WebDAV"
                        SourceType.MEGA -> "MEGA"
                        SourceType.GDRIVE -> "Google Drive"
                        SourceType.DROPBOX -> "Dropbox"
                        SourceType.ONEDRIVE -> "OneDrive"
                    }
                    val where = when (src.type) {
                        SourceType.DROPBOX, SourceType.ONEDRIVE -> "/" + src.path.trim('/')
                        SourceType.MEGA -> "javna povezava"
                        else -> src.address + (if (src.path.isNotBlank()) "/" + src.path.trim('/') else "")
                    }
                    Text("$kind: $where", color = Color.Gray, fontSize = 11.sp, maxLines = 1)
                }
                Switch(
                    checked = src.enabled,
                    onCheckedChange = { v ->
                        onChange { cur -> cur.copy(sources = cur.sources.map { if (it.id == src.id) it.copy(enabled = v) else it }) }
                    },
                )
            }
            Row {
                TextButton(onClick = {
                    result = "Preverjam ..."
                    scope.launch { result = NasClient.test(context, src) }
                }) { Text("Preizkusi") }
                TextButton(onClick = { editing = src }) { Text("Uredi") }
                TextButton(onClick = {
                    onChange { cur -> cur.copy(sources = cur.sources.filter { it.id != src.id }) }
                }) { Text("Izbrisi") }
            }
        }
    }
    Button(onClick = { adding = true }) { Text("Dodaj vir") }

    var cacheInfo by remember { mutableStateOf(RemoteCache.sizeMb(context)) }
    SliderRow("Predpomnilnik slik z omrezja", s.cacheMb.toFloat(), 50f..2000f, { "${it.toInt()} MB (zasedeno $cacheInfo MB)" }) { v ->
        onChange { it.copy(cacheMb = v.toInt()) }
    }
    TextButton(onClick = {
        RemoteCache.clear(context)
        cacheInfo = RemoteCache.sizeMb(context)
    }) { Text("Pocisti predpomnilnik") }

    result?.let { text ->
        AlertDialog(
            onDismissRequest = { result = null },
            title = { Text("Preizkus povezave") },
            text = { Column(Modifier.verticalScroll(rememberScrollState())) { SelectionContainer { Text(text, fontSize = 13.sp) } } },
            confirmButton = { TextButton(onClick = { result = null }) { Text("Zapri") } },
        )
    }
    if (adding) {
        SourceDialog(null, { adding = false }) { src ->
            adding = false
            onChange { cur -> cur.copy(sources = cur.sources + src) }
        }
    }
    editing?.let { old ->
        SourceDialog(old, { editing = null }) { src ->
            editing = null
            onChange { cur -> cur.copy(sources = cur.sources.map { if (it.id == old.id) src else it }) }
        }
    }
}

@Composable
private fun SourceDialog(initial: PhotoSource?, onDismiss: () -> Unit, onSave: (PhotoSource) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val id = remember { initial?.id ?: newStationId() }
    var type by remember { mutableStateOf(initial?.type ?: SourceType.SMB) }
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var address by remember { mutableStateOf(initial?.address ?: "") }
    var path by remember { mutableStateOf(initial?.path ?: "") }
    var domain by remember { mutableStateOf(initial?.domain ?: "") }
    var user by remember { mutableStateOf(initial?.user ?: "") }
    var password by remember { mutableStateOf(initial?.password ?: "") }
    var smb1 by remember { mutableStateOf(initial?.smb1 ?: false) }
    var insecure by remember { mutableStateOf(initial?.insecureTls ?: false) }
    var clientId by remember { mutableStateOf(initial?.clientId ?: "") }
    var refresh by remember { mutableStateOf(initial?.refreshToken ?: "") }
    var verifier by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var loginInfo by remember { mutableStateOf<String?>(null) }

    val a = address.trim()
    val cid = clientId.trim()
    val valid = when (type) {
        SourceType.SMB -> a.isNotEmpty()
        SourceType.WEBDAV -> a.startsWith("http://", true) || a.startsWith("https://", true)
        SourceType.MEGA -> a.contains("mega.nz")
        SourceType.GDRIVE -> a.isNotEmpty() && cid.isNotEmpty()
        SourceType.DROPBOX, SourceType.ONEDRIVE -> cid.isNotEmpty() && refresh.isNotEmpty()
    }

    fun openBrowser(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nov vir" else "Uredi vir") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipRow(
                    listOf(
                        SourceType.SMB to "SMB", SourceType.WEBDAV to "WebDAV", SourceType.MEGA to "MEGA",
                        SourceType.GDRIVE to "Google Drive", SourceType.DROPBOX to "Dropbox", SourceType.ONEDRIVE to "OneDrive",
                    ),
                    type,
                ) { type = it; loginInfo = null }
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Ime (npr. Moje slike)") }, singleLine = true,
                )

                when (type) {
                    SourceType.SMB, SourceType.WEBDAV -> {
                        OutlinedTextField(
                            value = address, onValueChange = { address = it },
                            label = { Text(if (type == SourceType.SMB) "Streznik (npr. 192.168.1.10)" else "Naslov (npr. https://nas:5006/foto/)") },
                            singleLine = true,
                        )
                        if (type == SourceType.SMB) {
                            OutlinedTextField(
                                value = path, onValueChange = { path = it },
                                label = { Text("Skupna raba in mapa (npr. slike/dopust)") }, singleLine = true,
                            )
                            OutlinedTextField(
                                value = domain, onValueChange = { domain = it },
                                label = { Text("Domena (neobvezno)") }, singleLine = true,
                            )
                        }
                        OutlinedTextField(
                            value = user, onValueChange = { user = it },
                            label = { Text("Uporabnik (prazno = gost)") }, singleLine = true,
                        )
                        OutlinedTextField(
                            value = password, onValueChange = { password = it },
                            label = { Text("Geslo") }, singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        )
                        if (type == SourceType.SMB) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Dovoli stari SMB1 (manj varno)", modifier = Modifier.weight(1f))
                                Switch(checked = smb1, onCheckedChange = { smb1 = it })
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Zaupaj tudi samopodpisanemu certifikatu (nevarno)", modifier = Modifier.weight(1f))
                                Switch(checked = insecure, onCheckedChange = { insecure = it })
                            }
                        }
                    }

                    SourceType.MEGA -> {
                        Text(
                            "V aplikaciji MEGA izvozi mapo kot povezavo S KLJUCEM (Deli -> Izvozi). " +
                                "Podprte so samo javne povezave do map; prijava v racun ni podprta.",
                            fontSize = 12.sp,
                        )
                        OutlinedTextField(
                            value = address, onValueChange = { address = it },
                            label = { Text("Povezava (https://mega.nz/folder/...#...)") }, singleLine = true,
                        )
                    }

                    SourceType.GDRIVE -> {
                        Text(
                            "Mapa mora biti deljena kot \"vsak s povezavo\". Potreben je API kljuc (Google Cloud Console: " +
                                "omogoci Google Drive API, ustvari API kljuc).",
                            fontSize = 12.sp,
                        )
                        OutlinedTextField(
                            value = address, onValueChange = { address = it },
                            label = { Text("Povezava do mape ali njen ID") }, singleLine = true,
                        )
                        OutlinedTextField(
                            value = clientId, onValueChange = { clientId = it },
                            label = { Text("API kljuc") }, singleLine = true,
                        )
                    }

                    SourceType.DROPBOX -> {
                        Text(
                            "Na dropbox.com/developers ustvari aplikacijo (Scoped access, Full Dropbox), vklopi dovoljenji " +
                                "files.metadata.read in files.content.read ter prepisi App key.",
                            fontSize = 12.sp,
                        )
                        OutlinedTextField(
                            value = clientId, onValueChange = { clientId = it },
                            label = { Text("App key") }, singleLine = true,
                        )
                        OutlinedTextField(
                            value = path, onValueChange = { path = it },
                            label = { Text("Mapa (npr. /Foto; prazno = vse)") }, singleLine = true,
                        )
                        OutlinedButton(enabled = cid.isNotEmpty(), onClick = {
                            val v = verifier.ifEmpty { CloudAuth.newVerifier().also { verifier = it } }
                            openBrowser(CloudAuth.dropboxAuthUrl(cid, v))
                            loginInfo = "Potrdi dostop v brskalniku, nato prepisi prikazano kodo spodaj."
                        }) { Text("1. Odpri prijavo v brskalniku") }
                        OutlinedTextField(
                            value = code, onValueChange = { code = it },
                            label = { Text("2. Koda iz Dropboxa") }, singleLine = true,
                        )
                        OutlinedButton(enabled = code.isNotBlank() && verifier.isNotEmpty(), onClick = {
                            loginInfo = "Potrjujem ..."
                            scope.launch {
                                loginInfo = try {
                                    refresh = withContext(Dispatchers.IO) {
                                        CloudAuth.dropboxExchange(SecureHttp.client(context), cid, code, verifier)
                                    }
                                    "Prijava uspela."
                                } catch (e: Exception) {
                                    "NAPAKA: " + NasClient.describe(e)
                                }
                            }
                        }) { Text("3. Potrdi kodo") }
                    }

                    SourceType.ONEDRIVE -> {
                        Text(
                            "V Azure portalu (portal.azure.com -> App registrations) ustvari aplikacijo, vklopi \"Allow public client flows\", " +
                                "dodaj dovoljenji Files.Read in offline_access ter prepisi Application (client) ID. Podprti so osebni racuni.",
                            fontSize = 12.sp,
                        )
                        OutlinedTextField(
                            value = clientId, onValueChange = { clientId = it },
                            label = { Text("Application (client) ID") }, singleLine = true,
                        )
                        OutlinedTextField(
                            value = path, onValueChange = { path = it },
                            label = { Text("Mapa (npr. Slike/Dopust; prazno = vse)") }, singleLine = true,
                        )
                        OutlinedButton(enabled = cid.isNotEmpty(), onClick = {
                            loginInfo = "Zacenjam prijavo ..."
                            scope.launch {
                                loginInfo = try {
                                    val http = SecureHttp.client(context)
                                    val dc = withContext(Dispatchers.IO) { CloudAuth.msDeviceStart(http, cid) }
                                    loginInfo = "V brskalniku (" + dc.verificationUri + ") vpisi kodo: " + dc.userCode
                                    openBrowser(dc.verificationUri)
                                    refresh = withContext(Dispatchers.IO) { CloudAuth.msDevicePoll(http, cid, dc) }
                                    "Prijava uspela."
                                } catch (e: Exception) {
                                    "NAPAKA: " + NasClient.describe(e)
                                }
                            }
                        }) { Text("Prijava (koda za napravo)") }
                    }
                }

                if (type == SourceType.DROPBOX || type == SourceType.ONEDRIVE) {
                    Text(
                        if (refresh.isNotEmpty()) "Stanje: prijavljen" else "Stanje: ni prijave",
                        fontSize = 12.sp,
                    )
                }
                loginInfo?.let { SelectionContainer { Text(it, fontSize = 12.sp) } }
            }
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        PhotoSource(
                            id = id, type = type, name = name.trim().ifBlank { if (a.isNotEmpty()) a else type.name },
                            address = a, path = path.trim(), domain = domain.trim(), user = user.trim(), password = password,
                            smb1 = smb1, insecureTls = insecure, enabled = initial?.enabled ?: true,
                            clientId = cid, refreshToken = refresh,
                        ),
                    )
                },
            ) { Text("Shrani") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Preklici") } },
    )
}
