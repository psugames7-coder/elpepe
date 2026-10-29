package com.kephale.psuattributes;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.LoreComponent;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.ScreenHandlerType;
import net.minecraft.screen.SimpleNamedScreenHandlerFactory;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.util.List;

public final class HudMenu {

    private HudMenu() {}

    private static final int FIRST_OPTION = 2;
    private static final int ROWS = 4;
    private static final int SIZE = ROWS * 9;

    public static void open(ServerPlayerEntity player) {
        SimpleInventory inv = new SimpleInventory(SIZE);
        player.openHandledScreen(new SimpleNamedScreenHandlerFactory(
                (syncId, playerInv, ignored) -> new Handler(syncId, playerInv, inv, player),
                Text.literal("HUD layout")));
    }

    private static Item elementIcon(Hud.Element element) {
        return switch (element) {
            case COMBAT -> Items.IRON_SWORD;
            case FLIGHT -> Items.ELYTRA;
            case ABILITY -> Items.NETHER_STAR;
            case COOLDOWN -> Items.CLOCK;
        };
    }

    private static Item spotIcon(Hud.Spot spot) {
        return switch (spot) {
            case ACTION_BAR -> Items.NAME_TAG;
            case BOSS_BAR -> Items.DRAGON_HEAD;
            case SUBTITLE -> Items.OAK_SIGN;
            case CHAT -> Items.WRITABLE_BOOK;
            case OFF -> Items.BARRIER;
        };
    }

    private static final class Handler extends GenericContainerScreenHandler {

        private final SimpleInventory inv;
        private final ServerPlayerEntity owner;

        Handler(int syncId, PlayerInventory playerInv, SimpleInventory inv, ServerPlayerEntity owner) {
            super(ScreenHandlerType.GENERIC_9X4, syncId, playerInv, inv, ROWS);
            this.inv = inv;
            this.owner = owner;
            render();
        }

        @Override
        public void onSlotClick(int slot, int button, SlotActionType action, PlayerEntity player) {
            if (slot < 0 || slot >= SIZE) return;
            if (action != SlotActionType.PICKUP && action != SlotActionType.QUICK_MOVE) return;
            try {
                int row = slot / 9;
                int col = slot % 9 - FIRST_OPTION;
                Hud.Element[] elements = Hud.Element.values();
                Hud.Spot[] spots = Hud.Spot.values();
                if (row < elements.length && col >= 0 && col < spots.length) {
                    Hud.setSpot(owner, elements[row], spots[col]);
                }
            } catch (Throwable t) {
                PsuAttributes.LOGGER.warn("PsuAttributes: hud menu click failed", t);
            }
            render();
            syncState();
        }

        private void render() {
            for (int i = 0; i < SIZE; i++) inv.setStack(i, filler());
            Hud.Element[] elements = Hud.Element.values();
            Hud.Spot[] spots = Hud.Spot.values();
            for (int row = 0; row < elements.length; row++) {
                Hud.Element element = elements[row];
                Hud.Spot current = Hud.spot(owner, element);
                inv.setStack(row * 9, named(elementIcon(element), "§6" + element.label,
                        line("§7Now: §f" + current.label)));
                for (int i = 0; i < spots.length; i++) {
                    Hud.Spot spot = spots[i];
                    if (!Hud.allowed(element, spot)) continue;
                    boolean on = spot == current;
                    ItemStack stack = named(spotIcon(spot), (on ? "§a" : "§f") + spot.label,
                            line(on ? "§aSelected" : "§7Click to select"));
                    if (on) stack.set(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true);
                    inv.setStack(row * 9 + FIRST_OPTION + i, stack);
                }
            }
        }
    }

    private static ItemStack named(Item item, String name, Text... lore) {
        ItemStack s = new ItemStack(item);
        s.set(DataComponentTypes.CUSTOM_NAME, line(name));
        if (lore.length > 0) s.set(DataComponentTypes.LORE, new LoreComponent(List.of(lore)));
        return s;
    }

    private static ItemStack filler() {
        ItemStack s = new ItemStack(Items.BLACK_STAINED_GLASS_PANE);
        s.set(DataComponentTypes.CUSTOM_NAME, Text.literal(" "));
        return s;
    }

    private static Text line(String s) {
        return Text.literal(s).styled(st -> st.withItalic(false));
    }
}
