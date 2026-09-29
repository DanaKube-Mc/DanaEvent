## Description
<!-- Résumé clair des modifications apportées -->

## Type de changement
- [ ] `feat`: Nouvelle fonctionnalité
- [ ] `fix`: Correction de bug
- [ ] `docs`: Documentation
- [ ] `style`: Formatage / cosmétique
- [ ] `refactor`: Refactorisation sans changement de comportement
- [ ] `perf`: Optimisation des performances
- [ ] `test`: Ajout ou correction de tests
- [ ] `chore`: Tâche de maintenance / configuration

## Checklist
- [ ] Le code respecte les directives du projet (`.agents/AGENTS.md`)
- [ ] Les tests automatisés (JUnit 5 & MockBukkit) sont écrits et passent avec succès (`./gradlew test`)
- [ ] Aucun blocage d'I/O sur le thread principal Minecraft
- [ ] Pas de fuite mémoire (pas de stockage statique/persistant de `Player`, utilisation d'UUID)
- [ ] Les messages utilisent MiniMessage / Kyori Adventure
- [ ] Le commit respecte la convention `<type>(<scope>): <description>`
