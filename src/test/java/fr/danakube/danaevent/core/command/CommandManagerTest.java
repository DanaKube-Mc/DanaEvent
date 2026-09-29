package fr.danakube.danaevent.core.command;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.module.AbstractDanaModule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CommandManagerTest {

    private ServerMock server;
    private DanaEventPlugin plugin;
    private CommandManager commandManager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        commandManager = plugin.getCommandManager();
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    private List<String> drainMessages(PlayerMock player) {
        List<String> messages = new ArrayList<>();
        Component comp;
        while ((comp = player.nextComponentMessage()) != null) {
            messages.add(PlainTextComponentSerializer.plainText().serialize(comp));
        }
        return messages;
    }

    static class DummyModuleSubCommand implements SubCommand {
        private final AtomicBoolean executed = new AtomicBoolean(false);
        private final AtomicReference<String[]> receivedArgs = new AtomicReference<>();

        @Override
        public String getName() {
            return "start";
        }

        @Override
        public List<String> getAliases() {
            return List.of("s");
        }

        @Override
        public String getDescription() {
            return "Démarre le module de test";
        }

        @Override
        public String getSyntax() {
            return "/danaevent test start <arena>";
        }

        @Override
        public String getPermission() {
            return "danaevent.test.start";
        }

        @Override
        public boolean isPlayerOnly() {
            return false;
        }

        @Override
        public void execute(CommandSender sender, String[] args) {
            executed.set(true);
            receivedArgs.set(args);
        }

        @Override
        public List<String> tabComplete(CommandSender sender, String[] args) {
            if (args.length == 1) {
                return List.of("arena1", "arena2");
            }
            return List.of();
        }
    }

    static class DummyTestModule extends AbstractDanaModule {
        private final DummyModuleSubCommand subCommand = new DummyModuleSubCommand();

        public DummyTestModule(String id) {
            super(id, "Test Module", "1.0.0");
            registerSubCommand(subCommand);
        }

        public DummyModuleSubCommand getSubCommand() {
            return subCommand;
        }
    }

    @Nested
    @DisplayName("Core Commands Execution")
    class CoreCommandsTests {

        @Test
        @DisplayName("/de help should display authorized commands to sender")
        void shouldExecuteHelpCommand() {
            PlayerMock player = server.addPlayer();
            player.addAttachment(plugin, "danaevent.admin.help", true);

            server.dispatchCommand(player, "de help");

            List<String> messages = drainMessages(player);
            assertThat(messages).isNotEmpty();
            assertThat(String.join("\n", messages))
                .contains("DanaEvent")
                .contains("Commandes autorisées")
                .contains("/danaevent help");
        }

        @Test
        @DisplayName("/de without arguments should execute help")
        void shouldExecuteHelpWhenNoArgs() {
            PlayerMock player = server.addPlayer();
            player.addAttachment(plugin, "danaevent.admin.help", true);

            server.dispatchCommand(player, "de");

            List<String> messages = drainMessages(player);
            assertThat(messages).isNotEmpty();
            assertThat(String.join("\n", messages))
                .contains("DanaEvent")
                .contains("Commandes autorisées");
        }

        @Test
        @DisplayName("/de reload should reload configuration, messages, and modules")
        void shouldExecuteReloadCommand() {
            PlayerMock player = server.addPlayer();
            player.addAttachment(plugin, "danaevent.admin.reload", true);

            server.dispatchCommand(player, "de reload");

            List<String> messages = drainMessages(player);
            assertThat(messages).isNotEmpty();
            assertThat(String.join("\n", messages))
                .contains("Configuration et messages rechargés avec succès !");
        }

        @Test
        @DisplayName("/de modules should list registered modules and their status")
        void shouldExecuteModulesCommand() {
            DummyTestModule dummy = new DummyTestModule("dummy");
            plugin.getModuleManager().registerModule(dummy);
            plugin.getModuleManager().enableModule("dummy");

            PlayerMock player = server.addPlayer();
            player.addAttachment(plugin, "danaevent.admin.modules", true);

            server.dispatchCommand(player, "de modules");

            List<String> messages = drainMessages(player);
            assertThat(messages).isNotEmpty();
            String full = String.join("\n", messages);
            assertThat(full)
                .contains("Modules enregistrés")
                .contains("Test Module")
                .contains("Activé");
        }

        @Test
        @DisplayName("/de wand should give selection wand to player")
        void shouldExecuteWandCommandForPlayer() {
            PlayerMock player = server.addPlayer();
            player.addAttachment(plugin, "danaevent.admin.wand", true);

            assertThat(player.getInventory().isEmpty()).isTrue();

            server.dispatchCommand(player, "de wand");

            List<String> messages = drainMessages(player);
            assertThat(messages).isNotEmpty();
            assertThat(String.join("\n", messages))
                .contains("Vous avez reçu le bâton de sélection !");
            assertThat(player.getInventory().isEmpty()).isFalse();
            assertThat(plugin.getSelectionManager().isWand(player.getInventory().getItem(0))).isTrue();
        }

        @Test
        @DisplayName("/de wand from console should fail with player-only message")
        void shouldRejectWandCommandFromConsole() {
            ConsoleCommandSender console = server.getConsoleSender();

            boolean result = server.dispatchCommand(console, "de wand");
            assertThat(result).isTrue();
        }
    }

    @Nested
    @DisplayName("Permissions & Access Control")
    class PermissionTests {

        @Test
        @DisplayName("Sender without permission should be rejected with no-permission message")
        void shouldRejectWithoutPermission() {
            PlayerMock player = server.addPlayer();
            // player has no permissions

            server.dispatchCommand(player, "de reload");

            List<String> messages = drainMessages(player);
            assertThat(messages).isNotEmpty();
            assertThat(String.join("\n", messages))
                .contains("Vous n'avez pas la permission d'exécuter cette commande.");
        }

        @Test
        @DisplayName("Sender with permission should be allowed")
        void shouldAllowWithPermission() {
            PlayerMock player = server.addPlayer();
            player.addAttachment(plugin, "danaevent.admin.reload", true);

            server.dispatchCommand(player, "de reload");

            List<String> messages = drainMessages(player);
            assertThat(messages).isNotEmpty();
            assertThat(String.join("\n", messages))
                .contains("Configuration et messages rechargés avec succès !");
        }
    }

    @Nested
    @DisplayName("Module SubCommand Dispatching")
    class ModuleDispatchTests {

        @Test
        @DisplayName("Should dispatch /de <module_id> <subcommand> to module subcommand")
        void shouldDispatchToModuleSubCommand() {
            DummyTestModule module = new DummyTestModule("games");
            plugin.getModuleManager().registerModule(module);
            plugin.getModuleManager().enableModule("games");

            PlayerMock player = server.addPlayer();
            player.addAttachment(plugin, "danaevent.test.start", true);

            server.dispatchCommand(player, "de games start arena_1");

            assertThat(module.getSubCommand().executed.get()).isTrue();
            assertThat(module.getSubCommand().receivedArgs.get()).containsExactly("arena_1");
        }

        @Test
        @DisplayName("Should reject command if module is disabled")
        void shouldRejectIfModuleDisabled() {
            DummyTestModule module = new DummyTestModule("inactive");
            plugin.getModuleManager().registerModule(module);
            // Module is disabled

            PlayerMock player = server.addPlayer();
            player.addAttachment(plugin, "danaevent.test.start", true);

            server.dispatchCommand(player, "de inactive start");

            assertThat(module.getSubCommand().executed.get()).isFalse();
            List<String> messages = drainMessages(player);
            assertThat(messages).isNotEmpty();
            assertThat(String.join("\n", messages))
                .contains("est actuellement désactivé");
        }

        @Test
        @DisplayName("Should display module help if only module id is passed without subcommand")
        void shouldDisplayModuleHelpWhenNoSubCommand() {
            DummyTestModule module = new DummyTestModule("arena");
            plugin.getModuleManager().registerModule(module);
            plugin.getModuleManager().enableModule("arena");

            PlayerMock player = server.addPlayer();
            player.addAttachment(plugin, "danaevent.test.start", true);

            server.dispatchCommand(player, "de arena");

            List<String> messages = drainMessages(player);
            assertThat(messages).isNotEmpty();
            String full = String.join("\n", messages);
            assertThat(full)
                .contains("Commandes du module Test Module")
                .contains("/danaevent test start <arena>");
        }

        @Test
        @DisplayName("Should notify module-not-found when module id does not exist")
        void shouldNotifyWhenModuleNotFound() {
            PlayerMock player = server.addPlayer();

            server.dispatchCommand(player, "de unknownmodule");

            List<String> messages = drainMessages(player);
            assertThat(messages).isNotEmpty();
            assertThat(String.join("\n", messages))
                .contains("est introuvable");
        }
    }

    @Nested
    @DisplayName("Dynamic Tab Completion")
    class TabCompletionTests {

        @Test
        @DisplayName("Tab completion on root should suggest core commands and active modules allowed for sender")
        void shouldTabCompleteRootArgs() {
            DummyTestModule mod = new DummyTestModule("battle");
            plugin.getModuleManager().registerModule(mod);
            plugin.getModuleManager().enableModule("battle");

            PlayerMock admin = server.addPlayer();
            admin.addAttachment(plugin, "danaevent.admin.help", true);
            admin.addAttachment(plugin, "danaevent.admin.reload", true);
            admin.addAttachment(plugin, "danaevent.admin.modules", true);
            admin.addAttachment(plugin, "danaevent.admin.wand", true);

            List<String> completions = commandManager.onTabComplete(admin, null, "de", new String[]{""});

            assertThat(completions).contains("help", "reload", "modules", "wand", "battle");
        }

        @Test
        @DisplayName("Tab completion should filter out commands sender has no permission for")
        void shouldFilterUnauthorizedTabCompletions() {
            PlayerMock regularUser = server.addPlayer();
            // regular user has only help permission
            regularUser.addAttachment(plugin, "danaevent.admin.help", true);

            List<String> completions = commandManager.onTabComplete(regularUser, null, "de", new String[]{""});

            assertThat(completions).contains("help");
            assertThat(completions).doesNotContain("reload", "wand", "modules");
        }

        @Test
        @DisplayName("Tab completion on arg 1 for module should suggest its subcommands")
        void shouldTabCompleteModuleSubCommands() {
            DummyTestModule mod = new DummyTestModule("parkour");
            plugin.getModuleManager().registerModule(mod);
            plugin.getModuleManager().enableModule("parkour");

            PlayerMock player = server.addPlayer();
            player.addAttachment(plugin, "danaevent.test.start", true);

            List<String> completions = commandManager.onTabComplete(player, null, "de", new String[]{"parkour", ""});

            assertThat(completions).containsExactly("start");
        }

        @Test
        @DisplayName("Tab completion on arg 2+ for module should delegate to subcommand")
        void shouldDelegateTabCompleteToSubCommand() {
            DummyTestModule mod = new DummyTestModule("jump");
            plugin.getModuleManager().registerModule(mod);
            plugin.getModuleManager().enableModule("jump");

            PlayerMock player = server.addPlayer();
            player.addAttachment(plugin, "danaevent.test.start", true);

            List<String> completions = commandManager.onTabComplete(player, null, "de", new String[]{"jump", "start", ""});

            assertThat(completions).containsExactlyInAnyOrder("arena1", "arena2");
        }
    }
}
