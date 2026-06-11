# ValoriaFactions

Fork de [SaberFactions](https://github.com/SaberLLC/Saber-Factions) adapté pour le serveur **Valoria** — un serveur Minecraft PVP Faction Semi-Anarchique tournant sur Purpur 1.21.8.

## Modifications apportées

### Modules désactivés
Les modules suivants ont été désactivés via la configuration (`modules.<nom>: false`) pour réduire la charge serveur et supprimer les fonctionnalités non pertinentes au gameplay de Valoria :

| Module | Raison |
|---|---|
| Discord de faction | Aucun besoin gameplay |
| Missions & tributs | Non utilisé sur Valoria |
| Upgrades | Non utilisé sur Valoria |
| CoreX (36 addons) | Suppression du scan de classes au démarrage |
| Dynmap | Non installé sur le serveur |
| bStats | Télémétrie désactivée |
| PayPal | Non lié au gameplay |
| Points & boutique | Module incomplet |
| Coffre de faction | Remplacé par d'autres mécaniques |

### Corrections et améliorations
- **Java 21** — le `pom.xml` ciblait Java 8 avec une API Minecraft 1.21, corrigé
- **api-version** — mis à jour de `1.13` vers `1.21`
- **MVdWPlaceholderAPI** supprimé — API abandonnée, doublon avec PlaceholderAPI
- **AsyncPlayerMap** allégé — stockait des références `Player` chaque seconde inutilement
- **Timer global** — intervalle passé de 4 ticks à 20 ticks (configurable via `performance.timer-update-ticks`)
- **Top 10 puissance** — snapshot recalculé toutes les 100 ticks au lieu d'un tri à chaque requête

### Ajouts Valoria
- **Commande `/fadmin`** avec permission `valoria.fadmin` (OP par défaut) :
  - Gestion : disband, rename, leader, members, kick, join, leave
  - Puissance : set / add / remove
  - Claims : add / remove / clear
  - Économie : money set / add / remove
  - Outils : inspect et logs
- **Placeholders PlaceholderAPI** via l'expansion `valoria` :
  - `%valoria_top_faction_1_name%` à `%valoria_top_faction_10_name%`
  - `%valoria_top_faction_1_power%` à `%valoria_top_faction_10_power%`
  - `%valoria_faction_members%`, `%valoria_faction_online%`, `%valoria_faction_bank%`, `%valoria_faction_claims%`

## Prérequis

- Java 21
- Purpur 1.21.8 (ou Paper/Spigot compatible)
- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/)
- [Vault](https://www.spigotmc.org/resources/vault.34315/) + plugin d'économie

## Compilation

```bash
mvn clean install
```

Le `.jar` compilé se trouve dans `target/ValoriaFactions.jar`.

## Installation

1. Copier `ValoriaFactions.jar` dans le dossier `/plugins` du serveur
2. Redémarrer le serveur
3. Configurer `plugins/Factions/config.yml` selon les besoins

## Licence

Ce projet est un fork de SaberFactions, distribué sous licence [GPL-3.0](LICENSE).  
Voir aussi les licences tierces dans le dossier [`licenses/`](licenses/).
