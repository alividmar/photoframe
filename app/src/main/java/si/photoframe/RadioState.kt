package si.photoframe

import kotlinx.coroutines.flow.MutableStateFlow

enum class RadioStatus { STOPPED, LOADING, PLAYING, ERROR }

/** Skupno stanje radia: servis ga zapisuje, uporabniski vmesnik bere. */
object RadioState {
    val station = MutableStateFlow<Station?>(null)
    val status = MutableStateFlow(RadioStatus.STOPPED)
    val timerEndsAt = MutableStateFlow(0L)   // cas v ms (System.currentTimeMillis); 0 = brez casovnika
}
