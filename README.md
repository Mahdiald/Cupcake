# Cupcake-projekt – EK datamatiker 2. semester

Java 17, Javalin 6.7, Thymeleaf, PostgreSQL, JDBC, Maven. Studieprojekt – ikke produktionsklar betaling.

## Start i IntelliJ (Windows)

1. Åbn **File > Open** og vælg denne `CupcakeProjekt`-mappe. Vælg **Load Maven Project**.
2. Start PostgreSQL (evt. via Docker). Opret databasen `cupcakes` i pgAdmin.
3. Åbn `database/schema.sql` i pgAdmin Query Tool mod databasen `cupcakes` og kør scriptet.
4. Standardforbindelsen er `jdbc:postgresql://localhost:5432/cupcakes`, brugernavn `postgres`, adgangskode `postgres`. Hvis dine værdier er anderledes, sæt miljøvariablerne `DB_URL`, `DB_USER`, `DB_PASSWORD` i IntelliJ Run Configuration. Gem ikke rigtige adgangskoder i Git.
5. Kør `dk.ek.cupcake.App` i IntelliJ, eller `mvn compile exec:java` fra terminalen.
6. Åbn http://localhost:7070. Opret konto med email og kodeord (mindst 8 tegn).
7. Tilføj kontosaldo i pgAdmin: `UPDATE users SET balance=200 WHERE email='DIN_EMAIL';`.
8. For admin-demo: opret en separat konto, og kør `UPDATE users SET role='ADMIN' WHERE email='ADMIN_EMAIL';`. Log ud og ind igen, så rollen genindlæses.

## Leverede funktioner

- US1: Sammensæt cupcake, betal med kontosaldo og gem ordre.
- US2: Opret profil, log ind og gem ordrer.
- US3: Se ordrelinjer og samlet pris i kurv.
- US4: Login med email og kodeord, email vises på siderne.
- US5: Administrator kan se alle ordrer.
- US6: Administrator kan se alle kunder samt antal ordrer.
- US7: Fjern ordrelinje fra kurven.
- US8: Administrator kan slette en ordre.

Bemærk: Admin-kundeoversigten viser antal ordrer, ikke en klikbar komplet liste over hver kundes ordrelinjer. Det kan udvides. En rigtig betalingsgateway indgår ikke i opgaven.

## Aflevering

1. Kør og afprøv appen i IntelliJ med en rigtig PostgreSQL-instans.
2. Lav skærmbilleder af forside, kurv, login, adminpanel.
3. Gennemgå `docs/*.mmd` og tegn diagrammerne som billeder i Mermaid Live Editor eller diagrams.net.
4. Skriv rapport efter lærerens skabelon og dokumentér hvilke user stories der faktisk virker.
5. Optag en kort demo på højst 2-3 minutter.
6. Upload kode og `rapport/rapport.pdf` til GitHub og aflever repo-link på Moodle.

## Vigtige afgrænsninger

- Dette er et **første utestet projektudkast**, ikke en færdig verificeret aflevering. Maven/PostgreSQL var ikke tilgængelig i miljøet hvor det blev genereret.
- Mangler: automatisk testsuite, CSRF-beskyttelse, admin-kundeordre-detaljeside, rapport og videodemo.
- Sletning af en betalt ordre refunderer ikke saldo (afklares med underviser, evt. implementeres som særskilt transaktion).
- Til lokal demonstration: brug kun opdigtede konti og adgangskoder.
