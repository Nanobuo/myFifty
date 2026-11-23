# Configurare Supabase per myFifty

Tempo richiesto: circa 10 minuti. Il piano gratuito di Supabase basta per una casa.

## 1. Crea il progetto

1. Vai su <https://supabase.com> e accedi (anche con GitHub).
2. **New project** → scegli un nome (es. `myfifty`), una password per il database
   (salvala in un posto sicuro, all'app non serve) e la regione più vicina
   (es. *Central EU (Frankfurt)*).
3. Attendi un paio di minuti che il progetto sia pronto.

## 2. Crea le tabelle

1. Nel menu a sinistra apri **SQL Editor** → **New query**.
2. Copia tutto il contenuto di [`supabase/schema.sql`](../supabase/schema.sql), incollalo e premi **Run**.
3. Deve comparire *Success. No rows returned*.

Lo script crea:

| Oggetto | A cosa serve |
|---|---|
| `profiles` | nome visualizzato di ogni utente (creato in automatico alla registrazione) |
| `households` | le case, ognuna con un codice invito di 8 caratteri |
| `household_members` | chi fa parte di quale casa |
| `expenses` | spese, rimborsi e saldi tra membri (importi in centesimi) |
| `create_household`, `join_household`, `regenerate_invite_code`, `leave_household` | funzioni chiamate dall'app |
| Policy RLS | ogni utente vede e modifica **solo** i dati delle case di cui fa parte |
| Pubblicazione realtime | le modifiche degli altri membri arrivano subito sul telefono |

Lo script si può rilanciare in sicurezza (ad esempio dopo un aggiornamento dell'app): non cancella dati.

## 3. Autenticazione

Apri **Authentication → Sign In / Providers** (nelle versioni precedenti: *Providers → Email*):

- **Email** deve essere abilitato (lo è di default).
- **Confirm email**:
  - *attivo* (default): dopo la registrazione ogni membro riceve un'email e deve cliccare il link prima di
    poter accedere. Il link apre una pagina web: dopo averlo cliccato basta tornare nell'app e fare
    *Accedi*.
  - *disattivato*: si entra subito dopo la registrazione. Comodo per un uso tra coinquilini.

> Il servizio email integrato di Supabase ha un limite di pochi invii all'ora. Se siete in tanti a
> registrarvi insieme, disattiva temporaneamente *Confirm email* oppure configura un SMTP
> personalizzato in **Authentication → Emails → SMTP Settings**.

Facoltativo: in **Authentication → URL Configuration** imposta *Site URL* a una pagina qualsiasi
(es. il link al repository): è la pagina su cui atterra chi clicca il link di conferma o di
reset password.

## 4. Collega l'app

Apri **Project Settings → API** (oppure **Data API** / **API Keys**) e copia:

- **Project URL** – tipo `https://abcdefghijkl.supabase.co`
- **anon public key** (o *publishable key*, `sb_publishable_…`)

> ⚠️ Usa solo la chiave *anon/publishable*. **Mai** la `service_role`/*secret*: aggirerebbe tutte le
> regole di sicurezza.

Hai due possibilità:

**A. Inserirli al primo avvio dell'app** (nessuna configurazione di build). Ogni membro della casa li
inserisce una volta sola nella schermata di benvenuto. Puoi mandarli in chat: la chiave anon è pensata
per stare dentro le app ed è protetta dalle policy RLS.

**B. Includerli nell'APK**, così nessuno deve inserirli:

- in locale, nel file `local.properties` nella radice del progetto:
  ```properties
  supabase.url=https://abcdefghijkl.supabase.co
  supabase.anonKey=eyJhbGciOi...
  ```
- su GitHub Actions: **Settings → Secrets and variables → Actions → New repository secret**, crea
  `SUPABASE_URL` e `SUPABASE_ANON_KEY`. La build della CI li userà in automatico.

## 5. Invita gli altri membri

1. Il primo membro si registra e crea la casa.
2. In **Altro → Casa** trova il codice invito (pulsante *Condividi*).
3. Gli altri installano l'app, si registrano e scelgono **Entra con un codice invito**.

## Verifiche e risoluzione problemi

| Sintomo | Causa probabile |
|---|---|
| "Il database non è configurato" | `schema.sql` non è stato eseguito (punto 2) |
| "Email o password non corretti" subito dopo la registrazione | email non ancora confermata (punto 3) |
| Le spese degli altri compaiono solo trascinando giù per aggiornare | Realtime non attivo: rilancia `schema.sql`, poi in **Database → Publications** controlla che `supabase_realtime` includa `expenses` |
| "Connessione assente o server non raggiungibile" | URL sbagliato o progetto in pausa: i progetti gratuiti vengono sospesi dopo 7 giorni senza utilizzo, si riattivano dalla dashboard |

### Backup

Oltre all'export JSON/CSV dall'app (**Altro → Importa ed esporta**), dalla dashboard puoi scaricare
le tabelle in CSV da **Table Editor**, oppure fare un dump completo con `supabase db dump`.
