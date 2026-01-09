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
import org.apache.commons.lang3.time.DurationFormatUtils;

import net.minecraft.util.hit.BlockHitResult;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class AutoFrameDupe extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgProtection = settings.createGroup("🛡️ Smart Protection");
    private final SettingGroup sgPerformance = settings.createGroup("⚙️ Performance");
    private final SettingGroup sgSpeedrun = settings.createGroup("Speedrun");
    private final SettingGroup sgRender = settings.createGroup("Render");

    public final Setting<List<Item>> dupeItems = sgGeneral.add(new ItemListSetting.Builder()
        .name("items")
        .description("Dupelenecek eşyalar.")
        .defaultValue(Arrays.asList(Items.SHULKER_BOX, Items.WHITE_SHULKER_BOX, Items.ORANGE_SHULKER_BOX, Items.MAGENTA_SHULKER_BOX,
            Items.LIGHT_BLUE_SHULKER_BOX, Items.YELLOW_SHULKER_BOX, Items.LIME_SHULKER_BOX, Items.PINK_SHULKER_BOX,
            Items.GRAY_SHULKER_BOX, Items.LIGHT_GRAY_SHULKER_BOX, Items.CYAN_SHULKER_BOX, Items.PURPLE_SHULKER_BOX,
            Items.BLUE_SHULKER_BOX, Items.BROWN_SHULKER_BOX, Items.GREEN_SHULKER_BOX, Items.RED_SHULKER_BOX, Items.BLACK_SHULKER_BOX))
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

    // 🛡️ Smart Protection Settings
    private final Setting<Boolean> smartProtection = sgProtection.add(new BoolSetting.Builder()
        .name("smart-protection")
        .description("Akıllı koruma: Sadece doğru item frame'lere vurur, yanlış vurmaları önler.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Boolean> verifyItemBeforeHit = sgProtection.add(new BoolSetting.Builder()
        .name("verify-item-before-hit")
        .description("Vurmadan önce item'ı doğrula (ekstra güvenlik).")
        .defaultValue(true)
        .visible(smartProtection::get)
        .build()
    );

    private final Setting<Boolean> checkFrameState = sgProtection.add(new BoolSetting.Builder()
        .name("check-frame-state")
        .description("Item frame durumunu kontrol et (boş/dolu).")
        .defaultValue(true)
        .visible(smartProtection::get)
        .build()
    );

    private final Setting<Boolean> preventDoubleHit = sgProtection.add(new BoolSetting.Builder()
        .name("prevent-double-hit")
        .description("Aynı frame'e tekrar vurmayı önle.")
        .defaultValue(true)
        .visible(smartProtection::get)
        .build()
    );

    // ⚙️ Performance Settings
    private final Setting<Double> range = sgPerformance.add(new DoubleSetting.Builder()
        .name("range")
        .description("Item frame algılama mesafesi (blok).")
        .defaultValue(5.0)
        .min(1.0)
        .max(10.0)
        .sliderMin(1.0)
        .sliderMax(10.0)
        .build()
    );

    private final Setting<Integer> interactionDelay = sgPerformance.add(new IntSetting.Builder()
        .name("interaction-delay")
        .description("Etkileşim gecikmesi (tick). Düşük değer = daha hızlı.")
        .defaultValue(0)
        .min(0)
        .max(20)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Integer> hitDelay = sgPerformance.add(new IntSetting.Builder()
        .name("hit-delay")
        .description("Vurma gecikmesi (tick). Düşük değer = daha hızlı.")
        .defaultValue(0)
        .min(0)
        .max(20)
        .sliderRange(0, 20)
        .build()
    );

    private final Setting<Boolean> speedMode = sgPerformance.add(new BoolSetting.Builder()
        .name("speed-mode")
        .description("🚀 Blazing Fast: Tick başına maksimum item için optimize edilmiş mod.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Integer> speedModeMultiplier = sgPerformance.add(new IntSetting.Builder()
        .name("speed-multiplier")
        .description("Speed mode çarpanı (1-5). Daha yüksek = daha hızlı ama daha fazla lag.")
        .defaultValue(2)
        .min(1)
        .max(5)
        .sliderRange(1, 5)
        .visible(speedMode::get)
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
        super(Categories.Misc, "auto-frame-dupe", "Item frame dupe otomasyonu & Item frame Dupe automation.");
    }

    private final List<ItemFrameEntity> reachableItemFrames = new ArrayList<>();
    private final List<ItemFrameEntity> toReplace = new ArrayList<>();
    private final List<ItemFrameEntity> dontHit = new ArrayList<>();

    private final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
    private long startTime = 0;
    private int dupeCount = 0;
    private long lastChatUpdateMs = 0;
    private int lastChatDupeCount = -1;
    
    // Dupe sayacı için item frame takibi
    private final Map<UUID, Vec3d> hitItemFrames = new HashMap<>();
    private final Map<UUID, Long> hitItemFramesTime = new HashMap<>();
    private static final long ITEM_CHECK_TIMEOUT = 2000; // 2 saniye
    
    // Delay tracking
    private int interactionTickCounter = 0;
    private int hitTickCounter = 0;
    private final Map<UUID, Integer> lastHitTick = new HashMap<>();

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
        hitItemFrames.clear();
        hitItemFramesTime.clear();
        lastHitTick.clear();
        interactionTickCounter = 0;
        hitTickCounter = 0;
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
        hitItemFrames.clear();
        hitItemFramesTime.clear();
        lastHitTick.clear();
        reachableItemFrames.clear();
        toReplace.clear();
        dontHit.clear();
        interactionTickCounter = 0;
        hitTickCounter = 0;

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

                double rangeValue = range.get();
                double maxDistanceSq = rangeValue * rangeValue;
                Box near = mc.player.getBoundingBox().expand(rangeValue);
                for (ItemFrameEntity itemFrame : mc.world.getEntitiesByClass(ItemFrameEntity.class, near, f -> f.squaredDistanceTo(mc.player) < maxDistanceSq)) {
                    ItemStack heldStack = itemFrame.getHeldItemStack();
                    if (!heldStack.isEmpty() && dupeItems.get().contains(heldStack.getItem())) {
                        mc.interactionManager.attackEntity(mc.player, itemFrame);
                    }
                    itemFrames.add(itemFrame);
                }

                if (mode.get() == Mode.Fast && replaceItemFrames.get()) {
                    double maxDistanceSqDeactivate = rangeValue * rangeValue;
                    for (ItemFrameEntity itemFrame : reachableItemFrames) {
                        if (itemFrame.squaredDistanceTo(mc.player) < maxDistanceSqDeactivate && !itemFrames.contains(itemFrame)) toReplace.add(itemFrame);
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

                        // Optimize edilmiş item frame hazırlığı
                        ItemStack mainHandStack = inv.getMainHandStack();
                        if (mainHandStack.isEmpty() || mainHandStack.getItem() != Items.ITEM_FRAME) {
                            boolean swapped = false;
                            // Hotbar'da item frame ara
                            for (int j = 0; j < 9; j++) {
                                ItemStack stack = inv.getStack(j);
                                if (!stack.isEmpty() && stack.getItem() == Items.ITEM_FRAME) {
                                    InvUtils.swap(j, false);
                                    swapped = true;
                                    break;
                                }
                            }
                            // Envanterde item frame ara
                            if (!swapped) {
                                for (int j = 9; j < inv.size(); j++) {
                                    ItemStack stack = inv.getStack(j);
                                    if (!stack.isEmpty() && stack.getItem() == Items.ITEM_FRAME) {
                                        InvUtils.move().from(j).toHotbar(inv.selectedSlot);
                                        break;
                                    }
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

        // Delay counter'ları güncelle
        interactionTickCounter++;
        hitTickCounter++;

        // Eski item frame takiplerini temizle
        long currentTime = System.currentTimeMillis();
        hitItemFramesTime.entrySet().removeIf(entry -> currentTime - entry.getValue() > ITEM_CHECK_TIMEOUT);
        hitItemFrames.entrySet().removeIf(entry -> !hitItemFramesTime.containsKey(entry.getKey()));

        // Düşen item'ları kontrol et ve dupe sayacını güncelle
        checkDroppedItems();

        String overlayMessage = null;
        if (chronometer.get()) {
            overlayMessage = "Süre: §f" + DurationFormatUtils.formatDurationWords(currentTime - startTime, true, true);
        }
        if (dupeCounter.get() && dupeCounterDisplay.get() == CounterDisplay.ActionBar) {
            overlayMessage = (overlayMessage == null ? "" : overlayMessage + " | ") + "Dupe: §f" + dupeCount;
        }
        if (overlayMessage != null) {
            mc.inGameHud.setOverlayMessage(Text.literal(overlayMessage), false);
        }

        if (dupeCounter.get() && dupeCounterDisplay.get() == CounterDisplay.Chat) {
            if (dupeCount != lastChatDupeCount && currentTime - lastChatUpdateMs >= 1000) {
                ChatUtils.infoPrefix("KatanaDupe", "Dupe sayısı: %d", dupeCount);
                lastChatDupeCount = dupeCount;
                lastChatUpdateMs = currentTime;
            }
        }

        PlayerInventory inv = mc.player.getInventory();

        if (autoDisable.get()) {
            int count = 0;
            for (Item item : dupeItems.get()) {
                count += inv.count(item);
            }
            double rangeValue = range.get();
            Box itemBox = mc.player.getBoundingBox().expand(rangeValue + 2);
            for (ItemEntity item : mc.world.getEntitiesByClass(ItemEntity.class, itemBox, e -> dupeItems.get().contains(e.getStack().getItem()))) {
                count += item.getStack().getCount();
            }
            Box frameBox = mc.player.getBoundingBox().expand(rangeValue);
            double maxDistanceSqAutoDisable = rangeValue * rangeValue;
            int frameCount = (int) mc.world.getEntitiesByClass(ItemFrameEntity.class, frameBox, f -> {
                ItemStack stack = f.getHeldItemStack();
                return f.squaredDistanceTo(mc.player) < maxDistanceSqAutoDisable && !stack.isEmpty() && dupeItems.get().contains(stack.getItem());
            }).stream().count();
            count += frameCount;
            if (count >= itemCount.get()) {
                info("Hedef eşya sayısına ulaşıldı, mod kapatıldı.");
                toggle();
                return;
            }
        }

        // Range ayarını kullan
        double rangeValue = range.get();
        double maxDistanceSq = rangeValue * rangeValue;
        Box near = mc.player.getBoundingBox().expand(rangeValue);

        if (mode.get() == Mode.Normal) {
            List<ItemFrameEntity> emptyItemFrames = new ArrayList<>();
            List<ItemFrameEntity> filledItemFrames = new ArrayList<>();
            
            for (ItemFrameEntity itemFrame : mc.world.getEntitiesByClass(ItemFrameEntity.class, near, f -> f.squaredDistanceTo(mc.player) < maxDistanceSq)) {
                // Smart Protection kontrolü
                if (!isValidItemFrame(itemFrame)) continue;
                
                ItemStack heldStack = itemFrame.getHeldItemStack();
                if (heldStack.isEmpty() || heldStack.getItem() == Items.AIR) {
                    emptyItemFrames.add(itemFrame);
                } else if (dupeItems.get().contains(heldStack.getItem())) {
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
            // Item frame koyma hızlandırma - multitask için optimize edildi
            int effectiveMaxPlacements = speedMode.get() ? maxPlacements.get() * speedModeMultiplier.get() : maxPlacements.get();
            for (ItemFrameEntity emptyItemFrame : emptyItemFrames) {
                if (!canPlace || placements >= effectiveMaxPlacements) break;
                
                // Interaction delay kontrolü
                if (interactionDelay.get() > 0 && interactionTickCounter < interactionDelay.get()) continue;
                
                if (hasDupeItemInHand(inv, dupeHand, 1)) {
                    interactItemFrame(emptyItemFrame, dupeHand);
                    dontHit.remove(emptyItemFrame);
                    placements++;
                    interactionTickCounter = 0; // Reset delay counter
                    
                    // Hızlı yerleştirme için offhand kontrolü
                    if (placements < effectiveMaxPlacements && useOffhand.get()) {
                        ItemStack offhandStack = inv.getStack(40);
                        if (!offhandStack.isEmpty() && dupeItems.get().contains(offhandStack.getItem())) {
                            dupeHand = Hand.OFF_HAND;
                        }
                    }
                }
            }

            for (ItemFrameEntity itemFrame : filledItemFrames) {
                // Smart Protection: Double hit önleme
                if (smartProtection.get() && preventDoubleHit.get() && dontHit.contains(itemFrame)) continue;
                
                // Hit delay kontrolü
                UUID frameId = itemFrame.getUuid();
                if (hitDelay.get() > 0 && lastHitTick.containsKey(frameId)) {
                    int ticksSinceLastHit = hitTickCounter - lastHitTick.get(frameId);
                    if (ticksSinceLastHit < hitDelay.get()) continue;
                }
                
                // Smart Protection: Item doğrulama
                if (smartProtection.get() && verifyItemBeforeHit.get()) {
                    ItemStack heldStack = itemFrame.getHeldItemStack();
                    if (heldStack.isEmpty() || !dupeItems.get().contains(heldStack.getItem())) continue;
                }
                
                if (!dontHit.contains(itemFrame)) {
                    mc.interactionManager.attackEntity(mc.player, itemFrame);
                    // Dupe sayacı artık checkDroppedItems() metodunda yapılıyor
                    trackItemFrameHit(itemFrame);
                    dontHit.add(itemFrame);
                    lastHitTick.put(frameId, hitTickCounter);
                    hitTickCounter = 0; // Reset hit delay counter
                }
            }
        }

        else if (mode.get() == Mode.Fast) {
            List<ItemFrameEntity> itemFrames = new ArrayList<>();
            
            for (ItemFrameEntity itemFrame : mc.world.getEntitiesByClass(ItemFrameEntity.class, near, f -> f.squaredDistanceTo(mc.player) < maxDistanceSq)) {
                // Smart Protection kontrolü
                if (!isValidItemFrame(itemFrame)) continue;
                
                ItemStack heldStack = itemFrame.getHeldItemStack();
                if (heldStack.isEmpty() || heldStack.getItem() == Items.AIR) {
                    itemFrames.add(itemFrame);
                } else if (dupeItems.get().contains(heldStack.getItem())) {
                    itemFrames.add(itemFrame);
                }
            }

            int placements = 0;
            int[] swaps = {0};
            int[] moves = {0};

            if (replaceItemFrames.get()) {
                // Gereksiz List.copyOf() kaldırıldı - iterator kullanarak optimize edildi
                // maxDistanceSq zaten yukarıda tanımlı
                reachableItemFrames.removeIf(itemFrame -> {
                    if (itemFrame.squaredDistanceTo(mc.player) > maxDistanceSq) {
                        return true;
                    } else if (!itemFrames.contains(itemFrame)) {
                        toReplace.add(itemFrame);
                        return true;
                    }
                    return false;
                });

                replaceLoop:
                for (int i = toReplace.size() - 1; i >= 0; i--) {
                    ItemFrameEntity itemFrame = toReplace.get(i);

                    for (ItemFrameEntity existingItemFrame : itemFrames) {
                        if (existingItemFrame.getPos().equals(itemFrame.getPos()) && existingItemFrame.getHorizontalFacing() == itemFrame.getHorizontalFacing()) {
                            toReplace.remove(i);
                            continue replaceLoop;
                        }
                    }

                    BlockPos pos = Utils.Vec3d2BlockPos(itemFrame.getPos().add(itemFrame.getRotationVector().normalize()));
                    
                    // Offhand önceliği - daha hızlı yerleştirme için
                    ItemStack offhandStack = inv.getStack(40);
                    if (!offhandStack.isEmpty() && offhandStack.getItem() == Items.ITEM_FRAME) {
                        mc.interactionManager.interactBlock(mc.player, Hand.OFF_HAND, new BlockHitResult(Vec3d.ofCenter(pos), itemFrame.getHorizontalFacing(), pos, false));
                        placements++;
                        toReplace.remove(i);
                        continue;
                    }

                    // Main hand için optimize edilmiş hazırlık
                    ItemStack mainHandStack = inv.getMainHandStack();
                    if (mainHandStack.isEmpty() || mainHandStack.getItem() != Items.ITEM_FRAME) {
                        boolean swapped = false;
                        if (swaps[0] < maxSwaps.get()) {
                            for (int j = 0; j < 9; j++) {
                                ItemStack stack = inv.getStack(j);
                                if (!stack.isEmpty() && stack.getItem() == Items.ITEM_FRAME) {
                                    InvUtils.swap(j, false);
                                    swaps[0]++;
                                    swapped = true;
                                    break;
                                }
                            }
                        }
                        if (!swapped && moves[0] < maxInventoryMoves.get()) {
                            for (int j = 9; j < inv.size(); j++) { // Hotbar'ı atla, direkt envanterden başla
                                ItemStack stack = inv.getStack(j);
                                if (!stack.isEmpty() && stack.getItem() == Items.ITEM_FRAME) {
                                    InvUtils.move().from(j).toHotbar(inv.selectedSlot);
                                    moves[0]++;
                                    break;
                                }
                            }
                        }
                    }

                    mainHandStack = inv.getMainHandStack();
                    if (!mainHandStack.isEmpty() && mainHandStack.getItem() == Items.ITEM_FRAME) {
                        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, new BlockHitResult(Vec3d.ofCenter(pos), itemFrame.getHorizontalFacing(), pos, false));
                        placements++;
                    }
                    toReplace.remove(i);
                }
            }
            reachableItemFrames.clear();
            reachableItemFrames.addAll(itemFrames);

            Hand dupeHand = getDupeHand(inv);
            boolean canPlace = true;
            if (dupeHand == Hand.MAIN_HAND && !dupeItems.get().contains(inv.getMainHandStack().getItem())) {
                canPlace = tryPrepareMainHand(inv, swaps, moves);
            }

            // Fast mode için optimize edilmiş item frame işleme - multitask + Blazing Fast
            int effectiveMaxPlacements = speedMode.get() ? maxPlacements.get() * speedModeMultiplier.get() : maxPlacements.get();
            for (ItemFrameEntity itemFrame : itemFrames) {
                if (!canPlace) break;

                // Boş item frame'e eşya koy
                ItemStack heldStack = itemFrame.getHeldItemStack();
                if (hasDupeItemInHand(inv, dupeHand, minStackSize.get()) && (heldStack.isEmpty() || heldStack.getItem() == Items.AIR) && placements < effectiveMaxPlacements) {
                    // Interaction delay kontrolü
                    if (interactionDelay.get() > 0 && interactionTickCounter < interactionDelay.get()) continue;
                    
                    interactItemFrame(itemFrame, dupeHand);
                    dontHit.remove(itemFrame);
                    placements++;
                    interactionTickCounter = 0; // Reset delay counter
                    
                    // Offhand'e geçiş için kontrol
                    if (placements < effectiveMaxPlacements && useOffhand.get()) {
                        ItemStack offhandStack = inv.getStack(40);
                        if (!offhandStack.isEmpty() && dupeItems.get().contains(offhandStack.getItem()) && offhandStack.getCount() >= minStackSize.get()) {
                            dupeHand = Hand.OFF_HAND;
                        }
                    }
                    continue;
                }

                // Dolu item frame'i vur ve hemen eşya koy (multitask)
                ItemStack frameStack = itemFrame.getHeldItemStack();
                
                // Smart Protection: Double hit önleme
                if (smartProtection.get() && preventDoubleHit.get() && dontHit.contains(itemFrame)) continue;
                
                // Hit delay kontrolü
                UUID frameId = itemFrame.getUuid();
                if (hitDelay.get() > 0 && lastHitTick.containsKey(frameId)) {
                    int ticksSinceLastHit = hitTickCounter - lastHitTick.get(frameId);
                    if (ticksSinceLastHit < hitDelay.get()) continue;
                }
                
                // Smart Protection: Item doğrulama
                if (smartProtection.get() && verifyItemBeforeHit.get()) {
                    if (frameStack.isEmpty() || !dupeItems.get().contains(frameStack.getItem())) continue;
                }
                
                if (!dontHit.contains(itemFrame) && !frameStack.isEmpty() && dupeItems.get().contains(frameStack.getItem()) && placements < effectiveMaxPlacements) {
                    dontHit.add(itemFrame);
                    mc.interactionManager.attackEntity(mc.player, itemFrame);
                    // Dupe sayacı artık checkDroppedItems() metodunda yapılıyor
                    trackItemFrameHit(itemFrame);
                    lastHitTick.put(frameId, hitTickCounter);
                    hitTickCounter = 0; // Reset hit delay counter
                    
                    // Hemen eşya koy - multitask için
                    if (hasDupeItemInHand(inv, dupeHand, minStackSize.get())) {
                        // Interaction delay kontrolü
                        if (interactionDelay.get() > 0 && interactionTickCounter < interactionDelay.get()) continue;
                        
                        interactItemFrame(itemFrame, dupeHand);
                        dontHit.remove(itemFrame);
                        placements++;
                        interactionTickCounter = 0; // Reset delay counter
                        
                        // Offhand'e geçiş için kontrol
                        if (placements < effectiveMaxPlacements && useOffhand.get()) {
                            ItemStack offhandStack = inv.getStack(40);
                            if (!offhandStack.isEmpty() && dupeItems.get().contains(offhandStack.getItem()) && offhandStack.getCount() >= minStackSize.get()) {
                                dupeHand = Hand.OFF_HAND;
                            }
                        }
                    }
                }
            }
        }
    }

    private void interactItemFrame(ItemFrameEntity itemFrame, Hand hand) {
        mc.interactionManager.interactEntity(mc.player, itemFrame, hand);
    }

    private Hand getDupeHand(PlayerInventory inv) {
        // Offhand optimizasyonu - önce offhand kontrolü
        if (useOffhand.get()) {
            ItemStack offhandStack = inv.getStack(40);
            if (!offhandStack.isEmpty() && dupeItems.get().contains(offhandStack.getItem()) && offhandStack.getCount() >= (mode.get() == Mode.Fast ? minStackSize.get() : 1)) {
                return Hand.OFF_HAND;
            }
        }
        return Hand.MAIN_HAND;
    }

    private boolean hasDupeItemInHand(PlayerInventory inv, Hand hand, int minCount) {
        if (inv == null) return false;
        
        if (hand == Hand.OFF_HAND) {
            ItemStack offhandStack = inv.getStack(40);
            return !offhandStack.isEmpty() 
                && dupeItems.get().contains(offhandStack.getItem())
                && offhandStack.getCount() >= minCount;
        }
        
        ItemStack mainHandStack = inv.getMainHandStack();
        return !mainHandStack.isEmpty() 
            && dupeItems.get().contains(mainHandStack.getItem()) 
            && mainHandStack.getCount() >= minCount;
    }

    private boolean tryPrepareMainHand(PlayerInventory inv, int[] swaps, int[] moves) {
        ItemStack mainHandStack = inv.getMainHandStack();
        if (!mainHandStack.isEmpty() && dupeItems.get().contains(mainHandStack.getItem())) {
            return true;
        }

        // Hotbar'da önce ara (daha hızlı)
        if (swaps[0] < maxSwaps.get()) {
            for (int i = 0; i < 9; i++) {
                ItemStack stack = inv.getStack(i);
                if (!stack.isEmpty() && dupeItems.get().contains(stack.getItem())) {
                    InvUtils.swap(i, false);
                    swaps[0]++;
                    return true;
                }
            }
        }

        // Envanterde ara
        if (moves[0] < maxInventoryMoves.get()) {
            for (int i = 9; i < inv.size(); i++) { // Hotbar'ı atla, direkt envanterden başla
                ItemStack stack = inv.getStack(i);
                if (!stack.isEmpty() && dupeItems.get().contains(stack.getItem())) {
                    InvUtils.move().from(i).toHotbar(inv.selectedSlot);
                    moves[0]++;
                    return true;
                }
            }
        }

        // Son kontrol
        mainHandStack = inv.getMainHandStack();
        return !mainHandStack.isEmpty() && dupeItems.get().contains(mainHandStack.getItem());
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;

        Box renderBox = mc.player.getBoundingBox().expand(64);
        for (ItemFrameEntity itemFrame : mc.world.getEntitiesByClass(ItemFrameEntity.class, renderBox, f -> true)) {
            ItemStack heldStack = itemFrame.getHeldItemStack();
            if (renderPlace.get() && (heldStack.isEmpty() || heldStack.getItem() == Items.AIR)) {
                renderItemFrame(event.renderer, itemFrame, emptyColor.get());
            } else if (renderDrop.get() && !heldStack.isEmpty() && dupeItems.get().contains(heldStack.getItem())) {
                renderItemFrame(event.renderer, itemFrame, filledColor.get());
            }
        }
    }

    private void renderItemFrame(Renderer3D renderer, ItemFrameEntity itemFrame, Color color) {
        Vec3d pos = itemFrame.getPos();
        renderer.boxSides(pos.x - 0.25, pos.y - 0.25, pos.z - 0.25, pos.x + 0.25, pos.y + 0.25, pos.z + 0.25, color, 0);
    }

    // 🛡️ Smart Protection: Item frame geçerliliğini kontrol et
    private boolean isValidItemFrame(ItemFrameEntity itemFrame) {
        if (itemFrame == null || !smartProtection.get()) return true;
        
        // Frame state kontrolü
        if (checkFrameState.get()) {
            ItemStack heldStack = itemFrame.getHeldItemStack();
            // Boş frame veya dupe item'ı içeren frame geçerli
            if (!heldStack.isEmpty() && heldStack.getItem() != Items.AIR && !dupeItems.get().contains(heldStack.getItem())) {
                return false;
            }
        }
        
        return true;
    }

    // Dupe sayacı için item frame hit takibi
    private void trackItemFrameHit(ItemFrameEntity itemFrame) {
        UUID frameId = itemFrame.getUuid();
        hitItemFrames.put(frameId, itemFrame.getPos());
        hitItemFramesTime.put(frameId, System.currentTimeMillis());
    }

    // Düşen item'ları kontrol et ve dupe sayacını güncelle - optimize edilmiş (O(n) yaklaşımı)
    private void checkDroppedItems() {
        if (mc.world == null || mc.player == null || hitItemFrames.isEmpty()) return;

        Box checkBox = mc.player.getBoundingBox().expand(6);
        List<ItemEntity> nearbyItems = mc.world.getEntitiesByClass(ItemEntity.class, checkBox, 
            item -> {
                ItemStack stack = item.getStack();
                return !stack.isEmpty() && dupeItems.get().contains(stack.getItem());
            });

        if (nearbyItems.isEmpty()) return;

        // Her item frame için düşen item sayısını kontrol et - optimize edilmiş
        Map<UUID, Integer> frameItemCounts = new HashMap<>();
        double maxDistanceSq = 4.0; // 2 blok mesafe karesi (squared distance için)
        
        // Önce aktif frame'lerin listesini oluştur (timeout kontrolü ile)
        List<Map.Entry<UUID, Vec3d>> activeFrames = new ArrayList<>();
        for (Map.Entry<UUID, Vec3d> entry : hitItemFrames.entrySet()) {
            if (hitItemFramesTime.containsKey(entry.getKey())) {
                activeFrames.add(entry);
            }
        }
        
        if (activeFrames.isEmpty()) return;
        
        // Her item için en yakın frame'i bul - optimize edilmiş
        for (ItemEntity item : nearbyItems) {
            Vec3d itemPos = item.getPos();
            UUID closestFrameId = null;
            double minDistanceSq = maxDistanceSq;

            // En yakın item frame'i bul
            for (Map.Entry<UUID, Vec3d> entry : activeFrames) {
                double distanceSq = itemPos.squaredDistanceTo(entry.getValue());
                if (distanceSq < minDistanceSq) {
                    minDistanceSq = distanceSq;
                    closestFrameId = entry.getKey();
                }
            }

            if (closestFrameId != null) {
                frameItemCounts.put(closestFrameId, frameItemCounts.getOrDefault(closestFrameId, 0) + item.getStack().getCount());
            }
        }

        // 1'den fazla item düşen frame'ler için dupe sayacını artır
        for (Map.Entry<UUID, Integer> entry : frameItemCounts.entrySet()) {
            if (entry.getValue() > 1) {
                dupeCount++;
                // Bu frame'i takipten çıkar (tekrar sayılmasın)
                hitItemFrames.remove(entry.getKey());
                hitItemFramesTime.remove(entry.getKey());
            }
        }
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
