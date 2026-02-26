package rctoys.network.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import rctoys.RCToysMod;

/**
 * Analog controls (granular pitch/roll/yaw/throttle).
 * Ranges:
 *  pitch:    -1..1
 *  roll:     -1..1
 *  yaw:      -1..1
 *  throttle: -1..1
 *  brake:    boolean
 */
public record RemoteControlAnalogC2SPacket(
        float pitch,
        float roll,
        float yaw,
        float throttle,
        boolean brake
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RemoteControlAnalogC2SPacket> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(RCToysMod.MOD_ID, "remote_control_analog"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteControlAnalogC2SPacket> CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, RemoteControlAnalogC2SPacket::pitch,
                    ByteBufCodecs.FLOAT, RemoteControlAnalogC2SPacket::roll,
                    ByteBufCodecs.FLOAT, RemoteControlAnalogC2SPacket::yaw,
                    ByteBufCodecs.FLOAT, RemoteControlAnalogC2SPacket::throttle,
                    ByteBufCodecs.BOOL,  RemoteControlAnalogC2SPacket::brake,
                    RemoteControlAnalogC2SPacket::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}