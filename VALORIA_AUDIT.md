# Audit ValoriaFactions

## Perimetre

Audit statique de SaberFactions 4.1.9 : 389 fichiers Java, environ 34 600 lignes,
144 classes de commandes, 55 listeners potentiels, 22 declarations de taches
repetitives et 24 dependances Maven avant nettoyage.

Le format JSON historique des factions, joueurs et claims est conserve. Les
modules retires du profil Valoria restent desactivables plutot que supprimes du
modele serialise, ce qui evite de perdre des donnees lors d'une mise a jour.

## Fonctions hors coeur Faction

| Systeme | Constat | Action Valoria | Impact de la desactivation |
|---|---|---|---|
| Discord de faction | Deux commandes et champs de lien; aucun besoin pour le gameplay de claims | `modules.discord: false` | Deux commandes non enregistrees; donnees Discord historiques conservees |
| MVdWPlaceholderAPI | API abandonnee et artefact Maven introuvable | Hook et dependance supprimes | Build reproductible; PlaceholderAPI devient l'unique moteur |
| Missions et tributs | GUI, listeners de kill/mine/place/fish et rafraichissement periodique | `modules.missions: false` | Aucun listener de progression ni GUI de mission; baisse du traitement d'evenements |
| Upgrades | GUI et listener blocs/entites avec hooks WildStacker/RoseStacker et reflection legacy | `modules.upgrades: false` | Pas de calcul d'upgrade sur les evenements; donnees de niveaux conservees |
| CoreX | 36 addons anti-exploit/gameplay sans rapport direct avec Factions, charges par scan Reflections | `modules.corex: false` | Suppression du scan de classes et de dizaines de listeners potentiels |
| Addons Saber | Scan et extraction de modules additionnels | `modules.addons: false` | Pas de chargement dynamique ni reconstruction tardive des commandes |
| Dynmap | Parcours periodique des factions/claims et gestion de marqueurs | `modules.dynmap: false` | Aucun rafraichissement de carte ni dependance runtime Dynmap |
| bStats | Telemetrie reseau et thread de soumission | `modules.metrics: false` | Aucun trafic ni thread de metriques |
| PayPal | Stockage d'identifiant et commandes non liees au gameplay | `modules.paypal: false` | Commandes masquees; champ conserve pour compatibilite JSON |
| Points/boutique | Points et textes de boutique, boutique incomplete dans cette version | `modules.points: false` | Commandes de points non enregistrees; aucun impact sur Vault |
| Coffre de faction | GUI, listener d'inventaire et audit de chaque mouvement | `modules.faction-chest: false` | Moins de listeners d'inventaire et de copies d'items |
| Drain/invsee/outils legacy | Fonctions de moderation ou de gameplay deja couvertes ailleurs | Modules dedies desactives | Surface de commande reduite et moins de chemins a maintenir |

## Elements obsoletes ou redondants

- Java 8 dans le `pom.xml` avec une API Minecraft 1.21 : incoherent et non
  supporte pour Purpur 1.21.8. Le build cible maintenant Java 21.
- `api-version: 1.13` : remplace par `1.21`.
- Liste de `softdepend` historique (Spout, PermissionsEx, AuthDB, iChat, etc.) :
  reduite aux integrations encore pertinentes.
- MVdWPlaceholderAPI doublonnait PlaceholderAPI et bloquait la resolution Maven.
- `AsyncPlayerMap` stockait chaque seconde des references `Player` et des clones de
  `Location` jamais lus. Les maps et listeners associes ont ete supprimes; la
  tache ne traite plus que les joueurs ayant un titre de territoire en attente.
- Le timer global tournait toutes les 4 ticks pour des expirations basees sur
  `System.currentTimeMillis()`. Il tourne par defaut toutes les 20 ticks, valeur
  configurable par `performance.timer-update-ticks`.
- Le classement de puissance necessite un parcours de toutes les factions puis
  de tous leurs membres. Les placeholders Valoria utilisent un snapshot top 10
  recalcule toutes les 100 ticks, jamais un tri par requete d'hologramme.

## Zones couteuses restantes

### Taches Bukkit

- Le controle du fly parcourt les joueurs concernes toutes les 30 ticks et peut
  chercher les ennemis proches. A conserver seulement si `/f fly` est utilise.
- Les scoreboards personnels se rafraichissent chaque seconde. Leur cout croit
  avec le nombre de joueurs et de lignes dynamiques.
- Les commandes corner/fill/TNT utilisent des taches par tick. Elles sont bornees
  ou adaptees par lot, mais doivent etre profilees lors de gros traitements.
- L'auto-leave traite les joueurs par lots d'un element par tick. Cette approche
  protege le TPS mais peut rester active longtemps sur une grosse base.

### Fichiers et persistance

- Les factions, joueurs et claims sont stockes en JSON complet. Les sauvegardes
  automatiques sont lancees hors thread principal, ce qui est correct, mais le
  volume d'allocation augmente avec la taille des donnees.
- `factionLogs.json` est reecrit en entier lors d'une sauvegarde de logs. Une base
  SQLite append-only serait preferable au-dela de plusieurs milliers de factions.
- Les fichiers YAML de missions, upgrades, CoreX et roster sont encore copies et
  lus au demarrage pour compatibilite. Une deuxieme phase peut rendre `FileManager`
  paresseux apres validation sur un serveur de preproduction.
- Aucun backend SQL fonctionnel n'est present malgre un commentaire MYSQL. Le
  serveur utilise donc uniquement JSON.

### Claims et puissance

- `MemoryBoard` maintient un index bidirectionnel faction -> chunks; le comptage
  de claims est donc deja en O(1)/indexe et doit etre conserve.
- `Faction#getPower()` additionne les puissances des membres en O(nombre de
  membres). Le cache top 10 limite le principal nouveau consommateur.
- Les recherches de relations parcourent toutes les factions. Elles sont surtout
  utilisees par les placeholders allies/ennemis historiques; eviter ces lignes
  sur des scoreboards rafraichis chaque tick.

## Commandes Valoria

La commande `/fadmin` utilise `valoria.fadmin` (OP par defaut) et fournit :

- disband, rename, leader, members, kick, join et leave;
- power set/add/remove;
- claim add/remove et claims clear;
- money set/add/remove;
- inspect et logs.

Les mutations de claim et d'appartenance emettent les evenements Bukkit
existants. Les operations sont ajoutees au journal d'audit Saber existant.

## Placeholders

Expansion PlaceholderAPI `valoria`, utilisable directement par
AjLeaderboards et DecentHolograms :

- `%valoria_top_faction_1_name%` a `%valoria_top_faction_10_name%`;
- `%valoria_top_faction_1_power%` a `%valoria_top_faction_10_power%`;
- `%valoria_faction_members%`;
- `%valoria_faction_online%`;
- `%valoria_faction_bank%`;
- `%valoria_faction_claims%`.

Les valeurs numeriques sont sans couleur ni separateur local, afin de rester
triables par les plugins de leaderboard.

## Validation production recommandee

1. Copier les donnees d'un serveur existant dans une instance de test.
2. Verifier creation, claim, overclaim, disband et sauvegarde apres redemarrage.
3. Tester Vault avec le plugin d'economie reel de Valoria.
4. Tester les placeholders via `/papi parse` puis dans AjLeaderboards et
   DecentHolograms.
5. Profiler 30 minutes avec spark pendant un pic de joueurs avant de modifier
   les intervalles de fly, scoreboard ou autosave.
