package com.tpforall;

import net.minecraft.server.network.ServerPlayerEntity;

public class TpaRequest {
    public final ServerPlayerEntity requester;
    public final ServerPlayerEntity target;
    public final long timestamp;

    public TpaRequest(ServerPlayerEntity requester, ServerPlayerEntity target) {
        this.requester = requester;
        this.target = target;
        this.timestamp = System.currentTimeMillis();
    }

    public boolean isExpired() {
        return System.currentTimeMillis() - timestamp > 60_000;
    }
}
