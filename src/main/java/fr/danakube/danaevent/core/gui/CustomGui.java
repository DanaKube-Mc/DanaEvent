package fr.danakube.danaevent.core.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Custom inventory GUI supporting MiniMessage titles, click handling,
 * auto borders, filling, pagination, and theft prevention.
 */
public class CustomGui implements InventoryHolder {

    private final Component title;
    private final int size;
    private final Inventory inventory;
    private final Map<Integer, GuiItem> items = new HashMap<>();

    private boolean cancelClicks = true;
    private Consumer<Player> closeCallback;

    // Pagination
    private int page = 1;
    private int maxPages = 1;
    private Consumer<Integer> onPageChange;

    public CustomGui(@NotNull Component title, int size) {
        Objects.requireNonNull(title, "title cannot be null");
        if (size < 9 || size > 54 || size % 9 != 0) {
            throw new IllegalArgumentException("Inventory size must be a multiple of 9 between 9 and 54. Given: " + size);
        }
        this.title = title;
        this.size = size;
        Inventory inv;
        try {
            inv = Bukkit.createInventory(this, size, title);
        } catch (Throwable t) {
            // Fallback for test environments (e.g. MockBukkit lacking Adventure Component createInventory)
            String legacyTitle = net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().serialize(title);
            inv = Bukkit.createInventory(this, size, legacyTitle);
        }
        this.inventory = inv;
    }

    public CustomGui(@NotNull String miniMessageTitle, int size) {
        this(MiniMessage.miniMessage().deserialize(Objects.requireNonNull(miniMessageTitle, "miniMessageTitle cannot be null")), size);
    }

    /**
     * Sets an item at the specified slot with a click callback.
     *
     * @param slot    inventory slot (0 to size-1)
     * @param item    the ItemStack to display
     * @param onClick callback executed on click
     */
    public void setItem(int slot, @Nullable ItemStack item, @Nullable Consumer<InventoryClickEvent> onClick) {
        validateSlot(slot);
        if (item == null) {
            removeItem(slot);
            return;
        }
        GuiItem guiItem = new GuiItem(item, onClick);
        items.put(slot, guiItem);
        inventory.setItem(slot, item);
    }

    /**
     * Sets a static item at the specified slot without click callback.
     *
     * @param slot inventory slot (0 to size-1)
     * @param item the ItemStack to display
     */
    public void setItem(int slot, @Nullable ItemStack item) {
        setItem(slot, item, null);
    }

    /**
     * Sets a GuiItem at the specified slot.
     *
     * @param slot    inventory slot (0 to size-1)
     * @param guiItem the GuiItem
     */
    public void setItem(int slot, @Nullable GuiItem guiItem) {
        validateSlot(slot);
        if (guiItem == null) {
            removeItem(slot);
            return;
        }
        items.put(slot, guiItem);
        inventory.setItem(slot, guiItem.getItemStack());
    }

    /**
     * Retrieves the GuiItem at a given slot.
     *
     * @param slot inventory slot
     * @return GuiItem or null if empty
     */
    public @Nullable GuiItem getItem(int slot) {
        return items.get(slot);
    }

    /**
     * Removes the item at the specified slot.
     *
     * @param slot inventory slot
     */
    public void removeItem(int slot) {
        validateSlot(slot);
        items.remove(slot);
        inventory.setItem(slot, null);
    }

    /**
     * Clears all items and callbacks from the GUI.
     */
    public void clear() {
        items.clear();
        inventory.clear();
    }

    /**
     * Fills all empty slots with the provided filler item.
     *
     * @param fillerItem ItemStack used to fill empty slots
     */
    public void fill(@NotNull ItemStack fillerItem) {
        Objects.requireNonNull(fillerItem, "fillerItem cannot be null");
        for (int i = 0; i < size; i++) {
            if (!items.containsKey(i) && (inventory.getItem(i) == null || inventory.getItem(i).getType().isAir())) {
                setItem(i, fillerItem.clone());
            }
        }
    }

    /**
     * Fills the GUI borders (first row, last row, first and last columns) with the given item.
     *
     * @param borderItem ItemStack used for borders
     */
    public void fillBorder(@NotNull ItemStack borderItem) {
        Objects.requireNonNull(borderItem, "borderItem cannot be null");
        int rows = size / 9;
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < 9; col++) {
                if (row == 0 || row == rows - 1 || col == 0 || col == 8) {
                    int slot = row * 9 + col;
                    setItem(slot, borderItem.clone());
                }
            }
        }
    }

    // --- Pagination ---

    public int getPage() {
        return page;
    }

    public void setPage(int page) {
        int target = Math.max(1, Math.min(page, maxPages));
        if (this.page != target) {
            this.page = target;
            if (onPageChange != null) {
                onPageChange.accept(this.page);
            }
        }
    }

    public int getMaxPages() {
        return maxPages;
    }

    public void setMaxPages(int maxPages) {
        this.maxPages = Math.max(1, maxPages);
        if (this.page > this.maxPages) {
            setPage(this.maxPages);
        }
    }

    public boolean hasPreviousPage() {
        return page > 1;
    }

    public boolean hasNextPage() {
        return page < maxPages;
    }

    public void nextPage() {
        if (hasNextPage()) {
            setPage(page + 1);
        }
    }

    public void previousPage() {
        if (hasPreviousPage()) {
            setPage(page - 1);
        }
    }

    public void setPreviousPageButton(int slot, @NotNull ItemStack item) {
        setItem(slot, item, event -> previousPage());
    }

    public void setNextPageButton(int slot, @NotNull ItemStack item) {
        setItem(slot, item, event -> nextPage());
    }

    public void setPaginationButtons(int prevSlot, @NotNull ItemStack prevItem, int nextSlot, @NotNull ItemStack nextItem) {
        setPreviousPageButton(prevSlot, prevItem);
        setNextPageButton(nextSlot, nextItem);
    }

    public void setOnPageChange(@Nullable Consumer<Integer> onPageChange) {
        this.onPageChange = onPageChange;
    }

    // --- Anti-theft & Events ---

    public boolean isCancelClicks() {
        return cancelClicks;
    }

    public void setCancelClicks(boolean cancelClicks) {
        this.cancelClicks = cancelClicks;
    }

    public void setOnClose(@Nullable Consumer<Player> closeCallback) {
        this.closeCallback = closeCallback;
    }

    public void onClose(@NotNull Player player) {
        if (closeCallback != null) {
            closeCallback.accept(player);
        }
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }

    public int getSize() {
        return size;
    }

    public @NotNull Component getTitle() {
        return title;
    }

    public @NotNull Map<Integer, GuiItem> getItems() {
        return Collections.unmodifiableMap(items);
    }

    private void validateSlot(int slot) {
        if (slot < 0 || slot >= size) {
            throw new IndexOutOfBoundsException("Slot " + slot + " is out of bounds for GUI size " + size);
        }
    }
}
