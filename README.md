# TIDAL Debug Menu Patches

ReVanced patches to enable TIDAL's debug menu. Tested with version 2.184.2.

- `Unlock Debug Menu`
  - Forces the debug menu gate to enabled in `DebugFeatureInteractorDefault`.
- `Export Debug Activity`
  - Ensures `com.tidal.android.debugmenu.DebugMenuActivity` is exported in `AndroidManifest.xml`.

## Patching with ReVanced Manager

1. Download the latest `patches-*.rvp` from Releases.
2. Download the lastest pre-release Manager.
3. Import the `.rvp` file from the Patches tab.
5. Enable patches:
   - `Unlock Debug Menu`
   - `Export Debug Activity`
6. Patch and install.
