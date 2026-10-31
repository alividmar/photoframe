package si.photoframe

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.size.Scale
import kotlinx.coroutines.delay

/**
 * Diaprojekcija z lastnim prehodom.
 *
 * - Naslednja slika se najprej naloži v pomnilnik, sele nato se prehod zacne.
 * - Prehod poganja kar casovnik zaslona (withFrameNanos), zato ne zavisi od sistemskih nastavitev
 *   animacij (varcevanje energije, "animator duration scale" ipd. lahko vse animacije izklopijo).
 * - Navaden preliv: stara slika ostane, nova se nanjo prelije.
 * - Prek crnine: stara ugasne do crnine, nova se nato prizge.
 */
@Composable
fun Slideshow(
    photos: List<Uri>,
    intervalSec: Int,
    shuffle: Boolean,
    imageScale: ImageScale,
    fadeMs: Int,
    fadeThroughBlack: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val config = LocalConfiguration.current
    val density = LocalDensity.current.density
    val widthPx = (config.screenWidthDp * density).toInt().coerceAtLeast(1)
    val heightPx = (config.screenHeightDp * density).toInt().coerceAtLeast(1)
    val scale = if (imageScale == ImageScale.FIT) Scale.FIT else Scale.FILL

    // Isti zahtevek za nalaganje vnaprej in za prikaz, da zadene predpomnilnik
    fun request(uri: Uri): ImageRequest =
        ImageRequest.Builder(context).data(uri).size(widthPx, heightPx).scale(scale).build()

    val ordered = remember(photos, shuffle) { if (shuffle) photos.shuffled() else photos }
    var shown by remember(ordered) { mutableStateOf(ordered.firstOrNull()) }
    var previous by remember(ordered) { mutableStateOf<Uri?>(null) }
    var progress by remember(ordered) { mutableFloatStateOf(1f) }

    LaunchedEffect(ordered, intervalSec, widthPx, heightPx, scale, fadeMs) {
        previous = null
        progress = 1f
        val current = shown
        var idx = if (current == null) 0 else ordered.indexOf(current).coerceAtLeast(0)
        while (ordered.size > 1) {
            delay(intervalSec * 1000L)
            idx = (idx + 1) % ordered.size
            val next = ordered[idx]
            context.imageLoader.execute(request(next))   // pocakaj, da je slika pripravljena
            if (fadeMs <= 0) {
                previous = null
                shown = next
                progress = 1f
            } else {
                previous = shown
                shown = next
                progress = 0f
                val start = withFrameNanos { it }
                while (progress < 1f) {
                    val now = withFrameNanos { it }
                    progress = ((now - start) / 1_000_000f / fadeMs).coerceIn(0f, 1f)
                }
                previous = null
            }
        }
    }

    // Spodaj stara slika (ce prehod poteka), zgoraj nova
    val layers = listOfNotNull(previous, shown)
    val hasPrev = layers.size > 1

    Box(modifier.fillMaxSize()) {
        for (u in layers) {
            key(u) {
                val isNew = u == shown
                val req = remember(u, widthPx, heightPx, scale) { request(u) }
                AsyncImage(
                    model = req,
                    contentDescription = null,
                    contentScale = when (imageScale) {
                        ImageScale.FIT -> ContentScale.Fit     // daljsa stranica do roba, ostalo crno
                        ImageScale.FILL -> ContentScale.Crop
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = when {
                                !hasPrev -> 1f
                                fadeThroughBlack ->
                                    if (isNew) ((progress - 0.5f) * 2f).coerceIn(0f, 1f)
                                    else (1f - progress * 2f).coerceIn(0f, 1f)
                                else -> if (isNew) progress else 1f
                            }
                        },
                )
            }
        }
    }
}
