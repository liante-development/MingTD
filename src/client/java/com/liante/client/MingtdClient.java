package com.liante.client;

import com.liante.Mingtd;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.client.MinecraftClient;

public class MingtdClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(Mingtd.OpenRtsScreenPayload.ID, (payload, context) -> {
            context.client().execute(() -> {
                context.client().setScreen(new RtsScreen());
            });
        });
    }
}
