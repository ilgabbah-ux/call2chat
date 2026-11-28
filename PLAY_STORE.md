# Call2Chat: pubblicazione su Google Play

Pacchetto: `com.gabbah.call2chat` · versione 1.9.4 (15) · Android 10 e successivi · target API 36

## File
- `Call2Chat-1.9.4.aab`: da caricare nella Play Console.
- `Call2Chat-1.9.4.apk`: per provarla sul tuo telefono prima di pubblicare.
- `store/icona-play-512.png`: icona dell'app per la scheda dello Store (512×512, senza angoli arrotondati: li aggiunge Google).
- `Call2Chat-upload-key.jks`: chiave di caricamento. Da conservare; la password è in Drive "Progetti Claude/Call2Chat/Call2Chat-chiave-password.properties". Con la firma delle app di Google Play, se si perde si può chiedere a Google di sostituirla.

## Passi nella Play Console
1. **Crea app**: nome "Call2Chat", lingua predefinita Italiano (aggiungi Inglese), App, Gratuita.
2. **Prodotto in-app**: Monetizza → Prodotti → Prodotti in-app → crea con ID esatto `call2chat_pro`, nome "Call2Chat Pro", prezzo **4,99 €** (lancio: **2,99 €** per le prime settimane), prezzi degli altri Paesi calcolati da Google, Attivo. Il pulsante nell'app mostra da solo il prezzo impostato qui. Si può creare solo dopo aver caricato un AAB in un canale di test.
3. **Test chiuso**: carica l'AAB, aggiungi almeno 12 tester (es. l'EDT) per 14 giorni consecutivi, poi chiedi l'accesso alla produzione.
4. **Informativa privacy**: https://github.com/ilgabbah-ux/call2chat/blob/main/PRIVACY.md · email di contatto: ilgabbah@gmail.com
5. **Sicurezza dei dati**: l'app non raccoglie né condivide dati (tutto resta sul telefono). Acquisti gestiti da Google Play.
6. **Dettagli dello Store**: email dello sviluppatore ilgabbah@gmail.com.
7. **Accesso alle app**: nessun login richiesto.
8. **Pubblicità**: nessuna.
9. **Categoria**: Comunicazione.

## Scheda dello Store

**Titolo:** Call2Chat: rispondi a chi ti chiama (senza "WhatsApp" nel titolo: Google lo può rifiutare)

**Descrizione breve (80):**
Chi ti chiama e non è in rubrica riceve il tuo messaggio su WhatsApp in un tocco.

**Descrizione completa:**
Ti chiama un numero sconosciuto e non puoi rispondere? Call2Chat ti mostra subito una scheda, già durante la chiamata: un tocco e si apre WhatsApp (o SMS, Telegram, WeChat) con il tuo messaggio già scritto, pronto da inviare, anche con foto, video e PDF.

Perfetta per negozi, artigiani, campeggi, B&B, agenti e professionisti: nessun cliente resta senza risposta.

• Solo numeri che non hai in rubrica: amici e parenti non ricevono niente
• Messaggio personalizzato con link, listino, orari ed emoji
• Funziona con WhatsApp, WhatsApp Business, SMS, Telegram e WeChat
• Allega fino a 3 foto, 1 video e il tuo catalogo PDF
• Più messaggi pronti, con variabili come ora e data
• Doppia SIM: scegli su quale numero attivarla e con quale app rispondere
• Statistiche, tema scuro o chiaro, 7 lingue
• Orari di attività: solo in negozio, o solo fuori orario
• Pausa per lo stesso numero: non scrivi due volte alla stessa persona
• Numeri esclusi
• Storico delle ultime chiamate, per inviare anche più tardi
• Non blocca le chiamate, non legge la rubrica, non registra niente
• Tutto resta sul tuo telefono: niente account, niente pubblicità

100 messaggi gratis. Poi sblocchi Pro una volta sola, per sempre: niente abbonamento.

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
