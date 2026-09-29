# Directives et Règles du Projet DanaEvent

<!-- SECTION: MINECRAFT_DEVELOPMENT -->
## Stack Minecraft & Java
- **API** : Paper 1.21.x
- **Java** : JDK 21 LTS
- **Build Tool** : Gradle Kotlin DSL (`build.gradle.kts`)
- **Architecture** : Micro-Kernel (Core + Modules enfichables)

## Règles Spécifiques au Développement du Plugin
- **TDD Obligatoire** : Tout service, commande ou listener doit disposer de tests automatisés (MockBukkit & JUnit 5) validant les exigences fonctionnelles et les cas limites.
- **Thread Principal (Tick Thread)** : NE JAMAIS bloquer le thread principal avec des opérations d'I/O (requêtes SQL HikariCP, sérialisation disque lourde, requêtes réseau). Utiliser des tâches asynchrones (`CompletableFuture` ou scheduler Paper/Bukkit).
- **Gestion de la Mémoire & Fuites** : Ne JAMAIS stocker d'instances `Player` de manière statique ou persistante dans les caches/collections (utiliser strictement l'UUID `UUID`). Nettoyer toutes les tâches, listeners et collections dans `onDisable()`.
- **Compatibilité 1.21 & Data Components** : Utiliser la sérialisation native Paper 1.21 pour les `ItemStack` (`ItemStack#serializeAsBytes()`) afin de préserver l'ensemble des Data Components.
- **PersistentDataContainer (PDC)** : Utiliser des `NamespacedKey` pour identifier de manière inviolable les items spéciaux (ex: Selection Wand) au lieu de simples noms ou lores textuels.
- **Messages & Textes** : Utiliser l'API Kyori Adventure et MiniMessage pour tout texte envoyé aux joueurs.
<!-- END_SECTION: MINECRAFT_DEVELOPMENT -->

<!-- SECTION: GITHUB_CONVENTIONS -->
## Conventions de Versioning et GitHub
- **Branches** :
  - `main` : Branche stable de référence.
  - `feature/[nom-feature]` : Pour le développement d'un lot ou d'une fonctionnalité.
  - `bugfix/[nom-bug]` : Pour les correctifs.
- **Format des Commits (Conventional Commits)** :
  - Format : `<type>(<scope>): <description>` (ex: `feat(core): setup database manager with hikaricp`)
  - Types autorisés : `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `chore`.
<!-- END_SECTION: GITHUB_CONVENTIONS -->
