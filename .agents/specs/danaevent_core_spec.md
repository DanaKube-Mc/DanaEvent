# Spécification Technique : DanaEvent Core

## 1. Vue d'ensemble & Rôle du Core

Le **DanaEvent Core** constitue le socle fondamental du plugin. Son rôle est d'assurer l'infrastructure technique globale sans contenir de logique métier propre à un mini-jeu spécifique. Il fournit tous les services transverses consommés par les modules d'événements.

* **Plateforme :** Paper 1.21.x (Java 21 LTS)
* **Système de build :** Gradle (Kotlin DSL) ou Maven
* **Pattern Architectural :** Micro-Kernel (Core + Modules enfichables)

---

## 2. Structure du Package Core

```
fr.danakube.danaevent/
 ├── DanaEventPlugin.java (JavaPlugin principal)
 └── core/
      ├── module/
      │    ├── DanaModule.java (Interface socle de tout module)
      │    └── ModuleManager.java (Découverte, cycle de vie, reload)
      ├── database/
      │    ├── DatabaseManager.java (Pool HikariCP unifié)
      │    ├── StorageProvider.java (Interface d'accès aux données)
      │    ├── MySQLStorageProvider.java
      │    └── SQLiteStorageProvider.java
      ├── player/
      │    ├── PlayerStateManager.java (Snapshot & Restauration)
      │    ├── PlayerStateSnapshot.java (Modèle NBT/Location/Stats)
      │    └── PlayerCrashRecoveryListener.java (Protection déconnexion/crash)
      ├── command/
      │    ├── CommandManager.java (Dispatcher racine /danaevent et /de)
      │    ├── SubCommand.java (Interface d'injection de commande)
      │    └── CoreAdminCommands.java (/de reload, /de modules, etc.)
      ├── selection/
      │    ├── SelectionManager.java (Gestionnaire des sélections wand)
      │    ├── CuboidRegion.java (Calculs de volumes et détection de passage)
      │    └── WandListener.java (Interaction clic droit/gauche)
      ├── hook/
      │    ├── HookManager.java (Vérification et enregistrement d'APIs tierces)
      │    ├── PlaceholderAPIHook.java (Expansion PAPI centrale)
      │    └── DecentHologramsHook.java (Bridge pour affichages textuels)
      ├── message/
      │    └── MessageManager.java (Parser MiniMessage / messages.yml centralisé)
      └── gui/
           ├── GuiManager.java (Gestionnaire d'inventaires interactifs)
           └── CustomGui.java (Abstraction des menus avec pagination)
```

---

## 3. Spécifications Détaillées des Services

### 3.1 Cycle de Vie des Modules (`DanaModule` & `ModuleManager`)
Chaque événement (ex: BoatRace) est un sous-module qui implémente :
```java
public interface DanaModule {
    String getId();                 // Identifiant unique (ex: "boatrace")
    String getName();               // Nom affiché (ex: "Course de Bateaux")
    void onEnable();                // Initialisation des configs, listeners, tasks
    void onDisable();               // Nettoyage complet (entités, scoreboard, tasks)
    void onReload();                // Rechargement des fichiers YAML du module
    List<SubCommand> getSubCommands(); // Commandes injectées dans /de <module_id>
}
```
* Le `ModuleManager` charge les modules au démarrage du plugin.
* Si un module plante ou lève une exception, le `ModuleManager` l'isole et le désactive sans faire crasher le Core ni les autres modules.

---

### 3.2 Gestionnaire de Base de Données (`DatabaseManager`)
* Utilise **HikariCP** pour un pool de connexions asynchrone hautement optimisé.
* Configuration dans `plugins/DanaEvent/config.yml` :
  * Type de base : `SQLITE` (par défaut, fichier `danaevent.db`) ou `MYSQL` / `MARIADB`.
  * Hôte, port, nom de base, identifiants, pool size (défaut: 10 connexions).
* Table centrale créée par le Core :
  ```sql
  CREATE TABLE IF NOT EXISTS dana_players (
      uuid VARCHAR(36) PRIMARY KEY,
      username VARCHAR(16) NOT NULL,
      last_seen TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
  );
  ```

---

### 3.3 Système de Snapshot Joueur (`PlayerStateManager`)
Ce système garantit qu'aucun item ne peut être dupliqué ou perdu lors de la participation à un event :
1. **À l'entrée en event (`saveAndClear`) :**
   * Sauvegarde complète en mémoire et écriture préventive sur disque/DB : inventaire complet (sérialisé en Base64 NBT), niveau d'XP, vie, saturation/faim, effets de potions actifs, mode de jeu, et coordonnées de départ (`Location`).
   * Nettoyage complet de l'inventaire du joueur et passage dans le mode requis par l'event.
2. **À la sortie normale (`restore`) :**
   * Téléportation aux coordonnées d'origine.
   * Restauration à l'identique de l'inventaire, des effets et des statistiques.
   * Suppression du snapshot.
3. **Cas de Déconnexion / Crash (`PlayerCrashRecoveryListener`) :**
   * Si un joueur se déconnecte pendant un event, le snapshot reste stocké dans la table `dana_player_snapshots`.
   * Dès sa reconnexion (`PlayerJoinEvent`), le Core détecte le snapshot orphelin, restaure immédiatement le joueur et le téléporte au spawn général ou à sa position pré-event.

---

### 3.4 Outil de Sélection Cuboïde (`SelectionManager` / Wand)
* Commande admin : `/de wand` donne un item de sélection (ex: `STICK` ou `BLAZE_ROD` avec nom personnalisé).
* **Clic gauche :** Sélection du point `Pos1` (Location).
* **Clic droit :** Sélection du point `Pos2` (Location).
* Fournit un objet `CuboidRegion` avec les méthodes :
  * `boolean contains(Location loc)` : Détecte si un joueur ou véhicule entre dans la zone.
  * `Map<String, Object> serialize()` : Sauvegarde propre dans les fichiers YAML des modules.

---

### 3.5 Système de Commandes & Messages
* Commande racine : `/danaevent` avec alias court `/de`.
* Arborescence dynamique :
  * `/de reload` : Recharge le Core et tous les modules actifs.
  * `/de modules` : Affiche l'état de santé de chaque module.
  * `/de wand` : Donne l'outil de sélection.
  * `/de <module_id> ...` : Redirige directement vers les sous-commandes enregistrées par le module concerné.
* Support natif de **MiniMessage** (tags `<gradient>`, `<color>`, etc.) avec fichier `messages.yml` personnalisable.

---

## 4. Critères d'Acceptation pour les Développeurs du Core

1. Le Core doit compiler et démarrer sous Paper 1.21.x avec Java 21 sans aucun avertissement ni dépendance obsolète.
2. La déconnexion sauvage d'un joueur en plein event doit toujours restaurer son inventaire lors de son retour sans duplication ni perte d'item.
3. Le rechargement (`/de reload`) ne doit pas créer de fuite de mémoire (memory leak) ni de doublons de listeners/tasks.
