package fr.Mrkatana.katanadupe.utils;

import static java.lang.Math.floor;
import static meteordevelopment.meteorclient.MeteorClient.mc;

import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractEntityC2SPacket;

public class Utils {
    public static void TPX(Vec3d pos, Vec3d startPos) {

        if (mc.player.isSneaking()) {
            mc.player.networkHandler.sendPacket(PlayerInteractEntityC2SPacket.interact(mc.player, mc.player.isSneaking(), Hand.MAIN_HAND));
        }

        double distance = startPos.distanceTo(pos);

        int packetsRequired = (int) Math.ceil(Math.abs(distance / 10));
        for (int packetNumber = 0; packetNumber < (packetsRequired - 1); packetNumber++) {
            mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.OnGroundOnly(true));
        }

        mc.player.networkHandler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(pos.x, pos.y, pos.z, true));
    }

    public static void TPX(Vec3d pos) {
        TPX(pos, mc.player.getPos());
    }

    public static BlockPos Vec3d2BlockPos(Vec3d pos) {
        return new BlockPos((int) floor(pos.x), (int) floor(pos.y), (int) floor(pos.z));
    }
}
