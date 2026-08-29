package com.minebro.client.input;

import com.minebro.client.MineBroClient;
import com.minebro.client.screen.MineBroChatScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import org.lwjgl.glfw.GLFW;

/**
 * The primary entry point to the chat panel (design doc §5.1): {@code B}, toggling.
 *
 * <p>No lang file backs the translation keys - vanilla falls back to showing the raw key string in
 * the controls list, which is acceptable for this pass and cheaper than standing up a localization
 * pipeline for two strings.
 */
public final class MineBroKeybinds {

    private static KeyMapping openChat;

    public static void register() {
        openChat = KeyBindingHelper.registerKeyBinding(
                new KeyMapping("key.minebro.open_chat", GLFW.GLFW_KEY_B, "key.categories.minebro"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            // Drain every queued click even when the press can't act, so a press made with no world
            // loaded doesn't sit in the queue and fire the moment one is.
            while (openChat.consumeClick()) {
                toggle(client);
            }
        });
    }

    private static void toggle(Minecraft client) {
        if (client.player == null) {
            return;
        }
        if (client.screen instanceof MineBroChatScreen chat) {
            // Routed through the screen's own onClose rather than setScreen(null) so this path
            // animates identically to Esc and the header's X.
            chat.onClose();
        } else if (client.screen == null) {
            MineBroClient.avatarAnimation().onPanelOpen();
            client.setScreen(new MineBroChatScreen());
        }
    }

    private MineBroKeybinds() {}
}
