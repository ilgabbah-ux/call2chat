# Call2Chat: pubblicazione su Google Play

Pacchetto: `com.gabbah.call2chat` · versione 1.0 (1) · Android 10 e successivi · target API 36

## File
- `Call2Chat-1.0.aab`: da caricare nella Play Console.
- `Call2Chat-1.0.apk`: per provarla sul tuo telefono prima di pubblicare.
- `Call2Chat-upload-key.jks`: chiave di caricamento. Da conservare; la password è in Drive "Progetti Claude/Call2Chat/Call2Chat-chiave-password.properties". Con la firma delle app di Google Play, se si perde si può chiedere a Google di sostituirla.

## Passi nella Play Console
1. **Crea app**: nome "Call2Chat", lingua predefinita Italiano (aggiungi Inglese), App, Gratuita.
2. **Prodotto in-app**: Monetizza → Prodotti → Prodotti in-app → crea con ID esatto `call2chat_pro`, nome "Call2Chat Pro", prezzo a tua scelta, Attivo. Si può creare solo dopo aver caricato un AAB in un canale di test.
3. **Test chiuso**: carica l'AAB, aggiungi almeno 12 tester (es. l'EDT) per 14 giorni consecutivi, poi chiedi l'accesso alla produzione.
4. **Informativa privacy**: serve un indirizzo pubblico (testo in `PRIVACY.md`, manca l'email di contatto).
5. **Sicurezza dei dati**: l'app non raccoglie né condivide dati (tutto resta sul telefono). Acquisti gestiti da Google Play.
6. **Accesso alle app**: nessun login richiesto.
7. **Pubblicità**: nessuna.
8. **Categoria**: Comunicazione.

## Scheda dello Store

**Titolo:** Call2Chat: rispondi a chi ti chiama (senza "WhatsApp" nel titolo: Google lo può rifiutare)

**Descrizione breve (80):**
Chi ti chiama e non è in rubrica riceve il tuo messaggio su WhatsApp in un tocco.

**Descrizione completa:**
Ti chiama un numero sconosciuto e non puoi rispondere? Call2Chat ti mostra subito una notifica: un tocco e si apre WhatsApp con il tuo messaggio già scritto, pronto da inviare.

Perfetta per negozi, artigiani, campeggi, B&B, agenti e professionisti: nessun cliente resta senza risposta.

• Solo numeri che non hai in rubrica: amici e parenti non ricevono niente
• Messaggio personalizzato con link, listino, orari ed emoji
• Funziona con WhatsApp e WhatsApp Business
• Orari di attività: solo in negozio, o solo fuori orario
• Pausa per lo stesso numero: non scrivi due volte alla stessa persona
• Numeri esclusi
• Storico delle ultime chiamate, per inviare anche più tardi
• Non blocca le chiamate, non legge la rubrica, non registra niente
• Tutto resta sul tuo telefono: niente account, niente pubblicità

100 messaggi gratis. Poi sblocchi Pro una volta sola, per sempre.

Call2Chat funziona con WhatsApp ma non è affiliata a WhatsApp o Meta.

**Short description (EN):**
Unknown caller? Send them your WhatsApp message with one tap.

## Note tecniche
- Usa il ruolo "Filtro chiamate" di Android (CallScreeningService): Android passa all'app solo i numeri non in rubrica. Nessun permesso su rubrica, registro chiamate o accessibilità.
- Il contatore dei 100 messaggi conta ogni volta che si apre WhatsApp dalla notifica.
- Aggiornamento automatico all'apertura con gli aggiornamenti in-app di Google Play (funziona quando l'app è scaricata da Play).
- Compilare: `./gradlew bundleRelease` (serve `keystore.properties` con i dati della chiave, non nel repo).

## Due versioni dell'app
- **github**: APK in `download/`, si aggiorna da solo leggendo `download/version.json`.
  Per pubblicare un aggiornamento: alza versionCode/versionName, `./gradlew assembleGithubRelease`,
  copia l'APK in `download/Call2Chat.apk` e aggiorna `download/version.json` con lo stesso versionCode.
- **play**: `./gradlew bundlePlayRelease` → `app/build/outputs/bundle/playRelease/app-play-release.aab` da caricare in Play Console.
  Non contiene l'auto-aggiornamento (Google non lo permette): usa gli aggiornamenti in-app di Google Play.
