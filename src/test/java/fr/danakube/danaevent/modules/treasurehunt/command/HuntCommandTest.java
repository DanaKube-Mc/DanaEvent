package fr.danakube.danaevent.modules.treasurehunt.command;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.WorldMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.gui.CustomGui;
import fr.danakube.danaevent.core.hook.DanaEventPlaceholderExpansion;
import fr.danakube.danaevent.modules.treasurehunt.TreasureHuntModule;
import fr.danakube.danaevent.modules.treasurehunt.manager.HuntLeaderboardManager;
import fr.danakube.danaevent.modules.treasurehunt.model.Hunt;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntMode;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntPathType;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntStep;
import fr.danakube.danaevent.modules.treasurehunt.model.StepTriggerType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HuntCommandTest {

    private ServerMock server;
    private WorldMock world;
    private DanaEventPlugin plugin;
    private TreasureHuntModule module;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        world = server.addSimpleWorld("hunt_world");
        module = plugin.getTreasureHuntModule();
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

    private Hunt createSampleHunt(String id, boolean active) {
        Hunt hunt = new Hunt(id, "Chasse Test " + id, HuntMode.SOLO, HuntPathType.LINEAR_STATIC);
        HuntStep step1 = new HuntStep(1, StepTriggerType.BLOCK_CLICK);
        step1.setTargetLocation(new Location(world, 10, 64, 10));
        step1.setClue("Indice 1");

        HuntStep step2 = new HuntStep(2, StepTriggerType.CHAT_ANSWER);
        step2.setChatAnswer("secret123");
        step2.setClue("Indice 2");

        hunt.addStep(step1);
        hunt.addStep(step2);
        hunt.setEnabled(active);

        module.getHuntConfig().registerHunt(hunt);
        module.getHuntConfig().saveHunts();
        return hunt;
    }

    @Nested
    @DisplayName("Player Commands Tests")
    class PlayerCommands {

        @Test
        @DisplayName("/de hunt list displays hunts with statuses")
        void shouldListActiveHunts() {
            createSampleHunt("hunt_active", true);
            createSampleHunt("hunt_inactive", false);

            PlayerMock player = server.addPlayer("Alice");
            server.dispatchCommand(player, "de hunt list");

            List<String> messages = drainMessages(player);
            assertThat(messages).anyMatch(m -> m.contains("hunt_active") && m.contains("Activé"));
            assertThat(messages).anyMatch(m -> m.contains("hunt_inactive") && m.contains("Désactivé"));
        }

        @Test
        @DisplayName("/de hunt join starts a hunt session")
        void shouldJoinHunt() {
            createSampleHunt("my_hunt", true);

            PlayerMock player = server.addPlayer("Bob");
            server.dispatchCommand(player, "de hunt join my_hunt");

            List<String> messages = drainMessages(player);
            assertThat(messages).anyMatch(m -> m.contains("commence") || m.contains("démarré"));
            assertThat(module.getProgressManager().isParticipant(player.getUniqueId())).isTrue();
        }

        @Test
        @DisplayName("/de hunt join fails when hunt is unknown or inactive")
        void shouldFailJoinInactiveOrUnknown() {
            createSampleHunt("inactive_hunt", false);

            PlayerMock player = server.addPlayer("Bob");
            server.dispatchCommand(player, "de hunt join unknown");

            List<String> messages = drainMessages(player);
            assertThat(messages).anyMatch(m -> m.contains("introuvable"));

            server.dispatchCommand(player, "de hunt join inactive_hunt");
            messages = drainMessages(player);
            assertThat(messages).anyMatch(m -> m.contains("pas encore prête") || m.contains("pas active"));
        }

        @Test
        @DisplayName("/de hunt journal opens GUI when in hunt, errors when not")
        void shouldOpenJournalGui() {
            PlayerMock player = server.addPlayer("Charlie");

            server.dispatchCommand(player, "de hunt journal");
            List<String> messages = drainMessages(player);
            assertThat(messages).anyMatch(m -> m.contains("participez"));

            createSampleHunt("hunt_journal", true);
            module.getProgressManager().startHunt(player.getUniqueId(), false, "hunt_journal");

            server.dispatchCommand(player, "de hunt journal");
            assertThat(player.getOpenInventory().getTopInventory().getHolder()).isInstanceOf(CustomGui.class);
        }

        @Test
        @DisplayName("/de hunt leave cancels active session")
        void shouldLeaveHunt() {
            createSampleHunt("hunt_leave", true);
            PlayerMock player = server.addPlayer("David");
            module.getProgressManager().startHunt(player.getUniqueId(), false, "hunt_leave");

            assertThat(module.getProgressManager().isParticipant(player.getUniqueId())).isTrue();

            server.dispatchCommand(player, "de hunt leave");
            assertThat(module.getProgressManager().isParticipant(player.getUniqueId())).isFalse();

            List<String> messages = drainMessages(player);
            assertThat(messages).anyMatch(m -> m.contains("quitté") || m.contains("abandonné"));
        }

        @Test
        @DisplayName("/de hunt top sends leaderboard messages")
        void shouldDisplayTopMessages() throws InterruptedException {
            createSampleHunt("hunt_top", true);
            PlayerMock player = server.addPlayer("Eva");

            server.dispatchCommand(player, "de hunt top hunt_top");
            Thread.sleep(100);
            List<String> messages = drainMessages(player);
            assertThat(messages).anyMatch(m -> m.toLowerCase().contains("classement") || m.toLowerCase().contains("aucun"));
        }
    }

    @Nested
    @DisplayName("Admin Commands Tests")
    class AdminCommands {

        @Test
        @DisplayName("Admin command requires danaevent.treasurehunt.admin permission")
        void shouldRequirePermission() {
            PlayerMock player = server.addPlayer("NoPermPlayer");
            server.dispatchCommand(player, "de hunt admin create test SOLO LINEAR");

            List<String> messages = drainMessages(player);
            assertThat(messages).anyMatch(m -> m.contains("permission"));
        }

        @Test
        @DisplayName("Admin create hunt succeeds with valid arguments")
        void shouldCreateHuntViaAdminCmd() {
            PlayerMock admin = server.addPlayer("Admin");
            admin.addAttachment(plugin, "danaevent.treasurehunt.admin", true);

            server.dispatchCommand(admin, "de hunt admin create new_hunt SOLO LINEAR");

            List<String> messages = drainMessages(admin);
            assertThat(messages).anyMatch(m -> m.contains("créée"));

            assertThat(module.getHuntConfig().getHunt("new_hunt")).isPresent();
            Hunt hunt = module.getHuntConfig().getHunt("new_hunt").get();
            assertThat(hunt.getMode()).isEqualTo(HuntMode.SOLO);
            assertThat(hunt.getPathType()).isEqualTo(HuntPathType.LINEAR_STATIC);
            assertThat(hunt.isEnabled()).isTrue();
        }

        @Test
        @DisplayName("Admin step add zone adds zone step using Selection Wand")
        void shouldAddZoneStep() {
            createSampleHunt("hunt_zone_add", true);

            PlayerMock admin = server.addPlayer("Admin");
            admin.addAttachment(plugin, "danaevent.treasurehunt.admin", true);

            plugin.getSelectionManager().setPos1(admin.getUniqueId(), new Location(world, 0, 60, 0));
            plugin.getSelectionManager().setPos2(admin.getUniqueId(), new Location(world, 10, 70, 10));

            server.dispatchCommand(admin, "de hunt admin step add zone hunt_zone_add");

            List<String> messages = drainMessages(admin);
            assertThat(messages).anyMatch(m -> m.contains("ajoutée"));

            Hunt hunt = module.getHuntConfig().getHunt("hunt_zone_add").get();
            assertThat(hunt.getSteps()).anyMatch(s -> s.getTriggerType() == StepTriggerType.ZONE_ENTER);
        }

        @Test
        @DisplayName("Admin step add chat adds chat answer step")
        void shouldAddChatStep() {
            createSampleHunt("hunt_chat_add", true);

            PlayerMock admin = server.addPlayer("Admin");
            admin.addAttachment(plugin, "danaevent.treasurehunt.admin", true);

            server.dispatchCommand(admin, "de hunt admin step add chat hunt_chat_add motdepasse");

            List<String> messages = drainMessages(admin);
            assertThat(messages).anyMatch(m -> m.contains("ajoutée"));

            Hunt hunt = module.getHuntConfig().getHunt("hunt_chat_add").get();
            HuntStep step = hunt.getStep(3).orElseThrow();
            assertThat(step.getTriggerType()).isEqualTo(StepTriggerType.CHAT_ANSWER);
            assertThat(step.getChatAnswer()).isEqualTo("motdepasse");
        }

        @Test
        @DisplayName("Admin step setclue, setreward, and setfinalreward configure hunt metadata")
        void shouldConfigureStepClueAndRewards() {
            createSampleHunt("hunt_meta", true);

            PlayerMock admin = server.addPlayer("Admin");
            admin.addAttachment(plugin, "danaevent.treasurehunt.admin", true);
            admin.getInventory().setItemInMainHand(new ItemStack(Material.DIAMOND, 5));

            server.dispatchCommand(admin, "de hunt admin step setclue hunt_meta 1 Cherchez sous l'arbre rouge");
            server.dispatchCommand(admin, "de hunt admin step setreward hunt_meta 1");
            server.dispatchCommand(admin, "de hunt admin setfinalreward hunt_meta");

            List<String> messages = drainMessages(admin);
            assertThat(messages).anyMatch(m -> m.contains("Indice"));
            assertThat(messages).anyMatch(m -> m.contains("Récompense"));

            Hunt hunt = module.getHuntConfig().getHunt("hunt_meta").get();
            HuntStep step = hunt.getStep(1).orElseThrow();
            assertThat(step.getClue()).isEqualTo("Cherchez sous l'arbre rouge");
            assertThat(step.getRewardItem()).isNotNull();
            assertThat(step.getRewardItem().getType()).isEqualTo(Material.DIAMOND);
            assertThat(hunt.getFinalRewardItem()).isNotNull();
            assertThat(hunt.getFinalRewardItem().getType()).isEqualTo(Material.DIAMOND);
        }

        @Test
        @DisplayName("Admin toggle and resetranking operate correctly")
        void shouldToggleAndResetRanking() throws InterruptedException {
            createSampleHunt("hunt_toggle", true);

            PlayerMock admin = server.addPlayer("Admin");
            admin.addAttachment(plugin, "danaevent.treasurehunt.admin", true);

            server.dispatchCommand(admin, "de hunt admin toggle hunt_toggle");
            assertThat(module.getHuntConfig().getHunt("hunt_toggle").get().isEnabled()).isFalse();

            server.dispatchCommand(admin, "de hunt admin toggle hunt_toggle");
            assertThat(module.getHuntConfig().getHunt("hunt_toggle").get().isEnabled()).isTrue();

            server.dispatchCommand(admin, "de hunt admin resetranking hunt_toggle");
            Thread.sleep(100);
            List<String> messages = drainMessages(admin);
            assertThat(messages).anyMatch(m -> m.contains("réinitialisé"));
        }
    }

    @Nested
    @DisplayName("Tab Completer Tests")
    class TabCompleterTests {

        @Test
        @DisplayName("Tab completion suggests commands according to permissions and arguments")
        void shouldTabCompleteProperly() {
            createSampleHunt("tab_hunt", true);

            PlayerMock admin = server.addPlayer("AdminTab");
            admin.addAttachment(plugin, "danaevent.treasurehunt.admin", true);

            List<String> rootCompletions = plugin.getCommandManager().onTabComplete(admin, null, "de", new String[]{"hunt", ""});
            assertThat(rootCompletions).contains("list", "join", "leave", "journal", "top", "admin");

            List<String> joinCompletions = plugin.getCommandManager().onTabComplete(admin, null, "de", new String[]{"hunt", "join", ""});
            assertThat(joinCompletions).contains("tab_hunt");

            List<String> adminCompletions = plugin.getCommandManager().onTabComplete(admin, null, "de", new String[]{"hunt", "admin", ""});
            assertThat(adminCompletions).contains("create", "step", "setfinalreward", "toggle", "resetranking");

            List<String> stepCompletions = plugin.getCommandManager().onTabComplete(admin, null, "de", new String[]{"hunt", "admin", "step", ""});
            assertThat(stepCompletions).contains("add", "setclue", "setreward");

            List<String> stepAddCompletions = plugin.getCommandManager().onTabComplete(admin, null, "de", new String[]{"hunt", "admin", "step", "add", ""});
            assertThat(stepAddCompletions).contains("click", "zone", "chat");
        }
    }

    @Nested
    @DisplayName("PAPI Expansion Tests")
    class PlaceholderExpansionTests {

        @Test
        @DisplayName("Placeholder expansion parses hunt progress and top1 placeholders")
        void shouldResolveHuntPlaceholders() {
            Hunt hunt = createSampleHunt("papi_hunt", true);
            PlayerMock player = server.addPlayer("PapiPlayer");

            DanaEventPlaceholderExpansion expansion = new DanaEventPlaceholderExpansion(plugin);

            // When player has no active session
            String progressNoSession = expansion.onPlaceholderRequest(player, "hunt_papi_hunt_progress");
            assertThat(progressNoSession).isEqualTo("N/A");

            // When player is in hunt
            module.getProgressManager().startHunt(player.getUniqueId(), false, "papi_hunt");
            String progressInHunt = expansion.onPlaceholderRequest(player, "hunt_papi_hunt_progress");
            assertThat(progressInHunt).isEqualTo("1/2");

            // Top1 without records
            String top1Name = expansion.onPlaceholderRequest(player, "hunt_papi_hunt_top1_name");
            String top1Time = expansion.onPlaceholderRequest(player, "hunt_papi_hunt_top1_time");
            assertThat(top1Name).isEqualTo("N/A");
            assertThat(top1Time).isEqualTo("N/A");

            // Save record and check top1
            module.getLeaderboardManager().registerHolderName(player.getUniqueId(), "PapiPlayer");
            module.getDatabase().saveHuntRecord("papi_hunt", "SOLO", player.getUniqueId(),
                "2026-09", 42150L, System.currentTimeMillis()).join();
            module.getLeaderboardManager().invalidateCache("papi_hunt");
            module.getLeaderboardManager().getTopAllTime("papi_hunt", 5).join();

            top1Name = expansion.onPlaceholderRequest(player, "hunt_papi_hunt_top1_name");
            top1Time = expansion.onPlaceholderRequest(player, "hunt_papi_hunt_top1_time");
            assertThat(top1Name).isEqualTo("PapiPlayer");
            assertThat(top1Time).isEqualTo("00:42.150");
        }
    }

    @Nested
    @DisplayName("E2E Scenario Test")
    class E2EScenario {

        @Test
        @DisplayName("Full E2E flow: Admin creates hunt, player joins, clicks target block, completes hunt and updates top")
        void shouldRunFullHuntFlowSuccessfully() {
            PlayerMock admin = server.addPlayer("E2EAdmin");
            admin.addAttachment(plugin, "danaevent.treasurehunt.admin", true);

            // 1. Admin creates hunt
            server.dispatchCommand(admin, "de hunt admin create e2e_hunt SOLO LINEAR");

            // 2. Add step directly to config and save
            Hunt hunt = module.getHuntConfig().getHunt("e2e_hunt").orElseThrow();
            Block block = world.getBlockAt(20, 64, 20);
            block.setType(Material.CHEST);
            HuntStep step = new HuntStep(1, StepTriggerType.BLOCK_CLICK);
            step.setTargetLocation(block.getLocation());
            hunt.addStep(step);
            module.getHuntConfig().saveHunts();

            // 3. Player joins
            PlayerMock player = server.addPlayer("Competitor");
            server.dispatchCommand(player, "de hunt join e2e_hunt");
            assertThat(module.getProgressManager().isParticipant(player.getUniqueId())).isTrue();

            // 4. Player clicks chest block
            PlayerInteractEvent interactEvent = new PlayerInteractEvent(
                player,
                Action.RIGHT_CLICK_BLOCK,
                new ItemStack(Material.AIR),
                block,
                org.bukkit.block.BlockFace.UP,
                EquipmentSlot.HAND
            );
            server.getPluginManager().callEvent(interactEvent);

            // 5. Hunt is completed!
            assertThat(module.getProgressManager().isParticipant(player.getUniqueId())).isFalse();

            // 6. Top record is stored
            var topList = module.getDatabase().getTopAllTime("e2e_hunt", 10).join();
            assertThat(topList).hasSize(1);
            assertThat(topList.get(0).holderUuid()).isEqualTo(player.getUniqueId());
        }
    }
}
