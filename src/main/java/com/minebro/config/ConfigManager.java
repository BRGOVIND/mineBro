package com.minebro.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final Path configPath;
    private final Logger logger;
    private MineBroConfig config;

    public ConfigManager(Logger logger) {
        this.logger = logger;
        Path configDir = FabricLoader.getInstance().getConfigDir().resolve("minebro");
        this.configPath = configDir.resolve("config.json");
        this.config = load(configDir);
    }

    public MineBroConfig get() {
        return config;
    }

    public Path path() {
        return configPath;
    }

    public void save() {
        try {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, GSON.toJson(config), StandardCharsets.UTF_8);
        } catch (IOException e) {
            logger.error("Failed to save MineBro config to {}", configPath, e);
        }
    }

    private MineBroConfig load(Path configDir) {
        if (!Files.exists(configPath)) {
            MineBroConfig fresh = new MineBroConfig();
            this.config = fresh;
            save();
            return fresh;
        }
        try {
            String json = Files.readString(configPath, StandardCharsets.UTF_8);
            MineBroConfig loaded = GSON.fromJson(json, MineBroConfig.class);
            return loaded != null ? loaded : new MineBroConfig();
        } catch (IOException | com.google.gson.JsonSyntaxException e) {
            logger.error("Failed to read MineBro config at {}, using defaults", configPath, e);
            return new MineBroConfig();
        }
    }
}
