# User-approved Emrooz artwork

The authoritative, unmodified source is `source/F7F2EA.png`, SHA256
`baa6459571faaf06aa6c727f54b52330dea684fe39584c45c4ed1c7bc42b5ca3`.
It replaces the earlier reconstructed artwork. No generated/redrawn logo is used.

`python tools/generate-branding.py` (Pillow) reproducibly builds the exact
approved cream/navy preview palette: original copper and green lettering in
light mode; identical copper/geometry/alpha with only the wordmark RGB changed
to shaded ivory in dark mode. The original copper dot is untouched.

The three requested launcher densities are48/72/96px;144/192px, round variants
and512px store artwork are also produced. Legacy launcher art is32dp high
within48dp; adaptive art is48dp high within108dp and fits the66dp safe circle.
The app/OS icon night resources follow the device theme. The in-app splash
follows the saved app theme, using a29% logo in the9:16 source poster and Fit
on other screen shapes. Android12+ uses a separate288dp system splash canvas
with a192dp safe circle (1152/768px at4x).

Generated proof, integrity report and portable graphic ZIP are under
`out/branding/`. Outputs are encoded/verified before atomic replacement.
