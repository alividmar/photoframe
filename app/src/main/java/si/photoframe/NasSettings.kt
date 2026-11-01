package si.photoframe

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
import kotlinx.coroutines.launch

/** Viri slik v omrezju: SMB in WebDAV. Slike se pridruzijo slikam iz mape v napravi. */
@Composable
internal fun NasSettingsSection(s: AppSettings, onChange: Change) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<PhotoSource?>(null) }
    var adding by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    Title("Viri v omrezju (NAS)")
    Text(
        "Slike z NAS-a (SMB ali WebDAV) se pridruzijo slikam iz mape v napravi. " +
            "Gesla so shranjena v pomnilniku aplikacije brez sifriranja.",
        color = Color.LightGray,
        fontSize = 12.sp,
    )

    for (src in s.sources) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(src.name, color = Color.White)
                    val kind = if (src.type == SourceType.SMB) "SMB" else "WebDAV"
                    val where = src.address + (if (src.path.isNotBlank()) "/" + src.path.trim('/') else "")
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
    Button(onClick = { adding = true }) { Text("Dodaj vir (SMB / WebDAV)") }

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

    val a = address.trim()
    val valid = a.isNotEmpty() && (type == SourceType.SMB || a.startsWith("http://", true) || a.startsWith("https://", true))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nov vir" else "Uredi vir") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ChipRow(listOf(SourceType.SMB to "SMB", SourceType.WEBDAV to "WebDAV"), type) { type = it }
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("Ime (npr. NAS doma)") }, singleLine = true,
                )
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
        },
        confirmButton = {
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        PhotoSource(
                            id = id, type = type, name = name.trim().ifBlank { a }, address = a,
                            path = path.trim(), domain = domain.trim(), user = user.trim(), password = password,
                            smb1 = smb1, insecureTls = insecure, enabled = initial?.enabled ?: true,
                        ),
                    )
                },
            ) { Text("Shrani") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Preklici") } },
    )
}
