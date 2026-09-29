package fr.danakube.danaevent.modules.treasurehunt.display;

import fr.danakube.danaevent.core.gui.CustomGui;
import fr.danakube.danaevent.modules.treasurehunt.model.Hunt;
import fr.danakube.danaevent.modules.treasurehunt.model.HuntStep;
import fr.danakube.danaevent.modules.treasurehunt.model.PlayerHuntProgress;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Visual quest journal GUI displaying unlocked clues and progression for the active treasure hunt.
 * Strictly suppresses default Minecraft italics on all item names and lore lines.
 */
public class QuestJournalMenu extends CustomGui {

    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43
    };

    private final Hunt hunt;
    private final PlayerHuntProgress progress;

    public QuestJournalMenu(@NotNull Hunt hunt, @NotNull PlayerHuntProgress progress) {
        super(CustomGui.textWithoutItalic("<gradient:#f7971e:#ffd200><b>Journal de Quête</b></gradient>"), 54);
        this.hunt = Objects.requireNonNull(hunt, "hunt cannot be null");
        this.progress = Objects.requireNonNull(progress, "progress cannot be null");

        setupLayout();
    }

    private void setupLayout() {
        // Border
        ItemStack border = CustomGui.createItem(
            Material.GRAY_STAINED_GLASS_PANE,
            Component.text(" "),
            List.of()
        );
        fillBorder(border);

        // Header Info Item at slot 4
        List<Component> headerLore = new ArrayList<>();
        headerLore.add(CustomGui.textWithoutItalic("<dark_gray>────────────────</dark_gray>"));
        headerLore.add(CustomGui.textWithoutItalic("<gray>Chasse : <white>" + hunt.getDisplayName() + "</white></gray>"));
        headerLore.add(CustomGui.textWithoutItalic("<gray>Progression : <yellow>" + (progress.getCurrentStepIndex() + 1) + "/" + progress.getStepOrder().size() + "</yellow></gray>"));
        headerLore.add(CustomGui.textWithoutItalic("<gray>Temps écoulé : <white>" + progress.formatElapsedTime() + "</white></gray>"));
        headerLore.add(CustomGui.textWithoutItalic("<dark_gray>────────────────</dark_gray>"));

        ItemStack headerItem = CustomGui.createItem(
            Material.KNOWLEDGE_BOOK,
            CustomGui.textWithoutItalic("<gold><b>Journal d'Aventure</b></gold>"),
            headerLore
        );
        setItem(4, headerItem);

        // Close button at slot 49
        ItemStack closeItem = CustomGui.createItem(
            Material.BARRIER,
            CustomGui.textWithoutItalic("<red><b>Fermer</b></red>"),
            List.of(CustomGui.textWithoutItalic("<gray>Cliquez pour fermer le journal.</gray>"))
        );
        setItem(49, closeItem, event -> {
            if (event.getWhoClicked() instanceof Player player) {
                player.closeInventory();
            }
        });

        // Populate steps in inner content slots
        List<Integer> stepOrder = progress.getStepOrder();
        int activeIndex = progress.getCurrentStepIndex();

        for (int i = 0; i < stepOrder.size() && i < CONTENT_SLOTS.length; i++) {
            int slot = CONTENT_SLOTS[i];
            int stepNumber = stepOrder.get(i);
            Optional<HuntStep> stepOpt = hunt.getStep(stepNumber);
            String clueText = stepOpt.map(HuntStep::getClue).orElse("Indice inconnu");

            if (i < activeIndex) {
                // Completed Step
                List<Component> lore = List.of(
                    CustomGui.textWithoutItalic("<dark_gray>────────────────</dark_gray>"),
                    CustomGui.textWithoutItalic("<gray>Indice : <white>" + clueText + "</white></gray>"),
                    CustomGui.textWithoutItalic("<dark_gray>────────────────</dark_gray>"),
                    CustomGui.textWithoutItalic("<green>✔ Objectif accompli !</green>")
                );
                ItemStack item = CustomGui.createItem(
                    Material.ENCHANTED_BOOK,
                    CustomGui.textWithoutItalic("<green>✔ Étape " + (i + 1) + " (Complétée)</green>"),
                    lore
                );
                setItem(slot, item);
            } else if (i == activeIndex) {
                // Currently Active Step
                List<Component> lore = List.of(
                    CustomGui.textWithoutItalic("<dark_gray>────────────────</dark_gray>"),
                    CustomGui.textWithoutItalic("<yellow>Indice actuel :</yellow>"),
                    CustomGui.textWithoutItalic("<white>" + clueText + "</white>"),
                    CustomGui.textWithoutItalic("<dark_gray>────────────────</dark_gray>"),
                    CustomGui.textWithoutItalic("<yellow>Trouvez cet objectif pour progresser !</yellow>")
                );
                ItemStack item = CustomGui.createItem(
                    Material.WRITABLE_BOOK,
                    CustomGui.textWithoutItalic("<gold>▶ Étape " + (i + 1) + " (En cours)</gold>"),
                    lore
                );
                setItem(slot, item);
            } else {
                // Locked Step
                List<Component> lore = List.of(
                    CustomGui.textWithoutItalic("<dark_gray>────────────────</dark_gray>"),
                    CustomGui.textWithoutItalic("<dark_gray>Progression requise pour débloquer cet indice.</dark_gray>"),
                    CustomGui.textWithoutItalic("<dark_gray>────────────────</dark_gray>")
                );
                ItemStack item = CustomGui.createItem(
                    Material.IRON_BARS,
                    CustomGui.textWithoutItalic("<gray>🔒 Étape " + (i + 1) + " (Verrouillée)</gray>"),
                    lore
                );
                setItem(slot, item);
            }
        }
    }
}
