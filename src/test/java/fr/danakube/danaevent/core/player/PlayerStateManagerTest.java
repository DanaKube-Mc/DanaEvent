package fr.danakube.danaevent.core.player;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerStateManagerTest {

    private ServerMock server;
    private DanaEventPlugin plugin;
    private PlayerStateManager stateManager;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        stateManager = plugin.getPlayerStateManager();
        world = server.addSimpleWorld("manager_world");
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("saveAndClear should persist state to SQLite, cache in memory, clear inventory and reset status")
    void shouldSaveAndClearPlayerState() throws SQLException {
        PlayerMock player = server.addPlayer("DanaSaver");
        player.teleport(new Location(world, 10, 64, 10));

        // Equip custom item
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD, 1);
        ItemMeta meta = sword.getItemMeta();
        assertThat(meta).isNotNull();
        meta.displayName(Component.text("Épée Céleste"));
        meta.lore(List.of(Component.text("Arme d'événement")));
        meta.addEnchant(Enchantment.SHARPNESS, 4, true);
        meta.getPersistentDataContainer().set(new NamespacedKey("danaevent", "id"), PersistentDataType.STRING, "celestial_sword");
        sword.setItemMeta(meta);
        player.getInventory().setItem(0, sword);

        // Armor and offhand
        player.getInventory().setBoots(new ItemStack(Material.DIAMOND_BOOTS));
        player.getInventory().setItemInOffHand(new ItemStack(Material.GOLDEN_APPLE, 5));

        // Status
        player.setHealth(12.0);
        player.setFoodLevel(14);
        player.setSaturation(4.0f);
        player.setGameMode(GameMode.SURVIVAL);
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 1000, 1));

        // Execute saveAndClear
        boolean success = stateManager.saveAndClear(player);
        assertThat(success).isTrue();

        // 1. Inventory must be completely cleared
        for (ItemStack item : player.getInventory().getStorageContents()) {
            assertThat(item).isNull();
        }
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            assertThat(armor).isNull();
        }
        assertThat(player.getInventory().getItemInOffHand().getType()).isEqualTo(Material.AIR);

        // 2. Status must be reset to defaults
        assertThat(player.getHealth()).isEqualTo(20.0);
        assertThat(player.getFoodLevel()).isEqualTo(20);
        assertThat(player.getGameMode()).isEqualTo(GameMode.ADVENTURE);
        assertThat(player.getActivePotionEffects()).isEmpty();

        // 3. Cache must contain the snapshot
        assertThat(stateManager.hasSnapshot(player.getUniqueId())).isTrue();
        assertThat(stateManager.getSnapshot(player.getUniqueId())).isPresent();

        // 4. SQLite DB must persist the snapshot
        Optional<byte[]> dbSnapshot = plugin.getDatabaseManager().loadSnapshot(player.getUniqueId());
        assertThat(dbSnapshot).isPresent();
        assertThat(dbSnapshot.get()).isNotEmpty();
    }

    @Test
    @DisplayName("restore should restore exact items and state, then delete snapshot from DB (anti-duplication)")
    void shouldRestoreExactItemsAndPurgeFromDatabase() throws SQLException {
        PlayerMock player = server.addPlayer("DanaRestorer");
        player.teleport(new Location(world, 25, 70, -100));

        // Setup custom items
        ItemStack bow = new ItemStack(Material.BOW, 1);
        ItemMeta meta = bow.getItemMeta();
        assertThat(meta).isNotNull();
        meta.displayName(Component.text("Arc Stellaire"));
        meta.addEnchant(Enchantment.POWER, 5, true);
        bow.setItemMeta(meta);
        player.getInventory().setItem(0, bow);
        player.getInventory().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
        player.getInventory().setItemInOffHand(new ItemStack(Material.TOTEM_OF_UNDYING));

        player.setHealth(15.5);
        player.setFoodLevel(18);
        player.setGameMode(GameMode.SURVIVAL);
        player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 2000, 0));

        // Save and clear
        stateManager.saveAndClear(player);

        // Mutate player while in "event"
        player.teleport(new Location(world, 500, 100, 500));
        player.getInventory().setItem(0, new ItemStack(Material.DIRT, 64));

        // Restore
        boolean restored = stateManager.restore(player, true);
        assertThat(restored).isTrue();

        // 1. Items must be restored faithfully
        ItemStack restoredBow = player.getInventory().getItem(0);
        assertThat(restoredBow).isNotNull();
        assertThat(restoredBow.getType()).isEqualTo(Material.BOW);
        assertThat(restoredBow.getItemMeta().displayName()).isEqualTo(Component.text("Arc Stellaire"));
        assertThat(restoredBow.getItemMeta().getEnchantLevel(Enchantment.POWER)).isEqualTo(5);

        assertThat(player.getInventory().getChestplate()).isNotNull();
        assertThat(player.getInventory().getChestplate().getType()).isEqualTo(Material.NETHERITE_CHESTPLATE);
        assertThat(player.getInventory().getItemInOffHand().getType()).isEqualTo(Material.TOTEM_OF_UNDYING);

        // 2. Status restored
        assertThat(player.getHealth()).isEqualTo(15.5);
        assertThat(player.getFoodLevel()).isEqualTo(18);
        assertThat(player.getGameMode()).isEqualTo(GameMode.SURVIVAL);
        assertThat(player.getActivePotionEffects()).anyMatch(e -> e.getType().equals(PotionEffectType.FIRE_RESISTANCE));

        // 3. Location restored
        assertThat(player.getLocation().getX()).isEqualTo(25.0);
        assertThat(player.getLocation().getY()).isEqualTo(70.0);
        assertThat(player.getLocation().getZ()).isEqualTo(-100.0);

        // 4. Memory cache and Database must be purged to strictly prevent duplication
        assertThat(stateManager.hasSnapshot(player.getUniqueId())).isFalse();
        assertThat(stateManager.getSnapshot(player.getUniqueId())).isEmpty();
        assertThat(plugin.getDatabaseManager().loadSnapshot(player.getUniqueId())).isEmpty();
    }

    @Test
    @DisplayName("Attempting double restore must fail to prevent item duplication")
    void shouldFailOnDoubleRestoreAttempt() {
        PlayerMock player = server.addPlayer("DanaDoubleRestore");
        player.getInventory().setItem(0, new ItemStack(Material.DIAMOND, 10));

        stateManager.saveAndClear(player);

        // First restore succeeds
        boolean firstRestore = stateManager.restore(player, false);
        assertThat(firstRestore).isTrue();
        assertThat(player.getInventory().getItem(0)).isNotNull();
        assertThat(player.getInventory().getItem(0).getAmount()).isEqualTo(10);

        // Second restore MUST fail
        boolean secondRestore = stateManager.restore(player, false);
        assertThat(secondRestore).isFalse();

        // Inventory must still have only 10 diamonds, not duplicated
        assertThat(player.getInventory().getItem(0).getAmount()).isEqualTo(10);
    }

    @Test
    @DisplayName("Should restore state from SQLite even if memory cache was wiped (reboot simulation)")
    void shouldRestoreFromDatabaseWhenCacheCleared() throws SQLException {
        PlayerMock player = server.addPlayer("DanaRebootTester");
        player.getInventory().setItem(0, new ItemStack(Material.EMERALD, 32));

        stateManager.saveAndClear(player);

        // Simulate server restart or memory purge
        stateManager.cleanUp();

        // Database still has it
        assertThat(plugin.getDatabaseManager().loadSnapshot(player.getUniqueId())).isPresent();
        assertThat(stateManager.hasSnapshot(player.getUniqueId())).isTrue();

        // Restore works directly from SQLite
        boolean restored = stateManager.restore(player, false);
        assertThat(restored).isTrue();
        assertThat(player.getInventory().getItem(0)).isNotNull();
        assertThat(player.getInventory().getItem(0).getType()).isEqualTo(Material.EMERALD);
        assertThat(player.getInventory().getItem(0).getAmount()).isEqualTo(32);

        // Database snapshot purged
        assertThat(plugin.getDatabaseManager().loadSnapshot(player.getUniqueId())).isEmpty();
        assertThat(stateManager.hasSnapshot(player.getUniqueId())).isFalse();
    }
}
