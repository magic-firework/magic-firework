package com.magic.firework.screen;

import com.magic.firework.registry.ModRegistries;

import net.fabricmc.fabric.api.attachment.v1.AttachmentTarget;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The firework editor container.
 *
 * <p>One centre slot accepts the magic firework being edited; the rest is the standard
 * player inventory (27 slots + 9 hotbar). The firework stays in the editor across sessions:
 * it is saved to the player's persistent data when the menu closes (or the player
 * disconnects) and restored when the menu reopens, like a furnace keeping its contents
 * instead of dropping them.
 */
public class FireworkEditorMenu extends AbstractContainerMenu {
    public static final int CENTER_SLOT = 0;
    private static final int INV_START = 1;
    private static final int INV_END = 27; // inclusive
    private static final int HOTBAR_START = 28;
    private static final int HOTBAR_END = 36; // inclusive

    /** Centre slot position, relative to the screen's leftPos/topPos. */
    public static final int CENTER_X = 43;
    public static final int CENTER_Y = 107;

    private final Container center = new SimpleContainer(1);

    public FireworkEditorMenu(int containerId, Inventory playerInventory) {
        super(ModRegistries.FIREWORK_EDITOR_MENU, containerId);

        if (playerInventory.player instanceof ServerPlayer serverPlayer) {
            this.center.setItem(0, loadStored(serverPlayer));
        }

        this.addSlot(new Slot(center, 0, CENTER_X, CENTER_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.is(ModRegistries.MAGIC_FIREWORK_ITEM);
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 109 + col * 18, 182 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 109 + col * 18, 236));
        }
    }

    /** @return the firework currently in the centre slot (may be empty) */
    public ItemStack getCenterStack() {
        return center.getItem(0);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();
            if (index == CENTER_SLOT) {
                if (!this.moveItemStackTo(stack, INV_START, HOTBAR_END + 1, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (index >= INV_START && index <= HOTBAR_END) {
                if (stack.is(ModRegistries.MAGIC_FIREWORK_ITEM)) {
                    if (!this.moveItemStackTo(stack, CENTER_SLOT, CENTER_SLOT + 1, false)
                            && !this.moveItemStackTo(stack, HOTBAR_START, HOTBAR_END + 1, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (!this.moveItemStackTo(stack, HOTBAR_START, HOTBAR_END + 1, false)) {
                    return ItemStack.EMPTY;
                }
            }
            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }
            if (stack.getCount() == result.getCount()) {
                return ItemStack.EMPTY;
            }
            slot.onTake(player, stack);
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return true;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (player instanceof ServerPlayer serverPlayer) {
            store(serverPlayer, this.center.getItem(0));
        }
    }

    /**
     * Persist the centre firework so it survives the session. Called from the disconnect hook
     * so it happens before the player is saved, and from {@link #removed} on a normal close —
     * neither path returns or drops the firework; it stays in the editor for next time.
     */
    public void saveContents(Player player) {
        if (player instanceof ServerPlayer serverPlayer) {
            store(serverPlayer, this.center.getItem(0));
        }
    }

    private static ItemStack loadStored(ServerPlayer player) {
        return ((AttachmentTarget) player).getAttachedOrElse(ModRegistries.EDITOR_FIREWORK, ItemStack.EMPTY);
    }

    private static void store(ServerPlayer player, ItemStack stack) {
        AttachmentTarget target = (AttachmentTarget) player;
        if (stack.isEmpty()) {
            target.removeAttached(ModRegistries.EDITOR_FIREWORK);
        } else {
            target.setAttached(ModRegistries.EDITOR_FIREWORK, stack);
        }
    }
}
