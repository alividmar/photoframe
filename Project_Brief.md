# Project Brief: Foto okvir (Android)

## Cilj
Android aplikacija za tablet: digitalni foto okvir z uro, radiem in alarmom. APK se zgradi na GitHubu.

## Odgovori uporabnika
- Tehnologija: Kotlin (nativni Android), Jetpack Compose
- Najnizja podprta verzija: Android 5.0 (API 21)
- OLED nastavitve: ura se premika po zaslonu na N minut; ikone se premikajo ali skrijejo in pokazejo na dotik
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
3. Radio (4 postaje, uvoz/izvoz M3U/PLS, casovnik izklopa) + vecerna zatemnitev (narejeno v 0.4.0, ni preizkuseno)
4. Alarm (2 loceni alarma: ura, dnevi, ton ali radio, glasnost zbujanja) (narejeno v 0.5.0, ni preizkuseno)
5. NAS (SMB, WebDAV) (narejeno v 0.6.0, ni preizkuseno)
6. Dropbox, Google Drive/Photos, OneDrive
7. Mega

## Pravila
- Nobenih izmisljenih podatkov, API-jev ali povezav; vse preveriti pri uradnih virih.
- Kljuci in gesla nikoli v kodi (GitHub secrets / nastavitve naprave).

## Dodatne zahteve (faza 3)
- Uporabnik lahko doda, uredi in izbrise svoje postaje; uvoz in izvoz v obicajnem formatu (M3U, uvoz tudi PLS).
- Casovnik za samodejni izklop radia v meniju, dosegljiv med predvajanjem.
- V nastavitvah spanja: izklop diaprojekcije in izbira enobarvnega ozadja, svetlost zaslona, svetlost ure in ikon.

## Dodatne zahteve (faza 4)
- Privzeti izklop radia v nastavitvah; ob zagonu radia se nastavi sam, z ikono casovnika ga je mogoce popraviti.
- Po koncu casovnika radia zatemnitev ostane do jutranje ure (izbirno); moznost, da se zaslon ugasne; moznost skrivanja ure ponoci.
- Ikone spodnje vrstice povecane (privzeto 200 %, nastavljivo).
