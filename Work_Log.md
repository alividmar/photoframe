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

## 2026-10-31 — v0.4.2
- Test v0.4.1: vse deluje, razen Radio Rock (naslov je enak tistemu v aplikaciji; drugega zanesljivega naslova nisem nasel).
- Samodejni izklop po postaji: Station.autoOffMin (nastavitev v urejanju postaje), ob pritisku na postajo servis sam nastavi casovnik. Uvoz/izvoz M3U z atributom x-timer.

## 2026-10-31 — v0.5.0 (faza 4: alarm)
- Test v0.4.2: radio, casovnik in zatemnitev delujejo; po koncu casovnika so se vrnile slike in svetlost (po zasnovi: zatemnitev se je koncala z radiem). Spremenjeno: privzeto ostane zatemnitev do jutranje ure (nightStayUntilMorning).
- Radio Rock: nov naslov https://live.radio.si/Radio1Rock?t=1793477993539 (uporabnik potrdil, da deluje; parameter t je verjetno samo predpomnilniski zeton).
- Dva loceni alarma: AlarmManager.setAlarmClock (brez dovoljenja za natancne alarme), AlarmReceiver (tudi ob zagonu naprave), AlarmService (ton prek MediaPlayer ali radio prek RadioService; zasilni ton, ce radio v 30 s ne zacne; samodejni stop po 30 min), celozaslonski AlarmOverlay, obvestilo s fullScreenIntent.
- Glasnost zbujanja po alarmu: STREAM_ALARM (ton) oziroma STREAM_MUSIC (radio).
- Privzeti izklop radia (defaultTimerMin); izklop po postaji ima prednost; velja samo ob svezem zagonu radia.
- Nocni nacin: ostane do jutra, izbirno ugasne zaslon (odstrani FLAG_KEEP_SCREEN_ON), izbirno skrije uro; alarm nocni nacin konca.
- Spodnja vrstica: povecava ikon (barScale, privzeto 2.0), samodejno omejena glede na sirino zaslona.
- STANJE: ni prevedeno v tem okolju; alarmi niso preizkuseni na napravi.

## 2026-11-01 — v0.5.1
- Test v0.5.0: Radio Capris in Radio Rock ne igrata, v gumbih ni ikon postaj; uporabnik zeli vecje ikone, izklop ure in izklop zaslona med radiem. Vzroka za https napako nisem mogel potrditi (v 0.4.1 je Capris deloval).
- Dodano: gumb "Diagnostika" pri vsaki postaji (StreamTest.kt): odpre tok in ikono z istim odjemalcem, izpise kodo, TLS, izdajatelja certifikata, napako, cas naprave; rezultat je mogoce kopirati.
- Spodnja vrstica: ce pri izbrani velikosti vse ne gre v eno vrstico, se ikone razporedijo v dve (postaje / alarmi in nastavitve) in ostanejo povecane (prej se je velikost samodejno zmanjsala, zato povecava na ozjih zaslonih ni bila vidna).
- Nastavitve v zavihkih (Slike, Ura, Zaslon, Radio, Alarmi, Spanje).
- Novo v Radio: "Ko radio igra: skrij uro" in "Ko radio igra: ugasni zaslon" (ob katerem koli casu, ne le ponoci).

## 2026-11-01 — v0.6.0 (faza 5: NAS)
- Test v0.5.1: vse deluje; zaslon ni ugasnil, ker je bil sistemski cas mirovanja predolg.
- Viri slik v omrezju: SMB (jcifs-ng 2.1.10, SMB 2.02 do 3.1.1, izbirno SMB1) in WebDAV (lastna koda prek OkHttp, PROPFIND, Basic prijava; izbirno zaupanje samopodpisanemu certifikatu).
- Slike z NAS-a se v aplikaciji predstavijo kot nas://<id vira>/<pot>; Coil bralnik NasFetcher jih prebere. Slike iz vseh vklopljenih virov in iz mape v napravi se zdruzijo v eno diaprojekcijo; iskanje poteka v ozadju in se sproti dodaja.
- Gumb "Preizkusi" pri viru izpise stevilo slik ali napako (NasClient.test).
- Opozorila: SMB na Androidu 5 s knjiznico jcifs-ng ni preizkusen; jcifs-ng je pod licenco LGPL; gesla so v pomnilniku aplikacije brez sifriranja; slike z NAS-a se ne shranjujejo na disk (vsakic znova prenos prek omrezja).
- Gradnja: dodan jcifs-ng, slf4j-api, izkljucitve podvojenih META-INF datotek.
- STANJE: ni prevedeno v tem okolju.

## 2026-11-01 — v0.7.0 (faza 6+7: oblak)
- Uporabnik trenutno nima dostopa do lokalnega omrezja, zato prednostno oblak.
- Preverjeno z iskanjem: Dropbox PKCE brez preusmeritve (uporabnik prepise kodo; token_access_type=offline; osvezitev brez skrivnosti); Microsoft koda za napravo (consumers, public client, Files.Read + offline_access); Google Photos Library API od 31. 3. 2025 vrne samo vsebino, ki jo je ustvarila aplikacija (readonly obseg odstranjen) -> Google Photos ni mogoc.
- Novi viri: MEGA (javna povezava mape, lastna implementacija: AES-ECB za kljuce, AES-CBC za atribute, AES-CTR za vsebino), Google Drive (javna mapa + API kljuc), Dropbox (list_folder recursive, download), OneDrive (Graph children, items/{id}/content).
- Predpomnilnik slik z omrezja na disku (privzeto 300 MB, nastavljivo, gumb za ciscenje); velja tudi za NAS.
- Trajni zeton Microsoft se ob rotaciji shrani prek NasRegistry.onUpdate.
- STANJE: ni prevedeno v tem okolju; MEGA kriptografija je napisana po opisu protokola in ni preizkusena na streznikih.

## 2026-11-01 — v0.7.1
- Test v0.7.0: MEGA deluje (javna povezava mape). Zatemnitev po uri ni delovala, ker se je nocni nacin vklopil samo ob zagonu radia; izklop zaslona je deloval le prek sistemskega casa mirovanja.
- Nocni nacin ima zdaj sprozilec: "po urniku" (privzeto; deluje tudi brez radia) ali "ob zagonu radia".
- Pravi izklop zaslona: skrbnik naprave (samo force-lock), DevicePolicyManager.lockNow(). Po urniku se zaslon ugasne po nightRelockSec sekundah brez dotika; ce ga uporabnik ponoci prebudi, se po isti neaktivnosti spet ugasne. Radio igra naprej v servisu v ospredju do izteka casovnika.
- Zbujanje ob jutranji uri prek AlarmManager (RTC_WAKEUP, exact ce dovoljeno) in WakeLock z ACQUIRE_CAUSES_WAKEUP, ker se ob izklopljenem zaslonu notranji casovniki ustavijo. Dovoljenji SCHEDULE_EXACT_ALARM in USE_EXACT_ALARM.
- Brez skrbniskih pravic ostane star nacin (zaslon ugasne po sistemskem casu mirovanja).
- Glasnost ob zatemnitvi se nastavi, ko ponoci zacne igrati radio.
- STANJE: ni prevedeno v tem okolju; zaklep in zbujanje nista preizkusena na napravi.

## 2026-11-01 — v0.7.2
- Vprasanje uporabnika: ali se tablica zjutraj sama odklene (brez PIN-a/vzorca, vedno na polnilcu). Ob izklopu zaslona z lockNow se pri zaklepu "podrsaj" ob zbujanju pokaze zaklenjen zaslon.
- Ob vklopljenem nocnem izklopu zaslona je aplikacija zdaj vedno prikazana tudi nad preprostim zaklepom (setShowWhenLocked/setTurnScreenOn), ob koncu nocnega okna pa se poskusi odkleniti (requestDismissKeyguard / FLAG_DISMISS_KEYGUARD). Pri zaklepu "brez" lockNow samo ugasne zaslon.

## 2026-11-02 — v0.7.3
- Uporabnik: pri namestitvi nove razlicice "App not installed". Najverjetnejsi vzrok: GitHub Actions vsakic ustvari nov razhroscevalni podpisni kljuc, zato se APK z drugim podpisom ne namesti cez obstojeco aplikacijo.
- Dodan stalen kljuc app/debug.keystore (PKCS12, alias androiddebugkey, geslo android; samo za osebno uporabo, ni skrivnost) in signingConfigs.debug. Prva namestitev po tej spremembi se se zahteva odstranitev stare aplikacije; nadaljnje se namestijo cez staro.
- .gitignore: izjema za app/debug.keystore.
