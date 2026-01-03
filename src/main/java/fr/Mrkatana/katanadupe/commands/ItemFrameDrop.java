package fr.Mrkatana.katanadupe.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import meteordevelopment.meteorclient.commands.Command;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.decoration.ItemFrameEntity;
import net.minecraft.item.Items;

import java.util.List;

public class ItemFrameDrop extends Command {
    public ItemFrameDrop() {
        super("itemframedrop", "Çevrendeki item frame'lerdeki tüm eşyaları düşürür.");
    }

    @Override
    public void build(LiteralArgumentBuilder<CommandSource> builder) {
        builder.executes(context -> {
            if (mc.player == null || mc.world == null || mc.interactionManager == null) return 0;
            List<ItemFrameEntity> itemFrames = mc.world.getEntitiesByClass(ItemFrameEntity.class, mc.player.getBoundingBox().expand(4), entity -> entity.getHeldItemStack().getItem() != Items.AIR);
            for (ItemFrameEntity itemFrame : itemFrames) {
                mc.interactionManager.attackEntity(mc.player, itemFrame);
            }
            info(itemFrames.size() + " item düşürüldü.");
            return SINGLE_SUCCESS;
        });
    }
}
