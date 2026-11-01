package si.photoframe

import android.content.Context
import android.net.Uri
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.buffer
import okio.source
import java.io.IOException

/** Coil bralnik za slike z naslovom nas://<id vira>/<pot>: prebere sliko z NAS-a (SMB ali WebDAV). */
class NasFetcher(
    private val data: Uri,
    private val options: Options,
    private val appContext: Context,
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val src = NasRegistry.get(data.authority.orEmpty()) ?: throw IOException("Vir slik ni na voljo")
        val stream = NasClient.open(appContext, src, data)
        SourceResult(
            source = ImageSource(stream.source().buffer(), options.context),
            mimeType = null,
            dataSource = DataSource.NETWORK,
        )
    }

    class Factory(private val appContext: Context) : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            if (data.scheme == "nas") NasFetcher(data, options, appContext) else null
    }
}
