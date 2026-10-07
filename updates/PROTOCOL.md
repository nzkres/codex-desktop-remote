# OTA wire contract v1

Implementation contract, initially no production trust. All three clients use the same envelope:

`{"schema":1,"keyId":"publisher-test-1","payload":"BASE64_UTF8_JSON","signature":"BASE64_SIGNATURE"}`

Signature: RSA (>=3072 bits), PKCS1v1.5 with SHA-256 over the **decoded payload bytes**, not reparsed JSON. Public keys are PEM SubjectPublicKeyInfo. Reject duplicate JSON keys, malformed encodings and unknown envelope fields. No secret or private key in configuration shipped to a client.

Trust document (provisioned at initial installation, never fetched from an untrusted feed):

`{"schema":1,"product":"codex-desktop-remote","channel":"test","keys":{"publisher-test-1":"PEM_PUBLIC_KEY"},"sources":{"android":"https://HOST/desktop-remote-updates/test/android/latest.json","windows":"https://HOST/desktop-remote-updates/test/windows/latest.json","relay":"https://HOST/desktop-remote-updates/test/relay/latest.json"}}`

An empty keys/sources map means OTA is unconfigured, not that verification may be skipped. Stable and test installations use separately provisioned trust. URLs must be HTTPS, no credentials/query/fragment, same origin and directory subtree as the selected source; redirects are rejected. TLS validation stays enabled.

Payload exact common fields: `schema`=1, `product`="codex-desktop-remote", `component`="android"|"windows"|"relay", `platform`="android"|"windows-x64"|"linux-x64", `channel`="test"|"stable", `version` (x.y.z or x.y.z-alpha.N), `sequence` positive integer, `issuedAt`/`expiresAt` UTC epoch seconds (<=31-day validity, at most 300-second future skew), `url`, `bytes` (>0, <=600 MiB), `sha256` lowercase hex, `notes` (<=8192 characters), `compatibility` nonempty string list, `dataSchema`=1, `rollbackCompatible`=true.

Android additionally requires exactly `versionCode` (positive integer), `packageId`="dev.codex.desktopremote.lite", `minSdk` (integer>=26). Its compatibility identifier is `desktop-remote-v1`; Windows uses `desktop-26.930.7945.0`; relay uses `desktop-remote-v1`. Reject unknown/missing payload fields, incompatible targets and stable prereleases. Installation version must increase; persistent ledger prevents lower sequence, same sequence with different payload, same version with different bytes. Re-check signed expiry and package integrity at installation time. Checking a same-version/same-package feed may show up-to-date, never authorizes replacement.

Python API: `updates.security.UpdateError(code)`, `verify(envelope, trust, component, platform, compatibility, installed_version, ledger=None, now=None) -> (payload, new_ledger)`. Ledger for one component: `sequence`, `binding` SHA256 of payload bytes, `versions` version->artifact SHA256. `fetch(source, context=None)` bounds metadata to 64 KiB. `download(payload, destination, progress=None, context=None)` requires new path, exact size/hash, fsync; context only supplies TLS trust for isolated tests, not insecure verification. `unpack(archive, destination, payload)` validates archive hash, path traversal/reparse/symlink/duplicate/bomb limits and returns extracted directory. Archive must contain `package.json` manifest with `schema=1`, `product`, `component`, `platform`, `version`, `files` relative path->SHA256, `dataSchema=1`; all files except package.json must appear exactly. Python `verify_tree(root, expected_component=None, expected_version=None)` verifies every file and returns manifest. No package may contain user data, trust overrides or local configs. Python security code does not switch versions or execute the archive.

Signed Windows/relay archives are immutable slots. Shared engine handles stages, journal, pointer CAS and fail-closed interruption; component drivers alone manage their explicitly owned service lifecycle. Android APK installation is delegated to Android after package/signing-certificate checks. Never call downloaded bytes before signature, platform, archive and manifest checks succeed.
