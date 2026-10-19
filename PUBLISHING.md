# Publish 1.1

This package is prepared for the owner to publish manually. It has not been uploaded as a 1.1 GitHub release.

1. Update the source in `renjhsky/RedMagic-Share-KeepOn` using the source archive contents. Do not upload private signing files or build caches.
2. Create a release with tag `v1.1`, title `1.1`, and the text in `RELEASE_NOTES.md`.
3. Attach `RedMagicShareKeepOn-1.1.apk`, `RedMagicShareKeepOn-1.1-Source.zip`, and `SHA256SUMS.txt`. Publish as a normal release to make it the latest version.

The app's update button opens the existing repository's `/releases/latest` page. No token, automated upload or in-app download service is required.

The APK retains the 1.0 signing identity. Source archives deliberately exclude the private keystore; retain your signing backup for future upgrades.
