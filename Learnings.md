# Learnings

- Izbira mape prek Storage Access Framework (OpenDocumentTree) odpravi potrebo po dovoljenju za pomnilnik.
- Gradle namestimo v GitHub Actions (gradle/actions/setup-gradle), zato v repozitoriju ni gradlew.
- Besedila v uporabniskem vmesniku so zaenkrat zapisana kar v Kotlin kodi (slovensko); pozneje jih je smiselno prestaviti v strings.xml.
- Adaptivne ikone (mipmap-anydpi-v26) delujejo od Androida 8; za Android 5-7 je potreben ohranjen ikon v mipmap-anydpi (layer-list).
- java.time na API < 26 zahteva coreLibraryDesugaring.
- Pred fazami 3-7 preveriti minSdk vsake knjiznice (Media3, SMB/WebDAV, Dropbox, Google, Microsoft, Mega) in delovanje HTTPS na starih napravah.
