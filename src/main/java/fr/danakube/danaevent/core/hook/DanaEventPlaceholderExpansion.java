package fr.danakube.danaevent.core.hook;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.modules.boatrace.manager.BoatRaceLeaderboardManager;
import fr.danakube.danaevent.modules.boatrace.model.RecordEntry;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * PlaceholderAPI expansion for DanaEvent.
 * Provides placeholders:
 * - %danaevent_active_modules%
 * - %danaevent_total_modules%
 * - %danaevent_in_event%
 * - %danaevent_version%
 * - %danaevent_boatrace_<track>_pb%
 * - %danaevent_boatrace_<track>_top1_name%
 * - %danaevent_boatrace_<track>_top1_time%
 * - %danaevent_boatrace_<track>_monthly_top1_name%
 * - %danaevent_boatrace_<track>_monthly_top1_time%
 */
public class DanaEventPlaceholderExpansion extends PlaceholderExpansion {

    private final DanaEventPlugin plugin;

    public DanaEventPlaceholderExpansion(DanaEventPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "danaevent";
    }

    @Override
    public @NotNull String getAuthor() {
        return "DanaKube";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        return resolvePlaceholder(offlinePlayer, params);
    }

    @Override
    public @Nullable String onPlaceholderRequest(Player player, @NotNull String params) {
        return resolvePlaceholder(player, params);
    }

    public @Nullable String resolvePlaceholder(OfflinePlayer player, @NotNull String params) {
        if (params.equalsIgnoreCase("active_modules")) {
            return String.valueOf(plugin.getModuleManager() != null ? plugin.getModuleManager().getEnabledModules().size() : 0);
        }

        if (params.equalsIgnoreCase("total_modules")) {
            return String.valueOf(plugin.getModuleManager() != null ? plugin.getModuleManager().getModules().size() : 0);
        }

        if (params.equalsIgnoreCase("in_event")) {
            if (player == null || plugin.getPlayerStateManager() == null) {
                return "false";
            }
            return String.valueOf(plugin.getPlayerStateManager().hasSnapshot(player.getUniqueId()));
        }

        if (params.equalsIgnoreCase("version")) {
            return plugin.getPluginMeta().getVersion();
        }

        String lower = params.toLowerCase();
        if (lower.startsWith("boatrace_")) {
            return resolveBoatRacePlaceholder(player, params.substring("boatrace_".length()));
        }

        if (lower.startsWith("hunt_") || lower.startsWith("treasurehunt_")) {
            String prefix = lower.startsWith("hunt_") ? "hunt_" : "treasurehunt_";
            return resolveTreasureHuntPlaceholder(player, params.substring(prefix.length()));
        }

        return null;
    }

    private @Nullable String resolveBoatRacePlaceholder(OfflinePlayer player, String sub) {
        BoatRaceLeaderboardManager m = plugin != null ? plugin.getBoatRaceLeaderboardManager() : null;
        if (m == null) {
            m = BoatRaceLeaderboardManager.getInstance();
        }
        if (m == null) {
            return "N/A";
        }
        final BoatRaceLeaderboardManager manager = m;

        String lower = sub.toLowerCase();

        // 1. %danaevent_boatrace_<track>_monthly_top1_name%
        if (lower.endsWith("_monthly_top1_name")) {
            String trackId = sub.substring(0, sub.length() - "_monthly_top1_name".length());
            return manager.getCachedTop1Monthly(trackId)
                .map(r -> manager.resolvePlayerName(r.playerUuid()))
                .orElse("N/A");
        }

        // 2. %danaevent_boatrace_<track>_monthly_top1_time%
        if (lower.endsWith("_monthly_top1_time")) {
            String trackId = sub.substring(0, sub.length() - "_monthly_top1_time".length());
            return manager.getCachedTop1Monthly(trackId)
                .map(RecordEntry::formatTime)
                .orElse("N/A");
        }

        // 3. %danaevent_boatrace_<track>_top1_name%
        if (lower.endsWith("_top1_name")) {
            String trackId = sub.substring(0, sub.length() - "_top1_name".length());
            return manager.getCachedTop1AllTime(trackId)
                .map(r -> manager.resolvePlayerName(r.playerUuid()))
                .orElse("N/A");
        }

        // 4. %danaevent_boatrace_<track>_top1_time%
        if (lower.endsWith("_top1_time")) {
            String trackId = sub.substring(0, sub.length() - "_top1_time".length());
            return manager.getCachedTop1AllTime(trackId)
                .map(RecordEntry::formatTime)
                .orElse("N/A");
        }

        // 5. %danaevent_boatrace_<track>_pb%
        if (lower.endsWith("_pb")) {
            String trackId = sub.substring(0, sub.length() - "_pb".length());
            if (player == null) {
                return "N/A";
            }
            return manager.getCachedPersonalBest(trackId, player.getUniqueId())
                .map(RecordEntry::formatTime)
                .orElse("N/A");
        }

        return null;
    }

    private @Nullable String resolveTreasureHuntPlaceholder(OfflinePlayer player, String sub) {
        fr.danakube.danaevent.modules.treasurehunt.manager.HuntLeaderboardManager lm =
            fr.danakube.danaevent.modules.treasurehunt.manager.HuntLeaderboardManager.getInstance();
        fr.danakube.danaevent.modules.treasurehunt.TreasureHuntModule thModule =
            plugin != null ? plugin.getTreasureHuntModule() : null;

        String lower = sub.toLowerCase();

        // 1. %danaevent_hunt_<hunt>_progress%
        if (lower.endsWith("_progress")) {
            String huntId = sub.substring(0, sub.length() - "_progress".length());
            if (player == null || !player.isOnline() || thModule == null) {
                return "N/A";
            }
            Player onlinePlayer = player.getPlayer();
            if (onlinePlayer == null) {
                return "N/A";
            }
            return thModule.getProgressManager().getProgressForPlayer(onlinePlayer)
                .filter(p -> p.getHuntId().equalsIgnoreCase(huntId))
                .map(p -> (p.getCurrentStepIndex() + 1) + "/" + p.getStepOrder().size())
                .orElse("N/A");
        }

        if (lm == null) {
            return "N/A";
        }

        // 2. %danaevent_hunt_<hunt>_top1_name%
        if (lower.endsWith("_top1_name")) {
            String huntId = sub.substring(0, sub.length() - "_top1_name".length());
            return lm.getCachedTop1AllTime(huntId)
                .map(r -> lm.resolveHolderName(r.holderUuid(), r.isTeam()))
                .orElse("N/A");
        }

        // 3. %danaevent_hunt_<hunt>_top1_time%
        if (lower.endsWith("_top1_time")) {
            String huntId = sub.substring(0, sub.length() - "_top1_time".length());
            return lm.getCachedTop1AllTime(huntId)
                .map(r -> fr.danakube.danaevent.modules.treasurehunt.model.PlayerHuntProgress.formatTime(r.timeMillis()))
                .orElse("N/A");
        }

        return null;
    }
}
