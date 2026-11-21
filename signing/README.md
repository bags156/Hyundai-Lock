# Public signing key

`public-release.p12` signs every build of this app. It is public on purpose: store password
`android`, key alias `hyundailock`, key password `android`. Anyone can rebuild and sign the app
exactly as the releases are signed, and no repository secrets are involved.

What that means: the signature shows a build is compatible with the others, not who made it.
Anyone could sign a modified APK that Android would accept as an update to this app, and an
update can read the app's stored data, including your saved Bluelink login. **Only install APKs
from this repository's Releases or from a build you made yourself.**

To trade that convenience for stronger protection, replace this key with a private one kept in a
GitHub Actions secret. You'd have to uninstall the app once, because Android won't update an app
signed with a different key.

Do not replace this file casually. A different key means every installed copy has to be
uninstalled before the next version will install.
