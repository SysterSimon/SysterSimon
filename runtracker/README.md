# RunTracker MVP

En liten Android-app för egen löptracking: lokal-first, offline-tålig och med frivillig synkning till Cloudflare D1.

## Vad som finns i v0.1

- GPS-spårning i foreground service, även när skärmen är släckt.
- Energisnål grundprofil: 5 s intervall, minst 5 m rörelse, upp till 15 s batching.
- Filter mot dåliga GPS-fixar och orimliga hopp.
- Lokal Room-databas är primär källa. Ingen nätanslutning krävs under passet.
- Distans, tid och tempo live.
- Veckomål (tryck på veckomålet för att ändra) och månadssumma.
- MapLibre Native + OpenFreeMap Dark, helt utan Google Maps-nyckel.
- Röd spårlinje på mörk karta.
- Knapp för att spara den synliga kartytan som MapLibre offline-region.
- WorkManager-synk till en Cloudflare Worker + D1 när nät finns.
- Synk körs ungefär var 20:e godkända GPS-punkt under ett pass och alltid när passet avslutas.

## Bygg Android-appen

Projektet använder AGP 8.13.2, compile/target SDK 36 och JDK 17. Om repo:t saknar Gradle wrapper kan Android Studio använda/återskapa wrappern; CLI-varianten är exempelvis:

```bash
gradle wrapper --gradle-version 8.13
./gradlew :app:assembleDebug
```

För Cloudflare-synk lägger du följande i `~/.gradle/gradle.properties` eller projektets lokala `gradle.properties` (commit:a inte token):

```properties
SYNC_BASE_URL=https://runtracker-sync.<din-subdomain>.workers.dev
SYNC_TOKEN=ett-langt-slumpat-hemligt-token
```

Utan dessa värden fungerar appen fullt lokalt; synkarbetet gör då ingenting.

## Cloudflare

```bash
cd cloudflare
npm install
npx wrangler login
npx wrangler d1 create runtracker
```

Kopiera D1-id:t till `wrangler.toml`, kör sedan:

```bash
npm run db:migrate
npx wrangler secret put APP_TOKEN
npm run deploy
```

Använd samma token som `SYNC_TOKEN` i Android-bygget.

## Offlinekarta

Själva löpningen behöver aldrig internet. Kartutseendet hämtas från OpenFreeMap när nät finns. Tryck `SPARA KARTA FÖR OFFLINE` innan du går ut för att lagra aktuell synlig kartregion (zoom 8–16) i MapLibres offline-databas. Spårningen fortsätter även om karttiles saknas.

## Integritet och säkerhet

GPS-spår är känsliga personuppgifter. Cloudflare-endpointen skyddas därför med ett bearer-token och token ska inte checkas in. För ett privat eget bygge är detta en rimlig första nivå. Om appen senare distribueras till andra bör varje användare få separat autentisering och servern bör begränsa/rate-limita anrop.

## Nästa lämpliga steg

1. Installera debug-APK på fysisk telefon och spring/gå 2–3 korta testpass.
2. Jämför distansen mot en känd runda eller Garmin/Strava.
3. Justera GPS-filter och intervall efter telefonens beteende och batteriförbrukning.
4. Lägg till historikskärm, personbästa och valfria månads-/årsmål.
5. Lägg till export/import (GPX) så att all lokal data går att flytta utan Cloudflare.
