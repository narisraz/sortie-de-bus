# Sortie de Bus

Jeu original inspiré des mécaniques de « Bus Out » (aucun code ni graphisme repris de l'application).
Touchez un bus : il sort dans le sens de sa flèche si la voie est libre et se gare sur une place d'attente ;
les passagers de sa couleur montent, un bus plein repart. Perdu si toutes les places sont prises et
qu'aucun bus ne convient au prochain passager. Bonus « +1 place » une fois par niveau.

Les trois versions partagent la même logique (même générateur, mêmes niveaux pour un numéro donné) :
les bus sont posés de sorte qu'un ordre de sortie existe toujours, donc chaque niveau a une solution.

## Web
`index.html` : s'ouvre dans n'importe quel navigateur.

## Mac et iPhone (Swift / SwiftUI)
Dossier `apple/`. Le projet Xcode est décrit dans `project.yml` (XcodeGen).
```
brew install xcodegen
cd apple && xcodegen generate && open SortieDeBus.xcodeproj
```
Dans Xcode : choisir « My Mac » ou un iPhone branché, régler l'équipe de signature (Signing & Capabilities), puis Run.

## Android (Kotlin / Jetpack Compose)
Dossier `android/`. Ouvrir le dossier dans Android Studio, ou en ligne de commande :
```
cd android && ./gradlew assembleDebug
adb install app/build/outputs/apk/debug/app-debug.apk
```
La logique (`Game.kt`) n'a aucune dépendance Android et a été testée sur 40 niveaux.
