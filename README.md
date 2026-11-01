# Foto okvir

Android aplikacija za tablet: diaprojekcija slik z uro. Radio, alarm in oblacni viri pridejo v naslednjih fazah (glej Project_Brief.md).

## Kaj zdaj dela (faze 1-7, verzija 0.7.1)
- Deluje od Androida 5.0 naprej
- OLED zascita: ura skace po zaslonu, ikone se premikajo, skrijejo po nekaj sekundah in se pokazejo na dotik
- Diaprojekcija slik iz mape na napravi (z podmapami), mehek prehod, nakljucni vrstni red
- Digitalna ali analogna ura cez sliko: barva, ozadje in prosojnost, velikost, polozaj, pisava/obroc
- Zaslon ostane prizgan, celozaslonski nacin
- Radio: 4 ikone v spodnji vrstici (postaje izbereš v Nastavitvah), predvajanje v ozadju, časovnik izklopa, uvoz/izvoz M3U/PLS
- Spanje: med izbranima urama (po urniku ali ob zagonu radia) se zaslon zatemni, diaprojekcija se izklopi in prikaže enobarvno ozadje; po želji se zaslon pravo ugasne (skrbniške pravice), radio pa igra naprej
- Alarmi: dva ločena alarma (ura, dnevi, ton ali radio, glasnost zbujanja); ikoni v spodnji vrstici ju vklopita/izklopita
- Viri v omrežju in oblaku: SMB, WebDAV, MEGA (javna povezava mape), Google Drive (javna mapa + API ključ), Dropbox, OneDrive (Nastavitve → Slike)
- Spodnja vrstica: povečane ikone, gumb za nastavitve

## Kako dobis APK (brez Android Studia)
1. Na github.com ustvari nov repozitorij (npr. `photoframe`).
2. Naloži vse datoteke iz te mape vanj (veja `main`). Pazi, da se naloži tudi skrita mapa `.github`.
3. Odpri zavihek **Actions**. Gradnja `Build APK` se zazene sama. Ce ne, klikni **Run workflow**.
4. Ko je gradnja zelena (traja nekaj minut), odpri tek in na dnu pod **Artifacts** prenesi `photoframe-debug-apk`.
5. Razpakiraj ZIP, APK prenesi na tablet in ga odpri. Android bo morda zahteval dovoljenje "namestitev iz neznanih virov".

To je *debug* APK (podpisan z razhroscevalnim kljucem). Za objavo v trgovini bo pozneje potreben svoj ključ, shranjen v GitHub secrets.

## Prvi zagon
Odpri **Nastavitve** (zobnik spodaj desno) → **Izberi mapo s slikami**.

## Lokalna gradnja (neobvezno)
Android Studio (Koala ali novejsi), odpri to mapo, Run.
