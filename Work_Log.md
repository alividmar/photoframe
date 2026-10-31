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
