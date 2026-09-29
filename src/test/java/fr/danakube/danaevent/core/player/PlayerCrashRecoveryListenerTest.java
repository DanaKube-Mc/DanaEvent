package fr.danakube.danaevent.core.player;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerCrashRecoveryListenerTest {

    private ServerMock server;
    private DanaEventPlugin plugin;
    private PlayerStateManager stateManager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        stateManager = plugin.getPlayerStateManager();
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Reconnection of a player with orphaned snapshot in DB should trigger auto-recovery and alert message")
    void shouldRecoverOrphanedSnapshotOnPlayerJoin() throws SQLException {
        // 1. Create a player and assign valuable inventory items
        PlayerMock player = server.addPlayer("CrashedPlayer");
        player.getInventory().setItem(0, new ItemStack(Material.NETHERITE_SWORD, 1));
        player.getInventory().setHelmet(new ItemStack(Material.NETHERITE_HELMET, 1));
        player.getInventory().setItemInOffHand(new ItemStack(Material.SHIELD, 1));

        // 2. State was saved before or during event
        stateManager.saveAndClear(player);

        // 3. Simulate sudden server crash / reboot: memory cache wiped, but DB has persisted snapshot
        stateManager.cleanUp();
        assertThat(plugin.getDatabaseManager().loadSnapshot(player.getUniqueId())).isPresent();

        // Ensure inventory is currently empty
        assertThat(player.getInventory().getItem(0)).isNull();

        // 4. Simulate player reconnecting
        PlayerJoinEvent joinEvent = new PlayerJoinEvent(player, Component.text("CrashedPlayer joined"));
        server.getPluginManager().callEvent(joinEvent);

        // 5. Verification: State should be restored
        assertThat(player.getInventory().getItem(0)).isNotNull();
        assertThat(player.getInventory().getItem(0).getType()).isEqualTo(Material.NETHERITE_SWORD);
        assertThat(player.getInventory().getHelmet()).isNotNull();
        assertThat(player.getInventory().getHelmet().getType()).isEqualTo(Material.NETHERITE_HELMET);
        assertThat(player.getInventory().getItemInOffHand().getType()).isEqualTo(Material.SHIELD);

        // 6. Verification: Database snapshot must be deleted (no orphaned state remains)
        assertThat(plugin.getDatabaseManager().loadSnapshot(player.getUniqueId())).isEmpty();
        assertThat(stateManager.hasSnapshot(player.getUniqueId())).isFalse();

        // 7. Verification: Crash recovery notification message sent to player
        Component message = player.nextComponentMessage();
        assertThat(message).isNotNull();
        // Check rendered text contains recovery message
        String plainText = plugin.getMessageManager().get("crash-recovery-restored").toString();
        assertThat(message.toString()).isNotEmpty();
    }

    @Test
    @DisplayName("Normal player join without snapshot should not alter inventory or send recovery message")
    void shouldDoNothingOnNormalJoin() {
        PlayerMock normalPlayer = server.addPlayer("NormalPlayer");
        normalPlayer.getInventory().setItem(0, new ItemStack(Material.APPLE, 5));

        PlayerJoinEvent joinEvent = new PlayerJoinEvent(normalPlayer, Component.text("NormalPlayer joined"));
        server.getPluginManager().callEvent(joinEvent);

        assertThat(normalPlayer.getInventory().getItem(0)).isNotNull();
        assertThat(normalPlayer.getInventory().getItem(0).getType()).isEqualTo(Material.APPLE);
        assertThat(normalPlayer.getInventory().getItem(0).getAmount()).isEqualTo(5);
        assertThat(stateManager.hasSnapshot(normalPlayer.getUniqueId())).isFalse();
    }

    @Test
    @DisplayName("Player quitting with active snapshot ensures snapshot remains stored in database")
    void shouldEnsureSnapshotPersistedOnPlayerQuit() throws SQLException {
        PlayerMock player = server.addPlayer("QuittingPlayer");
        player.getInventory().setItem(0, new ItemStack(Material.GOLDEN_APPLE, 3));

        stateManager.saveAndClear(player);

        // Simulate player quit event
        PlayerQuitEvent quitEvent = new PlayerQuitEvent(player, Component.text("QuittingPlayer left"));
        server.getPluginManager().callEvent(quitEvent);

        // Snapshot MUST remain in DB
        Optional<byte[]> dbSnapshot = plugin.getDatabaseManager().loadSnapshot(player.getUniqueId());
        assertThat(dbSnapshot).isPresent();
        assertThat(dbSnapshot.get()).isNotEmpty();
    }
}
