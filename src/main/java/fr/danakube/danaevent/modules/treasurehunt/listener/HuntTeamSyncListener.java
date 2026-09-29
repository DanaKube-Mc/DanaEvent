package fr.danakube.danaevent.modules.treasurehunt.listener;

import fr.danakube.danaevent.DanaEventPlugin;
import fr.danakube.danaevent.core.team.event.TeamDisbandEvent;
import fr.danakube.danaevent.core.team.model.DanaTeam;
import fr.danakube.danaevent.modules.treasurehunt.config.HuntConfig;
import fr.danakube.danaevent.modules.treasurehunt.manager.HuntProgressManager;
import fr.danakube.danaevent.modules.treasurehunt.model.Hunt;
import fr.danakube.danaevent.modules.treasurehunt.model.PlayerHuntProgress;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles team coordination, instant progress propagation, disconnect recovery,
 * and cancellation when teams are disbanded.
 */
public class HuntTeamSyncListener implements Listener {

    private final DanaEventPlugin plugin;
    private final HuntProgressManager progressManager;
    private final HuntConfig huntConfig;

    public HuntTeamSyncListener(
        @NotNull DanaEventPlugin plugin,
        @NotNull HuntProgressManager progressManager,
        @NotNull HuntConfig huntConfig
    ) {
        this.plugin = Objects.requireNonNull(plugin, "plugin cannot be null");
        this.progressManager = Objects.requireNonNull(progressManager, "progressManager cannot be null");
        this.huntConfig = Objects.requireNonNull(huntConfig, "huntConfig cannot be null");
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        // 1. Check if player belongs to a team with an ongoing hunt
        if (plugin.getTeamManager() != null) {
            Optional<DanaTeam> teamOpt = plugin.getTeamManager().getPlayerTeam(playerUuid);
            if (teamOpt.isPresent()) {
                DanaTeam team = teamOpt.get();
                UUID teamUuid = HuntProgressManager.getTeamUuid(team.getId());

                progressManager.loadOrResumeProgress(teamUuid).thenAccept(opt -> {
                    if (opt.isPresent() && player.isOnline()) {
                        PlayerHuntProgress progress = opt.get();
                        Optional<Hunt> huntOpt = huntConfig.getHunt(progress.getHuntId());
                        String huntName = huntOpt.map(Hunt::getDisplayName).orElse(progress.getHuntId());
                        int step = progress.getCurrentStepIndex() + 1;
                        int total = progress.getStepOrder().size();

                        plugin.getMessageManager().sendMessage(
                            player,
                            "hunt-team-reconnect",
                            Placeholder.parsed("hunt", huntName),
                            Placeholder.parsed("step", String.valueOf(step)),
                            Placeholder.parsed("total", String.valueOf(total))
                        );
                    }
                });
                return;
            }
        }

        // 2. Solo player: resume progress from DB if orphaned
        progressManager.loadOrResumeProgress(playerUuid).thenAccept(opt -> {
            if (opt.isPresent() && player.isOnline()) {
                PlayerHuntProgress progress = opt.get();
                Optional<Hunt> huntOpt = huntConfig.getHunt(progress.getHuntId());
                String huntName = huntOpt.map(Hunt::getDisplayName).orElse(progress.getHuntId());
                int step = progress.getCurrentStepIndex() + 1;
                int total = progress.getStepOrder().size();

                plugin.getMessageManager().sendMessage(
                    player,
                    "hunt-solo-reconnect",
                    Placeholder.parsed("hunt", huntName),
                    Placeholder.parsed("step", String.valueOf(step)),
                    Placeholder.parsed("total", String.valueOf(total))
                );
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Progress is persistently stored on every step advancement
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onTeamDisband(TeamDisbandEvent event) {
        DanaTeam team = event.getTeam();
        UUID teamUuid = HuntProgressManager.getTeamUuid(team.getId());

        if (progressManager.isParticipant(teamUuid)) {
            progressManager.cancelHunt(teamUuid);

            for (UUID memberUuid : team.getMembers().keySet()) {
                Player member = Bukkit.getPlayer(memberUuid);
                if (member != null && member.isOnline()) {
                    plugin.getMessageManager().sendMessage(member, "hunt-team-disbanded-cancelled");
                }
            }
        }
    }
}
