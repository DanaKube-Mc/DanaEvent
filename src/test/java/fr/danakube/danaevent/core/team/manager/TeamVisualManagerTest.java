package fr.danakube.danaevent.core.team.manager;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.core.team.model.TeamColor;
import fr.danakube.danaevent.core.team.model.TeamMember;
import fr.danakube.danaevent.core.team.model.TeamRole;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.LeatherArmorMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TeamVisualManagerTest {

    private ServerMock server;
    private TeamVisualManager visualManager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        visualManager = new TeamVisualManager();
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should create colored leather armor piece with matching Bukkit color")
    void shouldCreateColoredLeatherArmorPiece() {
        ItemStack helmet = TeamVisualManager.createColoredArmorPiece(Material.LEATHER_HELMET, TeamColor.CYAN);

        assertThat(helmet.getType()).isEqualTo(Material.LEATHER_HELMET);
        assertThat(helmet.getItemMeta()).isInstanceOf(LeatherArmorMeta.class);

        LeatherArmorMeta meta = (LeatherArmorMeta) helmet.getItemMeta();
        assertThat(meta.getColor()).isEqualTo(TeamColor.CYAN.getBukkitColor());
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when creating armor with non-leather material")
    void shouldThrowOnNonLeatherMaterial() {
        assertThatThrownBy(() -> TeamVisualManager.createColoredArmorPiece(Material.DIAMOND_CHESTPLATE, TeamColor.RED))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Material must be a leather armor piece");
    }

    @Test
    @DisplayName("Should create complete 4-piece colored armor set")
    void shouldCreateCompleteArmorSet() {
        ItemStack[] armor = TeamVisualManager.createColoredArmorSet(TeamColor.ORANGE);

        assertThat(armor).hasSize(4);
        assertThat(armor[0].getType()).isEqualTo(Material.LEATHER_BOOTS);
        assertThat(armor[1].getType()).isEqualTo(Material.LEATHER_LEGGINGS);
        assertThat(armor[2].getType()).isEqualTo(Material.LEATHER_CHESTPLATE);
        assertThat(armor[3].getType()).isEqualTo(Material.LEATHER_HELMET);

        for (ItemStack piece : armor) {
            LeatherArmorMeta meta = (LeatherArmorMeta) piece.getItemMeta();
            assertThat(meta.getColor()).isEqualTo(TeamColor.ORANGE.getBukkitColor());
        }
    }

    @Test
    @DisplayName("Should equip team armor on player")
    void shouldEquipTeamArmorOnPlayer() {
        PlayerMock player = server.addPlayer("ArmorKnight");

        visualManager.equipTeamArmor(player, TeamColor.PURPLE);

        ItemStack[] equipped = player.getInventory().getArmorContents();
        assertThat(equipped).hasSize(4);
        assertThat(equipped[0].getType()).isEqualTo(Material.LEATHER_BOOTS);
        assertThat(equipped[1].getType()).isEqualTo(Material.LEATHER_LEGGINGS);
        assertThat(equipped[2].getType()).isEqualTo(Material.LEATHER_CHESTPLATE);
        assertThat(equipped[3].getType()).isEqualTo(Material.LEATHER_HELMET);

        for (ItemStack piece : equipped) {
            LeatherArmorMeta meta = (LeatherArmorMeta) piece.getItemMeta();
            assertThat(meta.getColor()).isEqualTo(TeamColor.PURPLE.getBukkitColor());
        }
    }

    @Test
    @DisplayName("Should toggle glowing on all online team members")
    void shouldToggleTeamGlowing() {
        PlayerMock player1 = server.addPlayer("Member1");
        PlayerMock player2 = server.addPlayer("Member2");

        DanaTeam team = new DanaTeam("squad", "Squad", TeamColor.LIME, player1.getUniqueId());
        team.addMember(new TeamMember(player1.getUniqueId(), player1.getName(), TeamRole.LEADER, Instant.now()));
        team.addMember(new TeamMember(player2.getUniqueId(), player2.getName(), TeamRole.MEMBER, Instant.now()));

        assertThat(player1.isGlowing()).isFalse();
        assertThat(player2.isGlowing()).isFalse();

        visualManager.setTeamGlowing(team, true);

        assertThat(player1.isGlowing()).isTrue();
        assertThat(player2.isGlowing()).isTrue();

        visualManager.setTeamGlowing(team, false);

        assertThat(player1.isGlowing()).isFalse();
        assertThat(player2.isGlowing()).isFalse();
    }
}
