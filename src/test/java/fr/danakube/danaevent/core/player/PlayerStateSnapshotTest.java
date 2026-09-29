package fr.danakube.danaevent.core.player;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import net.kyori.adventure.text.Component;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PlayerStateSnapshotTest {

    private ServerMock server;
    private World world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("test_world");
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should capture, serialize, deserialize and restore full player state with custom items")
    void shouldSerializeAndDeserializeCompletePlayerState() {
        PlayerMock player = server.addPlayer("DanaTester");
        player.teleport(new Location(world, 100.5, 64.0, -200.5, 90.0f, 45.0f));

        // 1. Setup custom item in inventory
        ItemStack customSword = new ItemStack(Material.DIAMOND_SWORD, 1);
        ItemMeta swordMeta = customSword.getItemMeta();
        assertThat(swordMeta).isNotNull();
        swordMeta.displayName(Component.text("Lame Légendaire"));
        swordMeta.lore(List.of(Component.text("Forgée dans les ténèbres"), Component.text("Puissance infinie")));
        swordMeta.addEnchant(Enchantment.SHARPNESS, 5, true);
        swordMeta.getPersistentDataContainer().set(new NamespacedKey("danaevent", "custom_id"), PersistentDataType.STRING, "epic_sword");
        customSword.setItemMeta(swordMeta);
        player.getInventory().setItem(0, customSword);

        // Setup armor
        ItemStack helmet = new ItemStack(Material.NETHERITE_HELMET, 1);
        ItemStack chestplate = new ItemStack(Material.NETHERITE_CHESTPLATE, 1);
        player.getInventory().setHelmet(helmet);
        player.getInventory().setChestplate(chestplate);

        // Setup offhand
        ItemStack shield = new ItemStack(Material.SHIELD, 1);
        player.getInventory().setItemInOffHand(shield);

        // 2. Setup health, stats, level, effects
        var maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttr != null) {
            maxHealthAttr.setBaseValue(40.0);
        }
        player.setHealth(28.5);
        player.setFoodLevel(16);
        player.setSaturation(8.5f);
        player.setLevel(42);
        player.setExp(0.65f);
        player.setGameMode(GameMode.SURVIVAL);
        player.setAllowFlight(true);
        player.setFlying(true);

        PotionEffect speedEffect = new PotionEffect(PotionEffectType.SPEED, 1200, 1, false, true, true);
        PotionEffect regenEffect = new PotionEffect(PotionEffectType.REGENERATION, 600, 0, false, false, true);
        player.addPotionEffect(speedEffect);
        player.addPotionEffect(regenEffect);

        // 3. Create snapshot
        PlayerStateSnapshot original = PlayerStateSnapshot.of(player);
        assertThat(original.getUuid()).isEqualTo(player.getUniqueId());
        assertThat(original.getHealth()).isEqualTo(28.5);
        assertThat(original.getMaxHealth()).isEqualTo(40.0);
        assertThat(original.getFoodLevel()).isEqualTo(16);
        assertThat(original.getSaturation()).isEqualTo(8.5f);
        assertThat(original.getLevel()).isEqualTo(42);
        assertThat(original.getExp()).isCloseTo(0.65f, within(0.001f));
        assertThat(original.isAllowFlight()).isTrue();
        assertThat(original.isFlying()).isTrue();
        assertThat(original.getEffects()).hasSize(2);

        // 4. Serialize to byte[]
        byte[] bytes = original.toByteArray();
        assertThat(bytes).isNotNull().isNotEmpty();

        // 5. Deserialize from byte[]
        PlayerStateSnapshot deserialized = PlayerStateSnapshot.fromByteArray(bytes);
        assertThat(deserialized.getUuid()).isEqualTo(player.getUniqueId());
        assertThat(deserialized.getHealth()).isEqualTo(28.5);
        assertThat(deserialized.getMaxHealth()).isEqualTo(40.0);
        assertThat(deserialized.getFoodLevel()).isEqualTo(16);
        assertThat(deserialized.getSaturation()).isEqualTo(8.5f);
        assertThat(deserialized.getLevel()).isEqualTo(42);
        assertThat(deserialized.getExp()).isCloseTo(0.65f, within(0.001f));
        assertThat(deserialized.getGameMode()).isEqualTo(GameMode.SURVIVAL);
        assertThat(deserialized.isAllowFlight()).isTrue();
        assertThat(deserialized.isFlying()).isTrue();
        assertThat(deserialized.getWorldName()).isEqualTo("test_world");

        // Verify custom item components after deserialization
        ItemStack deserializedSword = deserialized.getInventory()[0];
        assertThat(deserializedSword).isNotNull();
        assertThat(deserializedSword.getType()).isEqualTo(Material.DIAMOND_SWORD);
        ItemMeta deserializedMeta = deserializedSword.getItemMeta();
        assertThat(deserializedMeta).isNotNull();
        assertThat(deserializedMeta.displayName()).isEqualTo(Component.text("Lame Légendaire"));
        assertThat(deserializedMeta.lore()).isEqualTo(List.of(Component.text("Forgée dans les ténèbres"), Component.text("Puissance infinie")));
        assertThat(deserializedMeta.getEnchantLevel(Enchantment.SHARPNESS)).isEqualTo(5);
        assertThat(deserializedMeta.getPersistentDataContainer().get(new NamespacedKey("danaevent", "custom_id"), PersistentDataType.STRING))
            .isEqualTo("epic_sword");

        // Verify armor & offhand
        assertThat(deserialized.getArmor()[3]).isNotNull(); // Helmet
        assertThat(deserialized.getArmor()[3].getType()).isEqualTo(Material.NETHERITE_HELMET);
        assertThat(deserialized.getArmor()[2]).isNotNull(); // Chestplate
        assertThat(deserialized.getArmor()[2].getType()).isEqualTo(Material.NETHERITE_CHESTPLATE);
        assertThat(deserialized.getExtra()[0]).isNotNull(); // Offhand
        assertThat(deserialized.getExtra()[0].getType()).isEqualTo(Material.SHIELD);

        // Verify potion effects
        assertThat(deserialized.getEffects()).anyMatch(e -> e.getType().equals(PotionEffectType.SPEED) && e.getDuration() == 1200 && e.getAmplifier() == 1);
        assertThat(deserialized.getEffects()).anyMatch(e -> e.getType().equals(PotionEffectType.REGENERATION) && e.getDuration() == 600 && e.getAmplifier() == 0);

        // 6. Test applyTo onto a second player
        PlayerMock targetPlayer = server.addPlayer("DanaTarget");
        targetPlayer.getInventory().clear();
        targetPlayer.teleport(new Location(world, 0, 0, 0));

        deserialized.applyTo(targetPlayer, true);

        assertThat(targetPlayer.getHealth()).isEqualTo(28.5);
        assertThat(targetPlayer.getFoodLevel()).isEqualTo(16);
        assertThat(targetPlayer.getLevel()).isEqualTo(42);
        assertThat(targetPlayer.getInventory().getItem(0)).isNotNull();
        assertThat(targetPlayer.getInventory().getItem(0).getItemMeta().displayName()).isEqualTo(Component.text("Lame Légendaire"));
        assertThat(targetPlayer.getInventory().getHelmet()).isNotNull();
        assertThat(targetPlayer.getInventory().getHelmet().getType()).isEqualTo(Material.NETHERITE_HELMET);
        assertThat(targetPlayer.getInventory().getItemInOffHand().getType()).isEqualTo(Material.SHIELD);
        assertThat(targetPlayer.getActivePotionEffects()).hasSize(2);
        assertThat(targetPlayer.getLocation().getX()).isEqualTo(100.5);
        assertThat(targetPlayer.getLocation().getY()).isEqualTo(64.0);
        assertThat(targetPlayer.getLocation().getZ()).isEqualTo(-200.5);
    }

    @Test
    @DisplayName("applyTo with restoreLocation false should not change player position")
    void shouldNotRestoreLocationWhenFalse() {
        PlayerMock player = server.addPlayer("PositionTester");
        Location originalLoc = new Location(world, 10, 64, 10);
        player.teleport(originalLoc);

        PlayerStateSnapshot snapshot = PlayerStateSnapshot.of(player);

        Location newLoc = new Location(world, 50, 70, 50);
        player.teleport(newLoc);

        snapshot.applyTo(player, false);

        assertThat(player.getLocation().getX()).isEqualTo(50.0);
        assertThat(player.getLocation().getY()).isEqualTo(70.0);
        assertThat(player.getLocation().getZ()).isEqualTo(50.0);
    }

    @Test
    @DisplayName("applyTo should fallback to default spawn when world does not exist")
    void shouldFallbackToDefaultSpawnWhenWorldNotFound() {
        PlayerMock player = server.addPlayer("FallbackTester");

        Location unknownLoc = new Location(null, 999, 100, 999);
        PlayerStateSnapshot snapshot = new PlayerStateSnapshot(
            player.getUniqueId(),
            new ItemStack[36],
            new ItemStack[4],
            new ItemStack[1],
            20.0,
            20.0,
            20,
            5.0f,
            0,
            0.0f,
            List.of(),
            GameMode.SURVIVAL,
            false,
            false,
            unknownLoc,
            "non_existent_world"
        );

        snapshot.applyTo(player, true);

        // Fallback should have teleported to default world spawn location
        Location defaultSpawn = server.getWorlds().get(0).getSpawnLocation();
        assertThat(player.getLocation().getWorld()).isEqualTo(defaultSpawn.getWorld());
    }
}
