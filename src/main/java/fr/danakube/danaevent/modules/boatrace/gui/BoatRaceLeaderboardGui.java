package fr.danakube.danaevent.modules.boatrace.gui;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.gui.CustomGui;
import fr.danakube.danaevent.modules.boatrace.manager.BoatRaceLeaderboardManager;
import fr.danakube.danaevent.modules.boatrace.model.RecordEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;

/**
 * Interactive 54-slot CustomGui displaying the BoatRace track leaderboard.
 * Supports filtering between Monthly and All-Time scopes, player head badges,
 * formatted times, and click navigation.
 */
public class BoatRaceLeaderboardGui {

    public static final int SIZE = 54;
    public static final int FILTER_SLOT = 48;
    public static final int CLOSE_SLOT = 50;
    public static final int EMPTY_INFO_SLOT = 22;

    public static final int[] CENTRAL_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
        .withZone(ZoneId.systemDefault());

    public enum Scope {
        MONTHLY,
        ALL_TIME
    }

    private final DanaEventPlugin plugin;
    private final BoatRaceLeaderboardManager leaderboardManager;
    private final String trackId;
    private final CustomGui customGui;
    private Scope scope;

    public BoatRaceLeaderboardGui(DanaEventPlugin plugin, BoatRaceLeaderboardManager leaderboardManager, String trackId) {
        this(plugin, leaderboardManager, trackId, Scope.MONTHLY);
    }

    public BoatRaceLeaderboardGui(DanaEventPlugin plugin, BoatRaceLeaderboardManager leaderboardManager, String trackId, Scope initialScope) {
        this.plugin = plugin;
        this.leaderboardManager = Objects.requireNonNull(leaderboardManager, "leaderboardManager cannot be null");
        this.trackId = Objects.requireNonNull(trackId, "trackId cannot be null");
        this.scope = initialScope != null ? initialScope : Scope.MONTHLY;

        Component title = MiniMessage.miniMessage().deserialize(
            "<gradient:#00c6ff:#0072ff><bold>Classement - </bold></gradient><white>" + trackId + "</white>"
        );
        this.customGui = new CustomGui(title, SIZE);
    }

    public Scope getScope() {
        return scope;
    }

    public void setScope(Scope scope) {
        if (scope != null && this.scope != scope) {
            this.scope = scope;
            refresh();
        }
    }

    public CustomGui getCustomGui() {
        return customGui;
    }

    public String getTrackId() {
        return trackId;
    }

    /**
     * Refreshes the leaderboard data asynchronously and updates GUI inventory contents.
     *
     * @return CompletableFuture completing when UI has been rendered
     */
    public CompletableFuture<Void> refresh() {
        CompletableFuture<List<RecordEntry>> future = (scope == Scope.MONTHLY)
            ? leaderboardManager.getTopMonthly(trackId, CENTRAL_SLOTS.length)
            : leaderboardManager.getTopAllTime(trackId, CENTRAL_SLOTS.length);

        return future.thenAccept(records -> {
            if (Bukkit.isPrimaryThread()) {
                render(records);
            } else if (plugin != null && plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, () -> render(records));
            } else {
                render(records);
            }
        });
    }

    /**
     * Opens this GUI for the specified player.
     *
     * @param player player to view the GUI
     * @return CompletableFuture completing when the GUI is opened
     */
    public CompletableFuture<Void> open(Player player) {
        Objects.requireNonNull(player, "player cannot be null");
        return refresh().thenRun(() -> {
            Runnable openTask = () -> {
                if (plugin != null && plugin.getGuiManager() != null) {
                    plugin.getGuiManager().openGui(player, customGui);
                } else {
                    player.openInventory(customGui.getInventory());
                }
            };

            if (Bukkit.isPrimaryThread()) {
                openTask.run();
            } else if (plugin != null && plugin.isEnabled()) {
                Bukkit.getScheduler().runTask(plugin, openTask);
            } else {
                openTask.run();
            }
        });
    }

    /**
     * Renders the full inventory layout with current scope and records.
     *
     * @param records ordered leaderboard records
     */
    public void render(List<RecordEntry> records) {
        customGui.clear();

        // 1. Fill borders with glass pane
        ItemStack border = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var borderMeta = border.getItemMeta();
        if (borderMeta != null) {
            borderMeta.displayName(CustomGui.textWithoutItalic(""));
            border.setItemMeta(borderMeta);
        }
        customGui.fillBorder(border);

        // 2. Filter toggle button
        renderFilterButton();

        // 3. Close button
        renderCloseButton();

        // 4. Central slots / records
        if (records == null || records.isEmpty()) {
            renderEmptyNotice();
        } else {
            renderRecords(records);
        }
    }

    private void renderFilterButton() {
        ItemStack filterItem;
        String titleStr;
        List<String> loreLines = new ArrayList<>();

        if (scope == Scope.MONTHLY) {
            filterItem = new ItemStack(Material.CLOCK);
            titleStr = "<gold><bold>Filtre : </bold><yellow>Mensuel</yellow></gold>";
            loreLines.add("<gray>Affichage : </gray><yellow>Meilleurs temps de ce mois</yellow>");
            loreLines.add("<dark_gray>Période : </dark_gray><aqua>" + leaderboardManager.getCurrentPeriodMonth() + "</aqua>");
            loreLines.add("");
            loreLines.add("<yellow>▸ Cliquez pour afficher Tous les temps</yellow>");
        } else {
            filterItem = new ItemStack(Material.NETHER_STAR);
            titleStr = "<gold><bold>Filtre : </bold><aqua>Tous les temps</aqua></gold>";
            loreLines.add("<gray>Affichage : </gray><aqua>Records historiques globaux</aqua>");
            loreLines.add("");
            loreLines.add("<yellow>▸ Cliquez pour afficher le classement Mensuel</yellow>");
        }

        var meta = filterItem.getItemMeta();
        if (meta != null) {
            meta.displayName(CustomGui.textWithoutItalic(titleStr));
            meta.lore(loreLines.stream().map(CustomGui::textWithoutItalic).toList());
            filterItem.setItemMeta(meta);
        }

        customGui.setItem(FILTER_SLOT, filterItem, event -> {
            this.scope = (this.scope == Scope.MONTHLY) ? Scope.ALL_TIME : Scope.MONTHLY;
            refresh();
        });
    }

    private void renderCloseButton() {
        ItemStack closeItem = new ItemStack(Material.BARRIER);
        var meta = closeItem.getItemMeta();
        if (meta != null) {
            meta.displayName(CustomGui.textWithoutItalic("<red><bold>Fermer</bold></red>"));
            meta.lore(List.of(CustomGui.textWithoutItalic("<gray>Cliquez pour fermer l'inventaire.</gray>")));
            closeItem.setItemMeta(meta);
        }

        customGui.setItem(CLOSE_SLOT, closeItem, event -> {
            if (event.getWhoClicked() instanceof Player player) {
                player.closeInventory();
            }
        });
    }

    private void renderEmptyNotice() {
        ItemStack emptyItem = new ItemStack(Material.SPYGLASS);
        var meta = emptyItem.getItemMeta();
        if (meta != null) {
            meta.displayName(CustomGui.textWithoutItalic("<red><bold>Aucun record</bold></red>"));
            meta.lore(List.of(
                CustomGui.textWithoutItalic("<gray>Aucun temps n'a été enregistré pour ce classement.</gray>"),
                CustomGui.textWithoutItalic("<yellow>Soyez le premier à franchir la ligne d'arrivée !</yellow>")
            ));
            emptyItem.setItemMeta(meta);
        }
        customGui.setItem(EMPTY_INFO_SLOT, emptyItem);
    }

    private void renderRecords(List<RecordEntry> records) {
        int count = Math.min(records.size(), CENTRAL_SLOTS.length);
        for (int i = 0; i < count; i++) {
            RecordEntry record = records.get(i);
            int slot = CENTRAL_SLOTS[i];
            int rank = i + 1;

            String playerName = leaderboardManager.resolvePlayerName(record.playerUuid());
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            var meta = head.getItemMeta();

            if (meta instanceof SkullMeta skullMeta) {
                try {
                    skullMeta.setOwningPlayer(Bukkit.getOfflinePlayer(record.playerUuid()));
                } catch (Throwable ignored) {
                }
            }

            String rankTitle;
            if (rank == 1) {
                rankTitle = "<gold><bold>#1 </bold><yellow>" + playerName + "</yellow></gold>";
            } else if (rank == 2) {
                rankTitle = "<white><bold>#2 </bold><gray>" + playerName + "</gray></white>";
            } else if (rank == 3) {
                rankTitle = "<gold><bold>#3 </bold><color:#cd7f32>" + playerName + "</color></gold>";
            } else {
                rankTitle = "<dark_aqua><bold>#" + rank + " </bold><white>" + playerName + "</white></dark_aqua>";
            }

            List<Component> lore = new ArrayList<>();
            lore.add(CustomGui.textWithoutItalic("<gray>Temps : </gray><green>" + record.formatTime() + "</green>"));
            lore.add(CustomGui.textWithoutItalic("<gray>Tours : </gray><white>" + record.laps() + " tour" + (record.laps() > 1 ? "s" : "") + "</white>"));
            lore.add(CustomGui.textWithoutItalic("<gray>Date : </gray><dark_gray>" + DATE_FORMATTER.format(record.createdAt()) + "</dark_gray>"));
            lore.add(CustomGui.textWithoutItalic("<gray>Période : </gray><aqua>" + record.periodMonth() + "</aqua>"));

            if (meta != null) {
                meta.displayName(CustomGui.textWithoutItalic(rankTitle));
                meta.lore(lore);
                head.setItemMeta(meta);
            }

            customGui.setItem(slot, head);
        }
    }
}
