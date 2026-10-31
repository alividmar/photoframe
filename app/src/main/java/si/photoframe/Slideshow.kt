package si.photoframe

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay

/** Prikazuje slike ene za drugo z mehkim prehodom. */
@Composable
fun Slideshow(
    photos: List<Uri>,
    intervalSec: Int,
    shuffle: Boolean,
    cropToFill: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val ordered = remember(photos, shuffle) { if (shuffle) photos.shuffled() else photos }
    var index by remember(ordered) { mutableIntStateOf(0) }

    LaunchedEffect(ordered, intervalSec) {
        if (ordered.size > 1) {
            while (true) {
                delay(intervalSec * 1000L)
                index = (index + 1) % ordered.size
            }
        }
    }

    Crossfade(
        targetState = ordered.getOrNull(index),
        animationSpec = tween(durationMillis = 1000),
        modifier = modifier,
        label = "slideshow",
    ) { uri ->
        if (uri != null) {
            AsyncImage(
                model = ImageRequest.Builder(context).data(uri).build(),
                contentDescription = null,
                contentScale = if (cropToFill) ContentScale.Crop else ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
