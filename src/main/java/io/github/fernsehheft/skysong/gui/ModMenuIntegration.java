package io.github.fernsehheft.skysong.gui;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import io.github.fernsehheft.skysong.SkysongClient;

/**
 * Adds a settings button to Skysong's entry in Mod Menu. Only loaded when Mod Menu is
 * installed; without it, the settings are on the K key.
 */
public final class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> SkysongClient.settingsScreen(parent).orElse(null);
    }
}
