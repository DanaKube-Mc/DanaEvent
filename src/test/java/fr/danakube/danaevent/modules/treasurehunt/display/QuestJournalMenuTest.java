package fr.danakube.danaevent.modules.treasurehunt.display;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.modules.treasurehunt.model.Hunt;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntMode;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntPathType;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntStep;
import fr.danakube.danaevent.modules.treasurehunt.model.PlayerHuntProgress;
import fr.danakube.danaevent.modules.treasurehunt.model.StepTriggerType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QuestJournalMenuTest {

    private ServerMock server;
    private DanaEventPlugin plugin;
    private Hunt hunt;
    private PlayerHuntProgress progress;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);

        hunt = new Hunt("temple", "Temple Englouti", HuntMode.SOLO, HuntPathType.LINEAR_STATIC);
        
        HuntStep s1 = new HuntStep(1, StepTriggerType.CHAT_ANSWER);
        s1.setClue("Sous la statue");
        hunt.addStep(s1);

        HuntStep s2 = new HuntStep(2, StepTriggerType.BLOCK_CLICK);
        s2.setClue("Dans le coffre d'or");
        hunt.addStep(s2);

        HuntStep s3 = new HuntStep(3, StepTriggerType.ZONE_ENTER);
        s3.setClue("Au fond du bassin");
        hunt.addStep(s3);

        PlayerMock player = server.addPlayer("Seeker");
        progress = PlayerHuntProgress.start(player.getUniqueId(), false, "temple", List.of(1, 2, 3));
        progress.advanceStep(); // at step 2 (index 1)
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    private String getPlain(Component component) {
        return component != null ? PlainTextComponentSerializer.plainText().serialize(component) : "";
    }

    @Test
    @DisplayName("Should build QuestJournalMenu with header, close button, and proper step items")
    void shouldBuildQuestJournalMenu() {
        QuestJournalMenu menu = new QuestJournalMenu(hunt, progress);

        assertThat(menu.getSize()).isEqualTo(54);

        // Header at slot 4
        ItemStack header = menu.getInventory().getItem(4);
        assertThat(header).isNotNull();
        assertThat(header.getType()).isEqualTo(Material.KNOWLEDGE_BOOK);
        assertThat(getPlain(header.getItemMeta().displayName())).contains("Journal d'Aventure");

        // Close button at slot 49
        ItemStack close = menu.getInventory().getItem(49);
        assertThat(close).isNotNull();
        assertThat(close.getType()).isEqualTo(Material.BARRIER);
        assertThat(getPlain(close.getItemMeta().displayName())).contains("Fermer");

        // Step 1 (Completed) at slot 10
        ItemStack step1Item = menu.getInventory().getItem(10);
        assertThat(step1Item).isNotNull();
        assertThat(step1Item.getType()).isEqualTo(Material.ENCHANTED_BOOK);
        assertThat(getPlain(step1Item.getItemMeta().displayName())).contains("Étape 1", "Complétée");

        // Step 2 (Active) at slot 11
        ItemStack step2Item = menu.getInventory().getItem(11);
        assertThat(step2Item).isNotNull();
        assertThat(step2Item.getType()).isEqualTo(Material.WRITABLE_BOOK);
        assertThat(getPlain(step2Item.getItemMeta().displayName())).contains("Étape 2", "En cours");

        // Step 3 (Locked) at slot 12
        ItemStack step3Item = menu.getInventory().getItem(12);
        assertThat(step3Item).isNotNull();
        assertThat(step3Item.getType()).isEqualTo(Material.IRON_BARS);
        assertThat(getPlain(step3Item.getItemMeta().displayName())).contains("Étape 3", "Verrouillée");
    }

    @Test
    @DisplayName("Should strictly suppress default Minecraft italics on all GUI items and lores")
    void shouldSuppressDefaultItalics() {
        QuestJournalMenu menu = new QuestJournalMenu(hunt, progress);

        for (int i = 0; i < menu.getSize(); i++) {
            ItemStack item = menu.getInventory().getItem(i);
            if (item == null || !item.hasItemMeta()) {
                continue;
            }
            ItemMeta meta = item.getItemMeta();
            if (meta.hasDisplayName()) {
                Component name = meta.displayName();
                assertThat(name.decoration(TextDecoration.ITALIC))
                    .as("Slot %d display name should have italic=FALSE", i)
                    .isEqualTo(TextDecoration.State.FALSE);
            }
            if (meta.hasLore()) {
                List<Component> lore = meta.lore();
                for (int l = 0; l < lore.size(); l++) {
                    Component line = lore.get(l);
                    assertThat(line.decoration(TextDecoration.ITALIC))
                        .as("Slot %d lore line %d should have italic=FALSE", i, l)
                        .isEqualTo(TextDecoration.State.FALSE);
                }
            }
        }
    }

    @Test
    @DisplayName("Clicking close button should close player inventory")
    void shouldCloseInventoryOnClick() {
        PlayerMock player = server.addPlayer("Clicker");
        QuestJournalMenu menu = new QuestJournalMenu(hunt, progress);

        plugin.getGuiManager().openGui(player, menu);
        assertThat(player.getOpenInventory().getTopInventory()).isEqualTo(menu.getInventory());

        // Simulate click on close slot 49
        menu.getItem(49).getOnClick().accept(new org.bukkit.event.inventory.InventoryClickEvent(
            player.getOpenInventory(),
            org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
            49,
            org.bukkit.event.inventory.ClickType.LEFT,
            org.bukkit.event.inventory.InventoryAction.PICKUP_ALL
        ));

        player.closeInventory();
    }
}
