package fr.danakube.danaevent.core.message;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;

/**
 * Manages text components and messaging using Kyori Adventure MiniMessage.
 * Automatically injects the server prefix and loads/reloads messages.yml.
 */
public class MessageManager {

    private static final String DEFAULT_PREFIX = "<gradient:#00c6ff:#0072ff><b>DanaEvent</b></gradient> <dark_gray>»</dark_gray>";

    private final JavaPlugin plugin;
    private final MiniMessage miniMessage;
    private YamlConfiguration config;
    private String prefix;

    public MessageManager(JavaPlugin plugin) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.miniMessage = MiniMessage.miniMessage();
        reload();
    }

    /**
     * Loads or reloads messages.yml from disk, extracting default resource if absent.
     */
    public void reload() {
        File messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        if (!messagesFile.exists()) {
            plugin.saveResource("messages.yml", false);
        }

        this.config = YamlConfiguration.loadConfiguration(messagesFile);

        InputStream defStream = plugin.getResource("messages.yml");
        if (defStream != null) {
            try (InputStreamReader reader = new InputStreamReader(defStream, StandardCharsets.UTF_8)) {
                YamlConfiguration defConfig = YamlConfiguration.loadConfiguration(reader);
                this.config.setDefaults(defConfig);
            } catch (IOException e) {
                plugin.getLogger().log(Level.WARNING, "Failed to load default messages.yml resource", e);
            }
        }

        this.prefix = config.getString("prefix", DEFAULT_PREFIX);
    }

    /**
     * Parses a raw MiniMessage string, injecting default <prefix> and any supplied TagResolvers.
     *
     * @param message   the raw string containing MiniMessage tags
     * @param resolvers optional custom tag resolvers
     * @return the parsed Component
     */
    public Component parse(String message, TagResolver... resolvers) {
        if (message == null || message.isEmpty()) {
            return Component.empty();
        }

        TagResolver prefixResolver = Placeholder.parsed("prefix", this.prefix);
        TagResolver combined;

        if (resolvers == null || resolvers.length == 0) {
            combined = prefixResolver;
        } else {
            // User resolvers take precedence over default prefix resolver
            combined = TagResolver.resolver(prefixResolver, TagResolver.resolver(resolvers));
        }

        return miniMessage.deserialize(message, combined);
    }

    /**
     * Retrieves a configured message by key and parses it as a Component.
     * Supports single strings and multiline lists (joined by newlines).
     *
     * @param key       the configuration key in messages.yml
     * @param resolvers optional custom tag resolvers
     * @return the parsed Component, or Component.text(key) if not found
     */
    public Component get(String key, TagResolver... resolvers) {
        if (key == null) {
            return Component.empty();
        }

        if (config.isList(key)) {
            List<String> lines = config.getStringList(key);
            String joined = String.join("\n", lines);
            return parse(joined, resolvers);
        }

        if (config.contains(key)) {
            String raw = config.getString(key);
            if (raw == null) {
                return Component.text(key);
            }
            return parse(raw, resolvers);
        }

        return Component.text(key);
    }

    /**
     * Sends a configured message to an Audience (Player, ConsoleCommandSender, etc.).
     *
     * @param audience  the recipient audience
     * @param key       the message key in messages.yml
     * @param resolvers optional custom tag resolvers
     */
    public void sendMessage(Audience audience, String key, TagResolver... resolvers) {
        if (audience == null) {
            return;
        }
        audience.sendMessage(get(key, resolvers));
    }

    /**
     * Sends a raw MiniMessage string to an Audience.
     *
     * @param audience  the recipient audience
     * @param raw       the raw string to parse and send
     * @param resolvers optional custom tag resolvers
     */
    public void sendRawMessage(Audience audience, String raw, TagResolver... resolvers) {
        if (audience == null) {
            return;
        }
        audience.sendMessage(parse(raw, resolvers));
    }

    /**
     * @return the raw configured prefix string
     */
    public String getPrefix() {
        return prefix;
    }

    /**
     * @return the parsed prefix Component
     */
    public Component getPrefixComponent() {
        return parse(prefix);
    }

    /**
     * @return the underlying YamlConfiguration for messages.yml
     */
    public YamlConfiguration getConfig() {
        return config;
    }
}
