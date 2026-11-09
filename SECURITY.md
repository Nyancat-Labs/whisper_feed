# Security

## Reporting a vulnerability

Please report it privately, not in a public issue:

- **GitHub:** the repository's *Security* tab → *Report a vulnerability*, or
- **Email:** [hej@nyancatlabs.com](mailto:hej@nyancatlabs.com), with
  "Security" in the subject.

Say what you found, how to reproduce it, and which version and store
(Google Play, F-Droid or GitHub) you have. You'll get a reply within a week.
Please give us a reasonable time to ship a fix before publishing details.

## Supported versions

Only the latest release gets security fixes. Whisper is the same code on
Google Play, F-Droid and GitHub Releases.

## What Whisper does to stay safe

- No plain HTTP: every connection is HTTPS, checked against the phone's
  own certificate store.
- No accounts with us, no analytics, no ads; see [PRIVACY.md](PRIVACY.md).
- A sync account's password is encrypted with a key held in the Android
  Keystore and is left out of backups.
- The in-app browser has file and content access off, blocks mixed content
  and uses Safe Browsing.
- Two parts of the app are open to other apps: the main screen and the
  launcher panel, which admits only launchers.
- The update check on GitHub builds opens the release page. It never
  downloads or installs anything.

## Checking a download

Release APKs on GitHub are signed with this certificate (SHA-256):

`D4:54:52:45:6C:89:FE:AF:2D:D5:A4:90:10:EF:14:93:70:A4:09:B6:3A:15:31:02:7B:D4:CA:A8:2F:88:B1:64`

F-Droid builds from this source and signs with its own key.
