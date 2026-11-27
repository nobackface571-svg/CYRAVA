# RefusBot n'utilise pas de réflexion : les règles par défaut d'Android suffisent.
# Les services (accessibilité, tuiles) et le FileProvider sont conservés via le manifeste.

# Pas d'obfuscation des noms (retrait et optimisation du code conservés) : le rapport de plantage
# affiché dans l'appli (CrashRecorder) doit rester lisible sans fichier mapping.txt, et le code
# est de toute façon public (GPL-3.0).
-dontobfuscate
