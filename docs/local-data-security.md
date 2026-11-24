# Local health-data security

Ringo-Star is an academic Android prototype. The safeguards below are designed
for local privacy, not for medical-device certification or a claim of full
regulatory compliance.

## Data stored on the device

| Data | Storage | Protection |
| --- | --- | --- |
| Profile (including salted PIN hash) | Room / `ringo_health_encrypted.db` | SQLCipher AES-encrypted database; random passphrase wrapped with Android Keystore AES-GCM |
| RDF knowledge graph / questionnaires | Private `sensitive/kg.ttl.enc` | Android Keystore AES-256-GCM (authenticated ciphertext) |
| Generated reports | Private `sensitive/report.pdf.enc` | Android Keystore AES-256-GCM until export |
| LLM model weights | App-scoped external Downloads | Optional build-pinned SHA-256 verified after download and before inference; not encrypted |
| Failed PIN attempt counter | Private application preferences | Persistent throttling; no PIN stored |

Private files and the wrapped database key are under `getNoBackupFilesDir()`.
Cloud and device-to-device transfer are excluded through manifest and extraction
rules. No health records are transmitted to the local model-distribution server.

## PIN and session handling

- Four-digit PINs are hashed using salted PBKDF2-HMAC-SHA256 (150,000 iterations).
  Earlier salted SHA-256 hashes are verified for backward compatibility and
  upgraded on successful sign-in.
- Repeated failed attempts trigger escalating delays starting after five
  failures, capped at fifteen minutes; the failure counter survives process
  restarts. The code uses the device clock, so lockout is **not** a tamper-proof
  defense against someone who fully controls the device.
- Health-data activities set `FLAG_SECURE` to restrict screenshots/recordings.
- The application requires the PIN again after it leaves the foreground. A
  two-minute grace window permits an explicitly initiated system PDF export
  picker to return without prematurely invalidating the operation.

## Existing installation migration

- When the former Room database `database` exists, its single profile is read
  with the original Room schema and imported into the new encrypted database.
  The previous database is deleted **only after** a successful import.
- When an original `files/kg.ttl` exists, it is parsed and saved as an encrypted
  `kg.ttl.enc`. The old plaintext graph is deleted after the new file is stored.
- Any failed operation is surfaced instead of silently erasing records. SQLCipher
  and Keystore failures require recovery/debugging; uninstalling or clearing app
  data destroys the associated cryptographic keys.
- "Delete all the data" closes/removes the encrypted SQLite database, deletes
  encrypted health files and the wrapped passphrase, and invalidates the Android
  Keystore encryption key. This provides cryptographic erasure for app-private
  encrypted content, subject to OS/filesystem guarantees.
- Old plaintext exported `report.pdf` files in shared Documents are outside the
  app-private storage and remain accessible until the user deletes them manually.
  Filesystem or flash-storage remnants from pre-encryption installations cannot
  be promised to have been securely erased.

## Profile and knowledge-graph consistency

Profile changes are prepared on a background executor and first committed to
Room, then serialized to the encrypted RDF file. On a graph write failure,
the app attempts to restore the earlier Room profile. Cross-store commits are
not truly atomic: an app crash after the Room write can leave a temporary
difference. Before generating a report, the profile fields and current
weight/smoking values in RDF are reconciled against Room while questionnaire
history is preserved. A crash may leave an extra historical observation, so
this is best-effort recovery, not a durable distributed transaction.

## Model transfer integrity

Downloads are first written to temporary `.bin.part` files. Successful
DownloadManager completion is required, followed by an optional SHA-256 match
against a hash compiled into the APK; only then is the file renamed to `.bin`.
A missing pin means a nonempty file and successful transfer can be checked,
but no cryptographic authenticity is established. Debug HTTP should only be
used on a trusted local network; release downloads require HTTPS.

## Exported PDF behavior

The report is first generated as a private authenticated encrypted file. On the
success screen, **Export PDF** opens Android's document picker. The app decrypts
only after the user chooses a target URI and writes the PDF there. The exported
copy is plaintext and may be accessible to other apps or cloud services chosen
by the user. Canceling export leaves the encrypted report in private storage.

## Threat model

The protections address loss of casual access to app files, accidental cloud
backup, unintended PDF publication, background-app access and simple online
PIN guessing. They do **not** stop a compromised OS, an attacker controlling a
rooted device, runtime memory inspection, screen photography, malicious
accessibility services, or redistribution of user-exported PDFs. Android Keystore
keys are not portable across devices; backup/restore of ciphertext without keys
cannot recover encrypted records.
