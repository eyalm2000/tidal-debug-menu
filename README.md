# TIDAL Debug Menu Patches

ReVanced patches to enable TIDAL's debug menu. Tested with version 2.184.2.

- `Unlock Debug Menu`
  - Forces the debug menu gate to enabled in `DebugFeatureInteractorDefault`.
- `Export Debug Activity`
  - Ensures `com.tidal.android.debugmenu.DebugMenuActivity` is exported in `AndroidManifest.xml`.

## Patching with ReVanced Manager

1. Download the latest ReVanced Manager (v2 or later).
2. Add this URL as a source in the Patches tab:

   `https://raw.githubusercontent.com/eyalm2000/tidal-debug-menu/main/patches-bundle.json`

3. Alternative: manually download the latest `patches-*.rvp` from Releases and import it.
4. Enable patches:
   - `Unlock Debug Menu`
   - `Export Debug Activity`
5. Patch and install.

(To use the new player UI, enable the "Player Market UI" feature flag)
