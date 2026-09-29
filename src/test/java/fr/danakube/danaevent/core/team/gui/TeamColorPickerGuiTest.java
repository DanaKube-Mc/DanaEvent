package fr.danakube.danaevent.core.team.gui;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.team.manager.TeamManager;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.core.team.model.TeamColor;
import fr.danakube.danaevent.core.team.model.TeamMember;
import fr.danakube.danaevent.core.team.model.TeamRole;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class TeamColorPickerGuiTest {

    private ServerMock server;
    private DanaEventPlugin plugin;
    private TeamManager teamManager;

    private PlayerMock leader1;
    private PlayerMock leader2;
    private PlayerMock member1;

    private DanaTeam team1;
    private DanaTeam team2;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        teamManager = plugin.getTeamManager();

        leader1 = server.addPlayer("LeaderOne");
        member1 = server.addPlayer("MemberOne");
        leader2 = server.addPlayer("LeaderTwo");

        team1 = new DanaTeam("team1", "Team 1", TeamColor.RED, leader1.getUniqueId());
        team1.addMember(new TeamMember(leader1.getUniqueId(), leader1.getName(), TeamRole.LEADER, Instant.now()));
        team1.addMember(new TeamMember(member1.getUniqueId(), member1.getName(), TeamRole.MEMBER, Instant.now()));

        team2 = new DanaTeam("team2", "Team 2", TeamColor.BLUE, leader2.getUniqueId());
        team2.addMember(new TeamMember(leader2.getUniqueId(), leader2.getName(), TeamRole.LEADER, Instant.now()));

        teamManager.getDatabase().insertTeam(team1).join();
        teamManager.getDatabase().insertTeam(team2).join();
        teamManager.loadAllTeams().join();

        team1 = teamManager.getTeam("team1").orElseThrow();
        team2 = teamManager.getTeam("team2").orElseThrow();
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should reject non-leader from opening color picker GUI")
    void shouldRejectNonLeader() {
        TeamColorPickerGui gui = new TeamColorPickerGui(plugin, teamManager);
        gui.open(member1, team1);

        String message = member1.nextMessage();
        assertThat(message).contains("Seul le chef d'équipe");
    }

    @Test
    @DisplayName("Should render 16 colors with correct current and taken states")
    void shouldRender16ColorsCorrectly() {
        TeamColorPickerGui gui = new TeamColorPickerGui(plugin, teamManager);
        gui.render(leader1, team1);

        // Close button at slot 49
        ItemStack closeBtn = gui.getCustomGui().getInventory().getItem(TeamColorPickerGui.CLOSE_SLOT);
        assertThat(closeBtn).isNotNull();
        assertThat(closeBtn.getType()).isEqualTo(Material.BARRIER);

        // Team 1 is RED, Team 2 is BLUE
        // Find RED and BLUE slots
        TeamColor[] colors = TeamColor.values();
        for (int i = 0; i < colors.length && i < TeamColorPickerGui.COLOR_SLOTS.length; i++) {
            TeamColor color = colors[i];
            int slot = TeamColorPickerGui.COLOR_SLOTS[i];
            ItemStack item = gui.getCustomGui().getInventory().getItem(slot);

            assertThat(item).isNotNull();
            ItemMeta meta = item.getItemMeta();
            assertThat(meta).isNotNull();

            if (color == TeamColor.RED) {
                // Current color of team1
                assertThat(item.getType()).isEqualTo(TeamColor.RED.getWoolMaterial());
                assertThat(meta.lore()).anyMatch(line -> line.toString().contains("Couleur Actuelle"));
            } else if (color == TeamColor.BLUE) {
                // Taken by team2
                assertThat(item.getType()).isEqualTo(Material.GRAY_STAINED_GLASS_PANE);
                assertThat(meta.lore()).anyMatch(line -> line.toString().contains("Déjà prise par l'équipe Team 2"));
            } else {
                // Available
                assertThat(item.getType()).isEqualTo(color.getWoolMaterial());
                assertThat(meta.lore()).anyMatch(line -> line.toString().contains("Cliquez pour choisir"));
            }
        }
    }

    @Test
    @DisplayName("Should suppress default Minecraft italics across all GUI items, buttons, and lore lines")
    void shouldSuppressDefaultItalics() {
        TeamColorPickerGui gui = new TeamColorPickerGui(plugin, teamManager);
        gui.render(leader1, team1);

        for (ItemStack item : gui.getCustomGui().getInventory().getContents()) {
            if (item != null && item.hasItemMeta()) {
                ItemMeta meta = item.getItemMeta();
                if (meta.hasDisplayName()) {
                    Component name = meta.displayName();
                    assertThat(name.decoration(TextDecoration.ITALIC)).isEqualTo(TextDecoration.State.FALSE);
                }
                if (meta.hasLore()) {
                    for (Component loreLine : meta.lore()) {
                        assertThat(loreLine.decoration(TextDecoration.ITALIC)).isEqualTo(TextDecoration.State.FALSE);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Should change team color and trigger callback when clicking an available color")
    void shouldChangeTeamColorOnClick() {
        AtomicBoolean selected = new AtomicBoolean(false);
        TeamColorPickerGui gui = new TeamColorPickerGui(plugin, teamManager, color -> selected.set(true));

        gui.open(leader1, team1);

        // Find GREEN slot (which is available)
        int greenSlot = -1;
        TeamColor[] colors = TeamColor.values();
        for (int i = 0; i < colors.length; i++) {
            if (colors[i] == TeamColor.GREEN) {
                greenSlot = TeamColorPickerGui.COLOR_SLOTS[i];
                break;
            }
        }
        assertThat(greenSlot).isNotEqualTo(-1);

        // Simulate click
        InventoryClickEvent clickEvent = new InventoryClickEvent(
            leader1.getOpenInventory(),
            org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
            greenSlot,
            org.bukkit.event.inventory.ClickType.LEFT,
            org.bukkit.event.inventory.InventoryAction.PICKUP_ALL
        );
        plugin.getServer().getPluginManager().callEvent(clickEvent);

        // Wait for async DB and color update
        long deadline = System.currentTimeMillis() + 1000L;
        while (System.currentTimeMillis() < deadline && !selected.get()) {
            try {
                Thread.sleep(20);
            } catch (InterruptedException ignored) {}
        }

        assertThat(teamManager.getTeam("team1").orElseThrow().getColor()).isEqualTo(TeamColor.GREEN);
        assertThat(selected.get()).isTrue();
    }

    @Test
    @DisplayName("Should reject and send message when clicking a color already taken by another team")
    void shouldRejectClickingTakenColor() {
        TeamColorPickerGui gui = new TeamColorPickerGui(plugin, teamManager);
        gui.open(leader1, team1);

        // Find BLUE slot (taken by team2)
        int blueSlot = -1;
        TeamColor[] colors = TeamColor.values();
        for (int i = 0; i < colors.length; i++) {
            if (colors[i] == TeamColor.BLUE) {
                blueSlot = TeamColorPickerGui.COLOR_SLOTS[i];
                break;
            }
        }
        assertThat(blueSlot).isNotEqualTo(-1);

        InventoryClickEvent clickEvent = new InventoryClickEvent(
            leader1.getOpenInventory(),
            org.bukkit.event.inventory.InventoryType.SlotType.CONTAINER,
            blueSlot,
            org.bukkit.event.inventory.ClickType.LEFT,
            org.bukkit.event.inventory.InventoryAction.PICKUP_ALL
        );
        plugin.getServer().getPluginManager().callEvent(clickEvent);

        // Team 1 color remains RED
        assertThat(team1.getColor()).isEqualTo(TeamColor.RED);
        String message = leader1.nextMessage();
        assertThat(message).contains("déjà utilisée par une autre équipe");
    }
}
