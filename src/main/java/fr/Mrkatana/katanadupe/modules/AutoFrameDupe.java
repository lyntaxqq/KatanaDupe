package fr.Mrkatana.katanadupe.modules;

import fr.Mrkatana.katanadupe.utils.Utils;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Categories;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.network.MeteorExecutor;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Hand;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.text.Text;
import net.minecraft.util.hit.HitResult;
import org.apache.commons.lang3.time.DurationFormatUtils;

import net.minecraft.util.hit.BlockHitResult;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AutoFrameDupe extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final SettingGroup sgSpeedrun = settings.createGroup("Speedrun");
    private final SettingGroup sgRender = settings.createGroup("Render");

    public final Setting<List<Item>> dupeItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("Dupelenecek eşyalar.")
        .defaultValue(Arrays.asList(Items.DIAMOND, Items.NETHERITE_INGOT, Items.EMERALD, Items.ANCIENT_DEBRIS,
            Items.SHULKER_SHELL, Items.ELYTRA, Items.DIAMOND_BLOCK, Items.NETHERITE_BLOCK,
            Items.EMERALD_BLOCK, Items.ENCHANTED_GOLDEN_APPLE, Items.DRAGON_EGG, Items.TOTEM_OF_UNDYING,
            Items.BEACON, Items.NETHER_STAR, Items.TRIDENT, Items.MACE, Items.END_CRYSTAL))
        .build()
    );

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode")
        .description("Dupe modu.")
        .defaultValue(Mode.Normal)
        .build()
    );

    private final Setting<Integer> minStackSize = sgGeneral.add(new IntSetting.Builder()
        .name("min-stack-size")
        .description("Dupe için minimum stack miktarı (daha düşük = daha hızlı ama envanter yönetimi artar).")
        .defaultValue(1)
        .min(1)
        .sliderMax(64)
        .visible(() -> mode.get() == Mode.Fast)
        .build()
    );

    private final Setting<Boolean> replaceItemFrames = sgGeneral.add(new BoolSetting.Builder()
        .name("replace-item-frames")
        .description("Düşen item frame'leri otomatik tekrar yerleştirir.")
        .defaultValue(true)
        .visible(() -> mode.get() == Mode.Fast)
        .build()
    );

    private final Setting<Integer> maxPlacements = sgGeneral.add(new IntSetting.Builder()
        .name("max-placements")
        .description("Tick başına maksimum yerleştirme.")
        .defaultValue(64)
        .min(0)
        .sliderRange(1, 128)
        .build()
    );

    private final Setting<Integer> maxSwaps = sgGeneral.add(new IntSetting.Builder()
        .name("max-swaps")
        .description("Tick başına maksimum slot değişimi.")
        .defaultValue(64)
        .min(0)
        .sliderRange(1, 128)
        .build()
    );

    private final Setting<Integer> maxInventoryMoves = sgGeneral.add(new IntSetting.Builder()
        .name("max-inventory-moves")
        .description("Tick başına maksimum envanter->hotbar taşıma.")
        .defaultValue(64)
        .min(0)
        .sliderMax(128)
        .build()
    );

    private final Setting<Boolean> useOffhand = sgGeneral.add(new BoolSetting.Builder()
        .name("use-offhand")
        .description("Dupe eşyası offhand'de ise yerleştirmeyi offhand ile yapar.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> chronometer = sgSpeedrun.add(new BoolSetting.Builder()
        .name("chronometer")
        .description("Dupe süresini ekranda gösterir.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> dupeCounter = sgSpeedrun.add(new BoolSetting.Builder()
        .name("dupe-counter")
        .description("Dupelenen miktarı ekranda canlı gösterir.")
        .defaultValue(true)
        .build()
    );

    private final Setting<CounterDisplay> dupeCounterDisplay = sgSpeedrun.add(new EnumSetting.Builder<CounterDisplay>()
        .name("dupe-counter-display")
        .description("Dupe sayacının nerede gösterileceği.")
        .defaultValue(CounterDisplay.ActionBar)
        .visible(dupeCounter::get)
        .build()
    );

    private final Setting<Boolean> autoDisable = sgSpeedrun.add(new BoolSetting.Builder()
        .name("auto-disable")
        .description("Yeterli eşya olunca otomatik kapatır.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> itemCount = sgSpeedrun.add(new IntSetting.Builder()
        .name("item-count")
        .description("Hedef eşya sayısı.")
        .defaultValue(2304)
        .min(0)
        .sliderMax(3000)
        .visible(autoDisable::get)
        .build()
    );

    private final Setting<Boolean> renderPlace = sgRender.add(new BoolSetting.Builder()
        .name("render-empty")
        .description("Boş item frame'leri gösterir.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> emptyColor = sgRender.add(new ColorSetting.Builder()
        .name("empty-color")
        .description("Boş item frame rengi.")
        .defaultValue(new Color(0, 255, 0, 32))
        .visible(renderPlace::get)
        .build()
    );

    private final Setting<Boolean> renderDrop = sgRender.add(new BoolSetting.Builder()
        .name("render-filled")
        .description("Dolu item frame'leri gösterir.")
        .defaultValue(true)
        .build()
    );

    private final Setting<SettingColor> filledColor = sgRender.add(new ColorSetting.Builder()
        .name("filled-color")
        .description("Dolu item frame rengi.")
        .defaultValue(new Color(255, 0, 0, 32))
        .visible(renderDrop::get)
        .build()
    );

    public AutoFrameDupe() {
        super(Categories.Misc, "auto-frame-dupe", "Item frame dupe otomasyonu (1.21.1 için optimize).");
    }

    private final List<ItemFrameEntity> reachableItemFrames = new ArrayList<>();
    private final List<ItemFrameEntity> toReplace = new ArrayList<>();

    private final List<ItemFrameEntity> dontHit = new ArrayList<>();

    private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
    private long startTime = 0;
    private int dupeCount = 0;
    private long lastChatUpdateMs = 0;
    private int lastChatDupeCount = -1;

    @Override
    public void onActivate() {
        startTime = System.currentTimeMillis();
        dupeCount = 0;
        lastChatUpdateMs = 0;
        lastChatDupeCount = -1;
        if (chronometer.get())
            info("Başlangıç: §f" + sdf.format(startTime));
        reachableItemFrames.clear();
        toReplace.clear();
        dontHit.clear();
    }

    @Override
    public void onDeactivate() {
        ChatUtils.infoPrefix("KatanaDupe", "Dupelenen eşya sayısı: %d", dupeCount);

        if (chronometer.get()) {
            long stopTime = System.currentTimeMillis();
            info("Bitiş: §f" + sdf.format(stopTime));
            info("Süre: §f" + DurationFormatUtils.formatDurationWords(stopTime - startTime, true, true));
        }

        dupeCount = 0;
        lastChatUpdateMs = 0;
        lastChatDupeCount = -1;

        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;
        MeteorExecutor.execute(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            mc.execute(() -> {
                if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

                List<ItemFrameEntity> itemFrames = new ArrayList<>();
                PlayerInventory inv = mc.player.getInventory();

                Box near = mc.player.getBoundingBox().expand(5);
                for (ItemFrameEntity itemFrame : mc.world.getEntitiesByClass(ItemFrameEntity.class, near, f -> f.squaredDistanceTo(mc.player) < 25)) {
                    if (dupeItems.get().contains(itemFrame.getHeldItemStack().getItem())) {
                        mc.interactionManager.attackEntity(mc.player, itemFrame);
                    }
                    itemFrames.add(itemFrame);
                }

                if (mode.get() == Mode.Fast && replaceItemFrames.get()) {
                    for (ItemFrameEntity itemFrame : reachableItemFrames) {
                        if (itemFrame.squaredDistanceTo(mc.player) < 25 && !itemFrames.contains(itemFrame)) toReplace.add(itemFrame);
                    }

                    replaceLoop:
                    for (ItemFrameEntity itemFrame : toReplace) {

                        for (ItemFrameEntity existingItemFrame : itemFrames) {
                            if (existingItemFrame.getPos().equals(itemFrame.getPos()) && existingItemFrame.getHorizontalFacing() == itemFrame.getHorizontalFacing()) {
                                continue replaceLoop;
                            }
                        }

                        BlockPos pos = Utils.Vec3d2BlockPos(itemFrame.getPos().add(itemFrame.getRotationVector().normalize()));
                        if (!inv.getStack(40).isEmpty()) {
                            mc.interactionManager.interactBlock(mc.player, Hand.OFF_HAND, new BlockHitResult(Vec3d.ofCenter(pos), itemFrame.getHorizontalFacing(), pos, false));
                            continue;
                        }

                        boolean swapped = false;
                        if (!(!inv.getMainHandStack().isEmpty())) {
                            for (int i = 0; i < 9; i++) {
                                if (!inv.getStack(i).isEmpty()) {
                                    InvUtils.swap(i, false);
                                    swapped = true;
                                    break;
                                }
                            }
                        }
                        if (!(!inv.getMainHandStack().isEmpty()) && !swapped) {
                            for (int i = 0; i < inv.size(); i++) {
                                if (!inv.getStack(i).isEmpty()) {
                                    InvUtils.move().from(i).toHotbar(inv.selectedSlot);
                                    break;
                                }
                            }
                        }

                        if (!inv.getMainHandStack().isEmpty()) {
                            mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(pos), itemFrame.getHorizontalFacing(), pos, false));
                        }
                    }
                }
            });
        });
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || mc.interactionManager == null) return;

        String overlayMessage = null;
        if (chronometer.get()) {
            overlayMessage = "Süre: §f" + DurationFormatUtils.formatDurationWords(System.currentTimeMillis() - startTime, true, true);
        }
        if (dupeCounter.get() && dupeCounterDisplay.get() == CounterDisplay.ActionBar) {
            overlayMessage = (overlayMessage == null ? "" : overlayMessage + " | ") + "Dupe: §f" + dupeCount;
        }
        if (overlayMessage != null) {
            mc.inGameHud.setOverlayMessage(Text.literal(overlayMessage), false);
        }

        if (dupeCounter.get() && dupeCounterDisplay.get() == CounterDisplay.Chat) {
            long now = System.currentTimeMillis();
            if (dupeCount != lastChatDupeCount && now - lastChatUpdateMs >= 1000) {
                ChatUtils.infoPrefix("KatanaDupe", "Dupe sayısı: %d", dupeCount);
                lastChatDupeCount = dupeCount;
                lastChatUpdateMs = now;
            }
        }

        PlayerInventory inv = mc.player.getInventory();

        if (autoDisable.get()) {
            int count = 0;
            for (Item item : dupeItems.get()) {
                count += inv.count(item);
            }
            Box itemBox = mc.player.getBoundingBox().expand(7);
            for (ItemEntity item : mc.world.getEntitiesByClass(ItemEntity.class, itemBox, e -> dupeItems.get().contains(e.getStack().getItem()))) {
                count += item.getStack().getCount();
            }
            Box frameBox = mc.player.getBoundingBox().expand(5);
            for (ItemFrameEntity itemFrame : mc.world.getEntitiesByClass(ItemFrameEntity.class, frameBox, f -> f.squaredDistanceTo(mc.player) < 25 && dupeItems.get().contains(f.getHeldItemStack().getItem()))) {
                count++;
            }
            if (count >= itemCount.get()) {
                info("Hedef eşya sayısına ulaşıldı, mod kapatıldı.");
                toggle();
                return;
            }
        }

        if (mode.get() == Mode.Normal) {

            List<ItemFrameEntity> emptyItemFrames = new ArrayList<>();
            List<ItemFrameEntity> filledItemFrames = new ArrayList<>();

            Box near = mc.player.getBoundingBox().expand(5);
            for (ItemFrameEntity itemFrame : mc.world.getEntitiesByClass(ItemFrameEntity.class, near, f -> f.squaredDistanceTo(mc.player) < 25)) {
                if (itemFrame.getHeldItemStack().getItem() == Items.AIR) {
                    emptyItemFrames.add(itemFrame);
                } else if (dupeItems.get().contains(itemFrame.getHeldItemStack().getItem())) {
                    filledItemFrames.add(itemFrame);
                }
            }

            int placements = 0;
            int[] swaps = {0};
            int[] moves = {0};
            Hand dupeHand = getDupeHand(inv);
            boolean canPlace = true;
            if (dupeHand == Hand.MAIN_HAND && !dupeItems.get().contains(inv.getMainHandStack().getItem())) {
                canPlace = tryPrepareMainHand(inv, swaps, moves);
            }
            for (ItemFrameEntity emptyItemFrame : emptyItemFrames) {
                if (!canPlace || placements >= maxPlacements.get()) break;
                if (hasDupeItemInHand(inv, dupeHand, 1)) {
                    mc.interactionManager.interactEntity(mc.player, emptyItemFrame, dupeHand);
                    dontHit.remove(emptyItemFrame);
                    placements++;
                }
            }

            for (ItemFrameEntity itemFrame : filledItemFrames) {
                if (!dontHit.contains(itemFrame)) {
                    mc.interactionManager.attackEntity(mc.player, itemFrame);
                    dupeCount++;
                    dontHit.add(itemFrame);
                }
            }
        }

        else if (mode.get() == Mode.Fast) {

            List<ItemFrameEntity> itemFrames = new ArrayList<>();
            Box near = mc.player.getBoundingBox().expand(5);
            for (ItemFrameEntity itemFrame : mc.world.getEntitiesByClass(ItemFrameEntity.class, near, f -> f.squaredDistanceTo(mc.player) < 25)) {
                if (itemFrame.getHeldItemStack().getItem() == Items.AIR) itemFrames.add(itemFrame);
                else if (dupeItems.get().contains(itemFrame.getHeldItemStack().getItem())) itemFrames.add(itemFrame);
            }

            int placements = 0;
            int[] swaps = {0};
            int[] moves = {0};

            if (replaceItemFrames.get()) {
                for (ItemFrameEntity itemFrame : List.copyOf(reachableItemFrames)) {
                    if (itemFrame.squaredDistanceTo(mc.player) > 25)
                        reachableItemFrames.remove(itemFrame);
                    else if (!itemFrames.contains(itemFrame)) {
                        reachableItemFrames.remove(itemFrame);
                        toReplace.add(itemFrame);
                    }

                }

                replaceLoop:
                for (ItemFrameEntity itemFrame : List.copyOf(toReplace)) {

                    for (ItemFrameEntity existingItemFrame : itemFrames) {
                        if (existingItemFrame.getPos().equals(itemFrame.getPos()) && existingItemFrame.getHorizontalFacing() == itemFrame.getHorizontalFacing()) {
                            toReplace.remove(itemFrame);
                            continue replaceLoop;
                        }
                    }

                    BlockPos pos = Utils.Vec3d2BlockPos(itemFrame.getPos().add(itemFrame.getRotationVector().normalize()));
                    if (!inv.getStack(40).isEmpty()) {
                        mc.interactionManager.interactBlock(mc.player, Hand.OFF_HAND, new BlockHitResult(Vec3d.ofCenter(pos), itemFrame.getHorizontalFacing(), pos, false));
                        placements++;
                        continue;
                    }

                    boolean swapped = false;
                    if (!(!inv.getMainHandStack().isEmpty())) {
                        for (int i = 0; i < 9; i++) {
                            if (!inv.getStack(i).isEmpty()) {
                                InvUtils.swap(i, false);
                                swaps[0]++;
                                swapped = true;
                                break;
                            }
                        }
                    }
                    if (!(!inv.getMainHandStack().isEmpty()) && !swapped) {
                        for (int i = 0; i < inv.size(); i++) {
                            if (!inv.getStack(i).isEmpty()) {
                                InvUtils.move().from(i).toHotbar(inv.selectedSlot);
                                moves[0]++;
                                break;
                            }
                        }
                    }

                    if (!inv.getMainHandStack().isEmpty()) {
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(pos), itemFrame.getHorizontalFacing(), pos, false));
                    }
                }
            }
            reachableItemFrames.clear();
            reachableItemFrames.addAll(itemFrames);

            Hand dupeHand = getDupeHand(inv);
            boolean canPlace = true;
            if (dupeHand == Hand.MAIN_HAND && !dupeItems.get().contains(inv.getMainHandStack().getItem())) {
                canPlace = tryPrepareMainHand(inv, swaps, moves);
            }

            for (ItemFrameEntity itemFrame : itemFrames) {
                if (!canPlace) break;

                if (hasDupeItemInHand(inv, dupeHand, minStackSize.get()) && itemFrame.getHeldItemStack().getItem() == Items.AIR && placements < maxPlacements.get()) {
                    interactItemFrame(itemFrame, dupeHand);
                    dontHit.remove(itemFrame);
                    placements++;
                    continue;
                }

                if (!dontHit.contains(itemFrame) && dupeItems.get().contains(itemFrame.getHeldItemStack().getItem()) && placements < maxPlacements.get()) {
                    dontHit.add(itemFrame);
                    mc.interactionManager.attackEntity(mc.player, itemFrame);
                    dupeCount++;
                    if (hasDupeItemInHand(inv, dupeHand, minStackSize.get())) {
                        interactItemFrame(itemFrame, dupeHand);
                        dontHit.remove(itemFrame);
                        placements++;
                    }
                }
            }
        }
    }

    private void interactItemFrame(ItemFrameEntity itemFrame, Hand hand) {
        mc.interactionManager.interactEntity(mc.player, itemFrame, hand);
    }

    private Hand getDupeHand(PlayerInventory inv) {
        if (useOffhand.get() && dupeItems.get().contains(inv.getStack(40).getItem())) {
            return Hand.OFF_HAND;
        }
        return Hand.MAIN_HAND;
    }

    private boolean hasDupeItemInHand(PlayerInventory inv, Hand hand, int minCount) {
        if (hand == Hand.OFF_HAND) {
            return dupeItems.get().contains(inv.getStack(40).getItem())
                && inv.getStack(40).getCount() >= minCount;
        }
        return dupeItems.get().contains(inv.getMainHandStack().getItem()) && inv.getMainHandStack().getCount() >= minCount;
    }

    private boolean tryPrepareMainHand(PlayerInventory inv, int[] swaps, int[] moves) {
        if (dupeItems.get().contains(inv.getMainHandStack().getItem())) return true;

        if (swaps[0] < maxSwaps.get()) {
            for (int i = 0; i < 9; i++) {
                if (dupeItems.get().contains(inv.getStack(i).getItem())) {
                    InvUtils.swap(i, false);
                    swaps[0]++;
                    return true;
                }
            }
        }

        if (moves[0] < maxInventoryMoves.get()) {
            for (int i = 0; i < inv.size(); i++) {
                if (dupeItems.get().contains(inv.getStack(i).getItem())) {
                    InvUtils.move().from(i).toHotbar(inv.selectedSlot);
                    moves[0]++;
                    return true;
                }
            }
        }

        return dupeItems.get().contains(inv.getMainHandStack().getItem());
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Box renderBox = mc.player.getBoundingBox().expand(64);
        for (ItemFrameEntity itemFrame : mc.world.getEntitiesByClass(ItemFrameEntity.class, renderBox, f -> true)) {
            if (renderPlace.get() && itemFrame.getHeldItemStack().getItem() == Items.AIR) {
                renderItemFrame(event.renderer, itemFrame, emptyColor.get());
            } else if (renderDrop.get() && dupeItems.get().contains(itemFrame.getHeldItemStack().getItem())) {
                renderItemFrame(event.renderer, itemFrame, filledColor.get());
            }
        }
    }

    private void renderItemFrame(Renderer3D renderer, ItemFrameEntity itemFrame, Color color) {
        Vec3d pos = itemFrame.getPos();
        renderer.boxSides(pos.x - 0.25, pos.y - 0.25, pos.z - 0.25, pos.x + 0.25, pos.y + 0.25, pos.z + 0.25, color, 0);
    }

    public enum Mode {
        Normal,
        Fast
    }

    public enum CounterDisplay {
        ActionBar,
        Chat
    }
}