# Spécification Technique : Module BoatRace (Course de Bateaux)

## 1. Vue d'ensemble du Module

Le module **BoatRace** (`boatrace`) est le premier événement implémenté au-dessus de **DanaEvent Core**. Il permet de gérer des courses de bateaux sur glace bleue (*Blue Ice*) avec détection automatique de départ/arrivée, chronométrage millimétré, anti-cut strict et classements synchronisés MySQL / Web.

* **ID du module :** `boatrace`
* **Dossier de configuration :** `plugins/DanaEvent/modules/boatrace/`
* **Dépendance interne :** Dépend des services du `DanaEvent Core` (Database, PlayerState, Wand, CommandManager, Hooks).

---

## 2. Structure Interne du Module

```
modules/boatrace/
 ├── BoatRaceModule.java (Implémente DanaModule)
 ├── config/
 │    └── BoatRaceConfig.java (Chargement des circuits tracks.yml)
 ├── model/
 │    ├── Track.java (Représentation d'une piste)
 │    ├── TrackType.java (Enum : SPRINT, CIRCUIT_LAPS)
 │    ├── TrackMode.java (Enum : TIME_ATTACK_247, EVENT_COMPETITION)
 │    ├── RaceSession.java (Session active d'un joueur ou groupe de coureurs)
 │    └── RecordEntry.java (Record avec temps en millisecondes)
 ├── manager/
 │    ├── TrackManager.java (CRUD des circuits, persistance YAML)
 │    ├── RaceManager.java (Gestion des départs, timers, franchissements de lignes)
 │    ├── CollisionManager.java (Gestion des Teams scoreboard anti-collision)
 │    └── BoatRaceLeaderboardManager.java (Gestion des records et cache local)
 ├── listener/
 │    ├── BoatMoveListener.java (Détection du franchissement des lignes départ/arrivée)
 │    ├── BoatDismountListener.java (Anti-cut : reset immédiat si le joueur quitte le bateau)
 │    └── BoatProtectionListener.java (Invulnérabilité des bateaux contre les coups/chocs)
 ├── task/
 │    └── RaceHudTask.java (Mise à jour périodique 1 tick de la BossBar / ActionBar)
 └── command/
      ├── BoatRacePlayerCmd.java (Sous-commandes joueurs : /de br ...)
      └── BoatRaceAdminCmd.java (Sous-commandes admin : /de br admin ...)
```

---

## 3. Spécifications Fonctionnelles

### 3.1 Caractéristiques d'un Circuit (`Track`)
Chaque circuit est identifié par un identifiant unique (ex: `glacier_speedway`) et comprend :
* **Type de course (`TrackType`) :**
  * `SPRINT` : Parcours linéaire du point A au point B (1 seul passage).
  * `CIRCUIT_LAPS` : Circuit en boucle fermée. Le joueur doit franchir la ligne d'arrivée $N$ fois (ex: 3 tours configurables).
* **Mode de fonctionnement (`TrackMode`) :**
  * `TIME_ATTACK_247` : Piste ouverte en continu. Les joueurs s'y rendent pour tenter de battre le record du mois.
  * `EVENT_COMPETITION` : Piste fermée. Uniquement activée lors d'un événement officiel lancé par l'administration avec départ groupé.
* **Zones & Coordonnées (Cuboïdes définis via la Wand du Core) :**
  * `StartRegion` : Zone déclenchant le chronomètre au franchissement.
  * `FinishRegion` : Zone comptabilisant le tour ou clôturant la course (identique à StartRegion pour une boucle).
  * `SpawnPoints` : Liste des emplacements de départ pour faire spawner le(s) bateau(x).
  * `LobbyLocation` : Point de rassemblement avant le départ d'une compétition.
* **Options de course :**
  * `collisions_enabled` : `true/false` (Active ou désactive la collision entre bateaux via les Teams Paper).
  * `boat_type` : Type de bateau (défaut : `OAK_BOAT`).
  * `laps` : Nombre de tours (si `CIRCUIT_LAPS`).

---

### 3.2 Mécaniques de Course & Règles Anti-Triche
* **Apparition & Embarquement automatique :**
  * Au lancement d'un run, le joueur est placé directement assis dans son bateau sur la ligne de départ.
* **Règle stricte anti-cut (Dismount) :**
  * Tout appui sur `Shift` (sortie de véhicule) entraîne la **destruction immédiate du bateau**, l'**invalidation du run** et la **téléportation au point de départ** avec un nouveau bateau.
  * Aucun joueur ne peut courir à pied sur la piste.
* **Invulnérabilité :**
  * Les bateaux ne peuvent pas être cassés par des coups d'épée ou de poing pendant l'épreuve.
* **Chronométrage de Haute Précision :**
  * Enregistrement en millisecondes : `delta = System.currentTimeMillis() - startTimeMillis`.
  * Format textuel : `MM:SS.mmm` (ex: `01:14.285`).
* **Affichage Temps Réel (HUD) :**
  * Affiché via **ActionBar** ou **BossBar** pendant la course :
    * `Chrono : 00:42.128 | Tour : [2/3] | Record : 01:12.450`

---

## 4. Persistance des Scores & Classements (MySQL & Web)

### 4.1 Structure de la table MySQL
```sql
CREATE TABLE IF NOT EXISTS dana_boatrace_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    track_id VARCHAR(32) NOT NULL,
    player_uuid VARCHAR(36) NOT NULL,
    time_millis BIGINT NOT NULL,
    laps INT NOT NULL DEFAULT 1,
    period_month VARCHAR(7) NOT NULL, -- Format 'YYYY-MM' (ex: '2026-09')
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_track_month_time (track_id, period_month, time_millis)
);
```

### 4.2 Requête Web pour le Site
Affichage direct du classement mensuel sur le site web :
```sql
SELECT p.username, r.time_millis, r.created_at
FROM dana_boatrace_records r
JOIN dana_players p ON r.player_uuid = p.uuid
WHERE r.track_id = 'glacier_speedway' AND r.period_month = DATE_FORMAT(NOW(), '%Y-%m')
ORDER BY r.time_millis ASC
LIMIT 10;
```

---

## 5. Commandes & Permissions

### Commandes Joueurs (`/de br`)
* `/de br list` : Liste les circuits accessibles.
* `/de br join <circuit>` : Rejoint la piste ou le lobby du circuit.
* `/de br leave` : Abandonne la course et restaure le joueur au spawn général.
* `/de br top <circuit> [alltime|monthly]` : Ouvre le GUI du classement du circuit.

### Commandes Administrateurs (`/de br admin`)
* `/de br admin track create <id> <SPRINT|CIRCUIT_LAPS> <247|EVENT>` : Crée un circuit.
* `/de br admin track <id> setstart` : Applique la sélection wand comme ligne de départ.
* `/de br admin track <id> setfinish` : Applique la sélection wand comme ligne d'arrivée.
* `/de br admin track <id> addspawn` : Ajoute la position actuelle comme emplacement de bateau.
* `/de br admin track <id> setlaps <nombre>` : Définit le nombre de tours.
* `/de br admin track <id> togglecollision` : Active/Désactive les collisions entre bateaux.
* `/de br admin event start <id>` : Démarre le compte à rebours d'une compétition.
* `/de br admin event stop <id>` : Interrompt la compétition en cours.
* `/de br admin resetranking <id> [YYYY-MM]` : Archive ou remet à zéro un classement mensuel.

---

## 6. Intégrations Externes (PAPI & DecentHolograms)

### 6.1 Identifiants PlaceholderAPI
* `%danaevent_boatrace_<track>_pb%` : Record personnel du joueur (`MM:SS.mmm`).
* `%danaevent_boatrace_<track>_top1_name%` : Pseudo du 1er au classement all-time.
* `%danaevent_boatrace_<track>_top1_time%` : Temps du 1er all-time.
* `%danaevent_boatrace_<track>_monthly_top1_name%` : Pseudo du 1er du mois en cours.
* `%danaevent_boatrace_<track>_monthly_top1_time%` : Temps du 1er du mois en cours.

### 6.2 Hook DecentHolograms
* Commande pour initialiser l'hologramme d'un circuit :
  * `/de br admin track <id> setholo [alltime|monthly]`
* L'hologramme est actualisé automatiquement en asynchrone dès qu'un joueur réalise un nouveau meilleur temps dans le top.

---

## 7. Critères d'Acceptation & Cas Limites pour les Développeurs

1. **Anti-Cut absolu :** Si un joueur descend du bateau (sneak), le chrono s'arrête, le bateau disparaît et le joueur est replacé sur la grille de départ.
2. **Précision du Chrono :** Le calcul du temps doit s'effectuer en millisecondes réelles (`System.currentTimeMillis()`) et non en ticks serveur pour ne pas être faussé par d'éventuels ralentissements du serveur.
3. **Gestion des Collisions :** Si désactivée, les bateaux et joueurs doivent pouvoir se traverser sans se bloquer (via `team.setOption(Option.COLLISION_RULE, OptionStatus.NEVER)`).
4. **Persistance Mensuelle :** Le champ `period_month` doit être renseigné automatiquement (`YYYY-MM`) pour permettre la remise à zéro mensuelle sans perte des données historiques.
