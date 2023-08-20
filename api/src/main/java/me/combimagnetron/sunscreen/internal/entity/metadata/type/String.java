package me.combimagnetron.sunscreen.internal.entity.metadata.type;

import me.combimagnetron.sunscreen.internal.network.ByteBuffer;

public record String(java.lang.String string) implements MetadataType {

    public static String of(java.lang.String string) {
        return new String(string);
    }

    @Override
    public byte[] bytes() {
        final ByteBuffer buffer = ByteBuffer.empty();
        buffer.write(ByteBuffer.Adapter.STRING, string);
        return buffer.bytes();
    }
}
