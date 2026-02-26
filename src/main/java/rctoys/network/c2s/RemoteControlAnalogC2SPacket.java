package rctoys.network.c2s;

import net.minecraft.network.RegistryFriendlyByteBuf;
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
        float lx,
        float ly,
        float rx,
        float ry,
        float l2,
        float r2,
        boolean r1,
        boolean l1,
        boolean r3,
        boolean l3,
        boolean buttonA,
        boolean buttonB,
        boolean buttonX,
        boolean buttonY,
        boolean buttonStart,
        boolean buttonSelect,
        boolean padUp,
        boolean padDown,
        boolean padLeft,
        boolean padRight
) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RemoteControlAnalogC2SPacket> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(RCToysMod.MOD_ID, "remote_control_analog"));

    // StreamCodec.composite(...) only supports up to a limited number of fields.
    // This packet has more, so we encode/decode manually.
    public static final StreamCodec<RegistryFriendlyByteBuf, RemoteControlAnalogC2SPacket> CODEC =
            StreamCodec.of(RemoteControlAnalogC2SPacket::encode, RemoteControlAnalogC2SPacket::decode);

    private static void encode(RegistryFriendlyByteBuf buf, RemoteControlAnalogC2SPacket p) {
        buf.writeFloat(p.lx);
        buf.writeFloat(p.ly);
        buf.writeFloat(p.rx);
        buf.writeFloat(p.ry);
        buf.writeFloat(p.l2);
        buf.writeFloat(p.r2);

        buf.writeBoolean(p.r1);
        buf.writeBoolean(p.l1);
        buf.writeBoolean(p.r3);
        buf.writeBoolean(p.l3);

        buf.writeBoolean(p.buttonA);
        buf.writeBoolean(p.buttonB);
        buf.writeBoolean(p.buttonX);
        buf.writeBoolean(p.buttonY);

        buf.writeBoolean(p.buttonStart);
        buf.writeBoolean(p.buttonSelect);

        buf.writeBoolean(p.padUp);
        buf.writeBoolean(p.padDown);
        buf.writeBoolean(p.padLeft);
        buf.writeBoolean(p.padRight);
    }

    private static RemoteControlAnalogC2SPacket decode(RegistryFriendlyByteBuf buf) {
        float lx = buf.readFloat();
        float ly = buf.readFloat();
        float rx = buf.readFloat();
        float ry = buf.readFloat();
        float l2 = buf.readFloat();
        float r2 = buf.readFloat();

        boolean r1 = buf.readBoolean();
        boolean l1 = buf.readBoolean();
        boolean r3 = buf.readBoolean();
        boolean l3 = buf.readBoolean();

        boolean buttonA = buf.readBoolean();
        boolean buttonB = buf.readBoolean();
        boolean buttonX = buf.readBoolean();
        boolean buttonY = buf.readBoolean();

        boolean buttonStart = buf.readBoolean();
        boolean buttonSelect = buf.readBoolean();

        boolean padUp = buf.readBoolean();
        boolean padDown = buf.readBoolean();
        boolean padLeft = buf.readBoolean();
        boolean padRight = buf.readBoolean();

        return new RemoteControlAnalogC2SPacket(
                lx, ly, rx, ry, l2, r2,
                r1, l1, r3, l3,
                buttonA, buttonB, buttonX, buttonY,
                buttonStart, buttonSelect,
                padUp, padDown, padLeft, padRight
        );
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return ID;
    }
}