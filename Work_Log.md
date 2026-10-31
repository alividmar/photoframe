# Work Log

## 2026-10-31 — Faza 1 in 2
- Ustvarjeno ogrodje Gradle (AGP 8.6.1, Kotlin 2.0.20, Compose BOM 2024.09.03, compileSdk 35, minSdk 26).
- GitHub Actions: `build-apk.yml` zgradi debug APK in ga objavi kot artefakt.
- Nastavitve (DataStore), iskanje slik v izbrani mapi (SAF), diaprojekcija (Coil), digitalna in analogna ura, spodnja vrstica, nastavitve.
- Radio in alarm ikoni so zaenkrat samo oznake (obvestilo "pride v fazi 3/4").
- STANJE: koda NI bila prevedena v okolju, kjer je nastala (brez Android SDK). Prvi pravi test je GitHub Actions.

## 2026-10-31 — v0.3.0
- Prva gradnja na GitHub Actions je uspela (sporocil uporabnik).
- minSdk 26 -> 21 (Android 5.0). Dodano core library desugaring (java.time) in ikona za stare verzije (mipmap-anydpi/ic_launcher.xml).
- OLED: ura skace na nakljucen polozaj vsakih N minut (mehek premik), ikone se lahko premikajo, po X sekundah se skrijejo, dotik jih pokaze.
- STANJE: ni prevedeno v tem okolju; preveri GitHub Actions.

## 2026-10-31 — v0.3.1
- Prikaz slik: nov nastavek `imageScale` (FIT privzeto = cela slika, daljsa stranica do roba, ostalo crno; FILL = napolni in obreze). Star kljuc `crop_to_fill` je opuscen, zato se vsi vrnejo na FIT.
- Polozaj ure: vedno prek BiasAlignment; OLED premik je zdaj v okolici izbranega polozaja (prej je ignoriral izbiro). Predogled v nastavitvah kaze izbrani polozaj.

## 2026-10-31 — v0.3.2
- Prehod med slikami: nastavljivo trajanje (0-6 s) in nacin "prek crnine". Zamenjan Crossfade z AnimatedContent.
- Naslednja slika se pred prehodom naloži v pomnilnik (Coil imageLoader.execute), da se prehod vedno prikaže v celoti, tudi na počasnih napravah.
- STANJE: ni prevedeno v tem okolju; preveri GitHub Actions.

## 2026-10-31 — v0.3.3
- Test v0.3.2: gradnja OK (samo opozorila o Node 20 in setup-java v4); prehod med slikami je bil se vedno trenuten; ura je na izbranem polozaju, OLED jo odmakne proti sredini (po zasnovi).
- Prehod: lastna animacija prek withFrameNanos (neodvisna od sistemskih nastavitev animacij), dve plasti slik (stara spodaj, nova zgoraj z alpha).
- Nastavitev "Obseg premika ure" (OLED).
- Workflow: checkout@v6, setup-java@v5, setup-gradle@v5, upload-artifact@v6, runner ubuntu-24.04 (preverjeno v iskanju 2026-10-31). setup-gradle@v6 zahteva sprejem pogojev za komponento gradle-actions-caching, zato ostajamo na v5.

## 2026-10-31 — v0.4.0 (faza 3: radio)
- Media3 ExoPlayer 1.7.1 (Google v primeru uporablja minSdk 21 s to verzijo), HLS modul; servis v ospredju z obvestilom, samodejni ponovni poskusi (max 6).
- Seznam postaj v DataStore (JSON), 4 ikone dodeljive poljubni postaji, uvoz M3U/PLS, izvoz M3U, preizkus postaje.
- Privzeta postaja: samo Val 202 (http://mp3.rtvslo.si/val202, vir: Wikipedia polje Webcast + direktorij postaj). Povezav za Prvi, Ars ipd. nisem nasel v zanesljivem viru, zato ostanejo ikone prazne.
- Casovnik izklopa (15/30/45/60/90 min ali po meri), tece v servisu.
- Spanje: ce radio zacne igrati po izbrani uri (privzeto 22:00-06:00), zaslon se zatemni (svetlost okna), diaprojekcija se izklopi in prikaze se izbrana barva, ura in ikone so zatemnjene.
- usesCleartextTraffic=true (veliko postaj uporablja http).
- STANJE: ni prevedeno v tem okolju; preveri GitHub Actions. Stream ni bil preizkusen z napravo.

## 2026-10-31 — v0.4.1
- Test v0.4.0: radio deluje; https povezave (stream.exit.si/live, radio.svet24.si/Radio1Rock-v-zivo) ne.
- Vzrok ni potrjen. Najverjetnejse: stara naprava ne zaupa novejsim certifikatom ali ne podpira nastavitev TLS. Ukrepi:
  - predvajalnik in nalaganje ikon zdaj uporabljata OkHttp (SecureHttp.kt): najprej sistemski seznam zaupanja, nato vgrajeni Mozilla koreni (res/raw/mozilla_roots.pem iz certifi 2026.02.25, 137 certifikatov, ISRG Root X1 preverjen po SHA-256);
  - ob napaki toast pokaze kodo in vzrok (npr. SSLHandshakeException) za diagnostiko.
- Ikone postaj: polje iconUrl (povezava ali slika iz naprave, kopirana v pomnilnik aplikacije); M3U atribut tvg-logo pri uvozu/izvozu. Privzete postaje: Val 202, Radio Koper, Radio Capris, Radio Rock (naslove in ikone je podal uporabnik; DuckDuckGo povezave sem dekodiral v izvorne).
- Glasnost ob zatemnitvi: sistemska glasnost predvajanja se enkrat nastavi ob vklopu nocnega nacina.
- docs/postaje.m3u: seznam za uvoz v ze nameščeno aplikacijo.
