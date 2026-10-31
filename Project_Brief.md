# Project Brief: Foto okvir (Android)

## Cilj
Android aplikacija za tablet: digitalni foto okvir z uro, radiem in alarmom. APK se zgradi na GitHubu.

## Odgovori uporabnika
- Tehnologija: Kotlin (nativni Android), Jetpack Compose
- Naprava: tablet
- Viri slik (vsi v prvi verziji): lokalni spomin, NAS (SMB ali WebDAV), Dropbox, Google Drive/Photos, Mega, OneDrive
- Ura: digitalna ali analogna, prek slik; nastavitve barve, ozadja ure, sloga
- Spodnja vrstica: 4 ikone radijskih postaj (tap = predvajanje), 2 ikoni za alarm; vse ostalo v nastavitvah
- Alarm: zvok/nacin izbere uporabnik v nastavitvah
- Radijske postaje: privzete slovenske postaje, uporabnik jih zamenja v nastavitvah
- Vecerna zatemnitev: ko uporabnik po uri, ki jo izbere sam, zacne predvajati radio, se zaslon zatemni (pomeni, da gre spat)

## Faze
1. Ogrodje + GitHub gradnja APK (narejeno)
2. Diaprojekcija iz lokalne mape + ura (narejeno, ni preizkuseno na napravi)
3. Radio (4 postaje) + vecerna zatemnitev
4. Alarm (2 ikoni, izbira zvoka)
5. NAS (SMB, WebDAV)
6. Dropbox, Google Drive/Photos, OneDrive
7. Mega

## Pravila
- Nobenih izmisljenih podatkov, API-jev ali povezav; vse preveriti pri uradnih virih.
- Kljuci in gesla nikoli v kodi (GitHub secrets / nastavitve naprave).
