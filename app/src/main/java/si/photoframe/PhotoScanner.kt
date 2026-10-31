package si.photoframe

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Poisce vse slike v mapi, ki jo je uporabnik izbral (vkljucno s podmapami).
 * Uporablja Android "Storage Access Framework", zato ni potrebno posebno dovoljenje za pomnilnik.
 */
object PhotoScanner {
    private const val MAX_DEPTH = 6

    suspend fun scan(context: Context, treeUri: Uri): List<Uri> = withContext(Dispatchers.IO) {
        val root = runCatching { DocumentFile.fromTreeUri(context, treeUri) }.getOrNull()
            ?: return@withContext emptyList()
        val result = ArrayList<Uri>()

        fun walk(dir: DocumentFile, depth: Int) {
            if (depth > MAX_DEPTH) return
            for (file in dir.listFiles()) {
                if (file.isDirectory) {
                    walk(file, depth + 1)
                } else if (file.type?.startsWith("image/") == true) {
                    result.add(file.uri)
                }
            }
        }

        runCatching { walk(root, 0) }
        result
    }
}
