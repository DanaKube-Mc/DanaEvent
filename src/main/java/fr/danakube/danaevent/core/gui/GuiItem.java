package fr.danakube.danaevent.core.gui;

import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Encapsulates an ItemStack and its associated click action callback in a CustomGui.
 */
public class GuiItem {

    private final ItemStack itemStack;
    private final Consumer<InventoryClickEvent> onClick;

    public GuiItem(ItemStack itemStack, Consumer<InventoryClickEvent> onClick) {
        this.itemStack = Objects.requireNonNull(itemStack, "ItemStack cannot be null");
        this.onClick = onClick != null ? onClick : event -> {};
    }

    public GuiItem(ItemStack itemStack) {
        this(itemStack, null);
    }

    public static GuiItem of(ItemStack itemStack, Consumer<InventoryClickEvent> onClick) {
        return new GuiItem(itemStack, onClick);
    }

    public static GuiItem of(ItemStack itemStack) {
        return new GuiItem(itemStack);
    }

    public ItemStack getItemStack() {
        return itemStack;
    }

    public Consumer<InventoryClickEvent> getOnClick() {
        return onClick;
    }

    /**
     * Executes the click callback safely.
     *
     * @param event the inventory click event
     */
    public void handleClick(InventoryClickEvent event) {
        if (onClick != null) {
            onClick.accept(event);
        }
    }
}
