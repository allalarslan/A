# Tetris pour Android

Un Tetris classique écrit en Kotlin, sans dépendance externe : le rendu est fait
dans une `View` personnalisée et la logique du jeu est isolée dans `TetrisGame`
(testée par des tests unitaires JVM).

## Fonctionnalités

- Plateau 10 × 20, les 7 tétrominos avec générateur « 7-bag »
- Rotation avec décalages contre les murs (*wall kicks*)
- Pièce fantôme (aperçu de l'atterrissage)
- Aperçu de la pièce suivante et réserve (*hold*)
- Score, lignes, niveau (la vitesse augmente toutes les 10 lignes), record sauvegardé
- Pause automatique quand l'application passe en arrière-plan

## Commandes

| Action | Contrôle |
| --- | --- |
| Déplacer | ◀ / ▶ (maintenir pour répéter) |
| Descente rapide | ▼ (+1 point par ligne) |
| Rotation | ⟳ ou toucher le plateau |
| Chute instantanée | ⤓ (+2 points par ligne) |
| Réserve | HOLD ou toucher la case « Réserve » |
| Pause | Bouton II |

Points par lignes effacées : 100 / 300 / 500 / 800 × niveau.

## Compiler et lancer

Prérequis : JDK 17+ et le SDK Android (API 35).

```bash
./gradlew assembleDebug        # APK dans app/build/outputs/apk/debug/
./gradlew installDebug         # installe sur un appareil/émulateur connecté
./gradlew test                 # tests unitaires de la logique du jeu
```

Ou ouvrez simplement le dossier dans Android Studio.
