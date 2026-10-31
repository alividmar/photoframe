package si.photoframe

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
 * Prikazuje slike ene za drugo.
 * Naslednjo sliko najprej naloziva v pomnilnik in sele nato zacne prehod. Tako se prehod vedno
 * zgodi v celoti (na pocasnih napravah se slika prej pojavila sele po koncu prehoda, kar je bilo videti kot rez).
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
    var index by remember(ordered) { mutableIntStateOf(0) }

    LaunchedEffect(ordered, intervalSec, widthPx, heightPx, scale) {
        if (ordered.size > 1) {
            while (true) {
                delay(intervalSec * 1000L)
                val next = (index + 1) % ordered.size
                context.imageLoader.execute(request(ordered[next]))   // pocakaj, da je slika pripravljena
                index = next
            }
        }
    }

    AnimatedContent(
        targetState = ordered.getOrNull(index),
        modifier = modifier,
        transitionSpec = {
            when {
                fadeMs <= 0 -> fadeIn(tween(0)) togetherWith fadeOut(tween(0))
                // Stara slika najprej ugasne do crnine, nova se nato prizge
                fadeThroughBlack -> fadeIn(tween(fadeMs / 2, delayMillis = fadeMs / 2)) togetherWith
                    fadeOut(tween(fadeMs / 2))
                // Navaden preliv: eno slika ugaša, druga se prizge hkrati
                else -> fadeIn(tween(fadeMs)) togetherWith fadeOut(tween(fadeMs))
            }
        },
        label = "slideshow",
    ) { uri ->
        if (uri != null) {
            val req = remember(uri, widthPx, heightPx, scale) { request(uri) }
            AsyncImage(
                model = req,
                contentDescription = null,
                contentScale = when (imageScale) {
                    ImageScale.FIT -> ContentScale.Fit     // daljsa stranica do roba, ostalo crno
                    ImageScale.FILL -> ContentScale.Crop
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
