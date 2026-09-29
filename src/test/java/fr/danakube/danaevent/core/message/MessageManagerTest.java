package fr.danakube.danaevent.core.message;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import be.seeseemelk.mockbukkit.entity.PlayerMock;
import fr.danakube.danaevent.DanaEventPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MessageManagerTest {

    private ServerMock server;
    private DanaEventPlugin plugin;
    private MessageManager messageManager;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        plugin = MockBukkit.load(DanaEventPlugin.class);
        messageManager = plugin.getMessageManager();
    }

    @AfterEach
    void tearDown() {
        if (MockBukkit.isMocked()) {
            MockBukkit.unmock();
        }
    }

    @Test
    @DisplayName("Should extract and load messages.yml into plugin data folder")
    void shouldExtractAndLoadMessagesFile() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        assertThat(messagesFile).exists();

        Component prefixComp = messageManager.parse("<prefix>");
        String plainPrefix = PlainTextComponentSerializer.plainText().serialize(prefixComp);
        assertThat(plainPrefix).contains("DanaEvent", "»");

        Component noPerm = messageManager.get("no-permission");
        String plainNoPerm = PlainTextComponentSerializer.plainText().serialize(noPerm);
        assertThat(plainNoPerm).contains("DanaEvent", "permission");
    }

    @Test
    @DisplayName("Should render MiniMessage tags, colors, and gradients properly")
    void shouldRenderMiniMessageTags() {
        Component comp = messageManager.parse("<gradient:#00c6ff:#0072ff><b>Gradient Text</b></gradient>");
        String plain = PlainTextComponentSerializer.plainText().serialize(comp);
        assertThat(plain).isEqualTo("Gradient Text");
        assertThat(comp.hasStyling()).isTrue();
    }

    @Test
    @DisplayName("Should replace placeholders with Placeholder.parsed and Placeholder.component")
    void shouldReplacePlaceholders() {
        // Test with Placeholder.parsed
        Component parsedComp = messageManager.get(
            "module-not-found",
            Placeholder.parsed("module", "Paintball")
        );
        String plainParsed = PlainTextComponentSerializer.plainText().serialize(parsedComp);
        assertThat(plainParsed).contains("Paintball").doesNotContain("<module>");

        // Test with Placeholder.component
        Component componentComp = messageManager.get(
            "module-not-found",
            Placeholder.component("module", Component.text("LaserTag", NamedTextColor.GOLD))
        );
        String plainComponent = PlainTextComponentSerializer.plainText().serialize(componentComp);
        assertThat(plainComponent).contains("LaserTag").doesNotContain("<module>");
    }

    @Test
    @DisplayName("Should automatically inject <prefix> in configured messages and parse calls")
    void shouldAutomaticallyInjectPrefix() {
        Component reloadMsg = messageManager.get("reload-success");
        String plainReload = PlainTextComponentSerializer.plainText().serialize(reloadMsg);
        assertThat(plainReload).startsWith("DanaEvent »").contains("rechargés avec succès");

        Component customMsgWithPrefix = messageManager.parse("<prefix> <yellow>Custom notification</yellow>");
        String plainCustom = PlainTextComponentSerializer.plainText().serialize(customMsgWithPrefix);
        assertThat(plainCustom).startsWith("DanaEvent »").contains("Custom notification");
    }

    @Test
    @DisplayName("Should support multiline string list messages joined with newline")
    void shouldSupportMultilineMessages() throws IOException {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(messagesFile);
        yaml.set("help-menu", List.of(
            "<prefix> <gold>Aide DanaEvent :</gold>",
            "<gray>- /danaevent help : affiche l'aide</gray>",
            "<gray>- /danaevent reload : recharge les configs</gray>"
        ));
        yaml.save(messagesFile);

        messageManager.reload();

        Component helpComp = messageManager.get("help-menu");
        String plainHelp = PlainTextComponentSerializer.plainText().serialize(helpComp);
        assertThat(plainHelp)
            .contains("Aide DanaEvent :")
            .contains("- /danaevent help")
            .contains("- /danaevent reload")
            .contains("\n");
    }

    @Test
    @DisplayName("Should send message to PlayerMock and receive expected Component")
    void shouldSendMessageToPlayerMock() {
        PlayerMock player = server.addPlayer();

        messageManager.sendMessage(player, "reload-success");
        Component received = player.nextComponentMessage();
        assertThat(received).isNotNull();
        String plain = PlainTextComponentSerializer.plainText().serialize(received);
        assertThat(plain).contains("DanaEvent »", "rechargés avec succès");

        messageManager.sendRawMessage(player, "<green>Test raw direct message</green>");
        Component receivedRaw = player.nextComponentMessage();
        assertThat(receivedRaw).isNotNull();
        String plainRaw = PlainTextComponentSerializer.plainText().serialize(receivedRaw);
        assertThat(plainRaw).isEqualTo("Test raw direct message");
    }

    @Test
    @DisplayName("Should support hot reloading when messages.yml is modified")
    void shouldSupportHotReloading() throws IOException {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(messagesFile);
        yaml.set("prefix", "<yellow>[DanaNew]</yellow>");
        yaml.set("custom-key", "<prefix> <aqua>Nouveau message</aqua>");
        yaml.save(messagesFile);

        messageManager.reload();

        Component customComp = messageManager.get("custom-key");
        String plainCustom = PlainTextComponentSerializer.plainText().serialize(customComp);
        assertThat(plainCustom).isEqualTo("[DanaNew] Nouveau message");
    }

    @Test
    @DisplayName("Should return fallback component for missing keys without throwing exception")
    void shouldReturnFallbackForMissingKeys() {
        Component missing = messageManager.get("unknown.test.key");
        assertThat(missing).isNotNull();
        String plain = PlainTextComponentSerializer.plainText().serialize(missing);
        assertThat(plain).isEqualTo("unknown.test.key");
    }

    @Test
    @DisplayName("Should handle null and empty inputs safely")
    void shouldHandleNullAndEmptyInputsSafely() {
        assertThat(messageManager.parse(null)).isEqualTo(Component.empty());
        assertThat(messageManager.parse("")).isEqualTo(Component.empty());
        assertThat(messageManager.get(null)).isEqualTo(Component.empty());

        // Should not throw NPE when sending to null audience
        messageManager.sendMessage(null, "reload-success");
        messageManager.sendRawMessage(null, "<red>test</red>");
    }

    @Test
    @DisplayName("Should allow custom prefix tag to override default prefix")
    void shouldAllowCustomPrefixOverride() {
        Component comp = messageManager.parse(
            "<prefix> Message",
            Placeholder.parsed("prefix", "[CustomPrefix]")
        );
        String plain = PlainTextComponentSerializer.plainText().serialize(comp);
        assertThat(plain).isEqualTo("[CustomPrefix] Message");
    }

    @Test
    @DisplayName("Should fallback to embedded jar defaults when key is missing on disk")
    void shouldFallbackToEmbeddedJarDefaults() throws IOException {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        // Overwrite disk file with an empty configuration
        Files.writeString(messagesFile.toPath(), "# Empty file\n");

        messageManager.reload();

        // Key should still be resolved from jar default resources
        Component reloadSuccess = messageManager.get("reload-success");
        String plain = PlainTextComponentSerializer.plainText().serialize(reloadSuccess);
        assertThat(plain).contains("DanaEvent »", "rechargés avec succès");
    }
}
