package com.minebro.client.screen;

import com.minebro.MineBro;
import com.minebro.client.MineBroClient;
import com.minebro.config.MineBroConfig;
import com.minebro.provider.AIProvider;
import com.minebro.provider.ProviderRegistry;
import com.minebro.provider.http.SecretRedactor;
import com.minebro.tool.PermissionLevel;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * The provider settings panel (design doc §9), reusing {@link MineBroChatScreen}'s visual language
 * - flat panel, 1px border, accent top rule, vanilla font, no texture assets - so the mod reads as
 * one UI rather than two.
 *
 * <p>Two deliberate deviations from DESIGN.md §9, both narrowing scope rather than adding to it:
 *
 * <ul>
 *   <li>§9.2's radio list shows five rows including Anthropic and Google Gemini. Those adapters do
 *       not exist: {@link ProviderRegistry} has exactly two real cases, and its {@code default}
 *       falls back to Ollama. Offering a row that silently selects a different provider than the
 *       one named would be a deceptive bug, so this screen offers exactly the two that are real.
 *   <li>§9.1's five-tab structure (Provider / Generation / Tools &amp; Permissions / Interface /
 *       Memory) is collapsed to a single pane covering provider settings plus {@code
 *       permissionLevel}, the one other field with day-to-day relevance. The remaining fields stay
 *       hand-editable in the config file, whose path this screen prints.
 * </ul>
 *
 * <p>Form state lives in this instance's own fields, not in the live {@link MineBroConfig}: nothing
 * here mutates the shared config object until Save is pressed, so closing with the ✕ discards
 * edits rather than half-applying them. Nothing ever calls {@code get()}/{@code join()} - the
 * health probe resolves through {@code thenAccept} marshalled back with {@link Minecraft#execute}
 * (§6.3), exactly as the chat screen and {@code /minebro status} already do.
 */
public final class MineBroConfigScreen extends Screen {

    private static final String OLLAMA = "ollama";
    private static final String OPENAI_COMPAT = "openai-compatible";

    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 200;
    private static final int HEADER_HEIGHT = 14;
    private static final int PAD = 6;
    private static final int ROW = 16;
    private static final int BOX = 14;
    private static final int LABEL_W = 58;
    private static final int MAX_FIELD_LENGTH = 256;
    private static final long SAVED_NOTICE_MILLIS = 2000L;

    // Same palette as MineBroChatScreen - this is one UI, not two.
    private static final int PANEL_BG = 0xE6101010;
    private static final int PANEL_BORDER = 0xFF3A3A3A;
    private static final int ACCENT = 0xFFE8A93C;
    private static final int TEXT_PRIMARY = 0xFFE7E1D6;
    private static final int TEXT_SECONDARY = 0xFFA09A90;
    private static final int POSITIVE = 0xFF6FCB6A;
    private static final int NEGATIVE = 0xFFE05555;

    // ------------------------------------------------------------------ form state

    private String selectedProvider;
    private String ollamaEndpoint;
    private String ollamaModel;
    private String openAiEndpoint;
    private String openAiModel;
    private PermissionLevel permissionLevel;

    /** True once "Change" is pressed: the stored key is replaced by whatever is typed, on Save. */
    private boolean editingApiKey;
    private String pendingApiKey = "";

    private String statusText = "";
    private int statusColor = TEXT_SECONDARY;
    private String modelsText = "";
    private long savedAtMillis;

    /**
     * Guards against a slow health probe from an earlier button press overwriting the result of a
     * later one - the same supersession discipline ConversationController applies to turns.
     */
    private int testGeneration;

    private Button providerOllama;
    private Button providerOpenAi;
    private Button permissionButton;
    private Button apiKeyButton;
    private EditBox endpointBox;
    private EditBox modelBox;
    private EditBox apiKeyBox;

    private int panelX;
    private int panelY;
    private int statusTop;

    public MineBroConfigScreen() {
        super(Component.literal("MineBro Settings"));
        MineBroConfig config = MineBro.configManager().get();
        this.selectedProvider = OPENAI_COMPAT.equals(config.providerId) ? OPENAI_COMPAT : OLLAMA;
        this.ollamaEndpoint = nullToEmpty(config.ollamaEndpoint);
        this.ollamaModel = nullToEmpty(config.ollamaModel);
        this.openAiEndpoint = nullToEmpty(config.openAiCompatEndpoint);
        this.openAiModel = nullToEmpty(config.openAiCompatModel);
        this.permissionLevel = config.permissionLevel == null ? PermissionLevel.SAFE_ACTIONS : config.permissionLevel;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** No dim/blur, matching the chat panel: MineBro's UI sits over the world, it doesn't replace it. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // intentionally empty
    }

    // ---------------------------------------------------------------------- layout

    @Override
    protected void init() {
        panelX = (this.width - PANEL_WIDTH) / 2;
        panelY = (this.height - PANEL_HEIGHT) / 2;

        int contentX = panelX + PAD;
        int contentW = PANEL_WIDTH - 2 * PAD;
        int fieldX = contentX + LABEL_W;
        int fieldW = contentW - LABEL_W;
        boolean cloud = isOpenAiCompat();

        addRenderableWidget(Button.builder(Component.literal("✕"), b -> onClose())
                .bounds(panelX + PANEL_WIDTH - PAD - 10, panelY + 2, 10, 10)
                .build());

        int y = panelY + HEADER_HEIGHT + 6;

        int half = (contentW - 4) / 2;
        providerOllama = addRenderableWidget(Button.builder(providerLabel("Ollama (local)", !cloud),
                        b -> selectProvider(OLLAMA))
                .bounds(contentX, y, half, ROW).build());
        providerOpenAi = addRenderableWidget(Button.builder(providerLabel("OpenAI-compatible", cloud),
                        b -> selectProvider(OPENAI_COMPAT))
                .bounds(contentX + half + 4, y, contentW - half - 4, ROW).build());
        y += ROW + 4;

        endpointBox = addRenderableWidget(new EditBox(this.font, fieldX, y, fieldW, BOX,
                Component.literal("Endpoint")));
        endpointBox.setMaxLength(MAX_FIELD_LENGTH);
        endpointBox.setValue(cloud ? openAiEndpoint : ollamaEndpoint);
        endpointBox.setResponder(value -> {
            if (isOpenAiCompat()) {
                openAiEndpoint = value;
            } else {
                ollamaEndpoint = value;
            }
        });
        y += ROW + 2;

        modelBox = addRenderableWidget(new EditBox(this.font, fieldX, y, fieldW, BOX,
                Component.literal("Model")));
        modelBox.setMaxLength(MAX_FIELD_LENGTH);
        modelBox.setValue(cloud ? openAiModel : ollamaModel);
        modelBox.setResponder(value -> {
            if (isOpenAiCompat()) {
                openAiModel = value;
            } else {
                ollamaModel = value;
            }
        });
        y += ROW + 2;

        // API key row: only meaningful for the OpenAI-compatible provider. OllamaProvider reports
        // requiresApiKey() == false and its adapter never sends an Authorization header, so an
        // editable key field there would be a field that does nothing.
        int keyButtonW = 50;
        if (cloud) {
            if (editingApiKey) {
                apiKeyBox = addRenderableWidget(new EditBox(this.font, fieldX, y,
                        fieldW - keyButtonW - 4, BOX, Component.literal("API key")));
                apiKeyBox.setMaxLength(MAX_FIELD_LENGTH);
                apiKeyBox.setHint(Component.literal("new key (blank clears it)"));
                apiKeyBox.setValue(pendingApiKey);
                apiKeyBox.setResponder(value -> pendingApiKey = value);
            } else {
                apiKeyBox = null;
            }
            apiKeyButton = addRenderableWidget(Button.builder(
                            Component.literal(editingApiKey ? "Cancel" : "Change"), b -> toggleApiKeyEditing())
                    .bounds(fieldX + fieldW - keyButtonW, y, keyButtonW, BOX).build());
        } else {
            apiKeyBox = null;
            apiKeyButton = null;
        }
        y += ROW + 4;

        permissionButton = addRenderableWidget(Button.builder(permissionLabel(), b -> cyclePermission())
                .bounds(contentX, y, contentW, ROW).build());
        y += ROW + 4;

        int saveW = 70;
        addRenderableWidget(Button.builder(Component.literal("Test Connection"), b -> onTestConnection())
                .bounds(contentX, y, contentW - saveW - 4, ROW).build());
        addRenderableWidget(Button.builder(Component.literal("Save"), b -> onSave())
                .bounds(contentX + contentW - saveW, y, saveW, ROW).build());
        y += ROW + 4;

        statusTop = y;
    }

    private Component providerLabel(String name, boolean selected) {
        return Component.literal((selected ? "[x] " : "[ ] ") + name);
    }

    private Component permissionLabel() {
        return Component.literal("Permission: " + permissionLevel);
    }

    private boolean isOpenAiCompat() {
        return OPENAI_COMPAT.equals(selectedProvider);
    }

    // ---------------------------------------------------------------------- actions

    private void selectProvider(String providerId) {
        if (providerId.equals(selectedProvider)) {
            return;
        }
        selectedProvider = providerId;
        // Both providers' endpoint/model values are kept side by side in this form, so toggling
        // back and forth never discards what was typed for the other one.
        editingApiKey = false;
        pendingApiKey = "";
        clearTransientNotices();
        rebuildWidgets();
    }

    private void toggleApiKeyEditing() {
        editingApiKey = !editingApiKey;
        pendingApiKey = "";
        clearTransientNotices();
        rebuildWidgets();
    }

    private void cyclePermission() {
        PermissionLevel[] levels = PermissionLevel.values();
        permissionLevel = levels[(permissionLevel.ordinal() + 1) % levels.length];
        permissionButton.setMessage(permissionLabel());
        clearTransientNotices();
    }

    /**
     * Probes the values currently in the form, not the saved ones, so a player can verify an
     * endpoint before committing it. The throwaway config goes through {@link ProviderRegistry} so
     * the probe exercises exactly the construction path a real turn would - including
     * {@code resolveApiKey()}'s env-var override.
     */
    private void onTestConnection() {
        AIProvider probe = ProviderRegistry.create(formConfig());
        int generation = ++testGeneration;
        savedAtMillis = 0;
        modelsText = "";
        statusText = "Checking...";
        statusColor = TEXT_SECONDARY;

        probe.health().thenAccept(report -> Minecraft.getInstance().execute(() -> {
            if (generation != testGeneration) {
                return;
            }
            boolean good = report.reachable() && report.modelAvailable();
            statusText = (good ? "Connected - " : "Problem - ") + report.detail();
            statusColor = good ? POSITIVE : NEGATIVE;
        })).exceptionally(t -> null);

        // Free with the same button press, and the only way to find out what to type in the Model
        // field. Deliberately not fired on init() - a settings screen must not make a network call
        // just for being opened.
        probe.listModels().thenAccept(models -> Minecraft.getInstance().execute(() -> {
            if (generation != testGeneration || models.isEmpty()) {
                return;
            }
            List<String> ids = models.stream().map(m -> m.id()).limit(8).toList();
            modelsText = "Models: " + String.join(", ", ids);
        })).exceptionally(t -> null);
    }

    private void onSave() {
        MineBroConfig config = MineBro.configManager().get();
        config.providerId = selectedProvider;
        config.ollamaEndpoint = ollamaEndpoint.trim();
        config.ollamaModel = ollamaModel.trim();
        config.openAiCompatEndpoint = openAiEndpoint.trim();
        config.openAiCompatModel = openAiModel.trim();
        if (editingApiKey) {
            config.openAiCompatApiKey = pendingApiKey.trim();
        }
        config.permissionLevel = permissionLevel;
        MineBro.configManager().save();

        // Rebuild and hand the new provider to the running agent loop: without this, every field
        // on this screen would need a Minecraft restart to take effect, which is exactly the
        // limitation the screen exists to remove.
        MineBroClient.applyProvider(ProviderRegistry.create(config));

        editingApiKey = false;
        pendingApiKey = "";
        statusText = "";
        modelsText = "";
        testGeneration++;
        savedAtMillis = System.currentTimeMillis();
        rebuildWidgets();
    }

    private void clearTransientNotices() {
        savedAtMillis = 0;
        statusText = "";
        modelsText = "";
        testGeneration++;
    }

    /** A detached copy of the live config carrying the form's unsaved values. Never persisted. */
    private MineBroConfig formConfig() {
        MineBroConfig live = MineBro.configManager().get();
        MineBroConfig form = new MineBroConfig();
        form.providerId = selectedProvider;
        form.ollamaEndpoint = ollamaEndpoint.trim();
        form.ollamaModel = ollamaModel.trim();
        form.openAiCompatEndpoint = openAiEndpoint.trim();
        form.openAiCompatModel = openAiModel.trim();
        form.openAiCompatApiKey = editingApiKey ? pendingApiKey.trim() : nullToEmpty(live.openAiCompatApiKey);
        form.openAiCompatApiKeyEnvVar = live.openAiCompatApiKeyEnvVar;
        form.permissionLevel = permissionLevel;
        return form;
    }

    // -------------------------------------------------------------------- rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int right = panelX + PANEL_WIDTH;
        int bottom = panelY + PANEL_HEIGHT;

        graphics.fill(panelX, panelY, right, bottom, PANEL_BG);
        graphics.fill(panelX, panelY, right, panelY + 1, PANEL_BORDER);
        graphics.fill(panelX, bottom - 1, right, bottom, PANEL_BORDER);
        graphics.fill(panelX, panelY, panelX + 1, bottom, PANEL_BORDER);
        graphics.fill(right - 1, panelY, right, bottom, PANEL_BORDER);
        graphics.fill(panelX + 1, panelY + 1, right - 1, panelY + 3, ACCENT);

        graphics.drawString(this.font, "MineBro Settings", panelX + PAD, panelY + 5, TEXT_PRIMARY, true);
        graphics.fill(panelX + 1, panelY + HEADER_HEIGHT, right - 1, panelY + HEADER_HEIGHT + 1, PANEL_BORDER);

        renderLabels(graphics);
        renderStatus(graphics);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void renderLabels(GuiGraphics graphics) {
        int labelX = panelX + PAD;
        int textOffset = (BOX - this.font.lineHeight) / 2 + 1;

        if (endpointBox != null) {
            graphics.drawString(this.font, "Endpoint", labelX, endpointBox.getY() + textOffset, TEXT_SECONDARY, false);
        }
        if (modelBox != null) {
            graphics.drawString(this.font, "Model", labelX, modelBox.getY() + textOffset, TEXT_SECONDARY, false);
        }
        if (apiKeyButton != null) {
            int rowY = apiKeyButton.getY() + textOffset;
            graphics.drawString(this.font, "API Key", labelX, rowY, TEXT_SECONDARY, false);
            if (!editingApiKey) {
                // The saved key is only ever shown through SecretRedactor, in static text - there
                // is deliberately no unmask toggle (§9.3): "Change" replaces the key, it never
                // reveals it.
                MineBroConfig live = MineBro.configManager().get();
                String stored = nullToEmpty(live.resolveApiKey());
                boolean fromEnv = live.openAiCompatApiKeyEnvVar != null
                        && !live.openAiCompatApiKeyEnvVar.isBlank()
                        && !nullToEmpty(System.getenv(live.openAiCompatApiKeyEnvVar)).isBlank();
                String shown = stored.isBlank()
                        ? "not set - requests will likely be rejected"
                        : SecretRedactor.mask(stored) + (fromEnv ? " (from $" + live.openAiCompatApiKeyEnvVar + ")" : "");
                graphics.drawString(this.font, shown, labelX + LABEL_W, rowY,
                        stored.isBlank() ? NEGATIVE : TEXT_PRIMARY, false);
            }
        } else if (endpointBox != null) {
            // Ollama row where the API key would be: say why it's absent rather than leaving a gap.
            graphics.drawString(this.font, "Ollama runs locally - no API key needed.",
                    labelX, modelBox.getY() + ROW + 2 + textOffset, TEXT_SECONDARY, false);
        }
    }

    private void renderStatus(GuiGraphics graphics) {
        int x = panelX + PAD;
        int maxWidth = PANEL_WIDTH - 2 * PAD;
        int bottomLimit = panelY + PANEL_HEIGHT - PAD;
        List<Line> lines = new ArrayList<>();

        if (savedAtMillis > 0 && System.currentTimeMillis() - savedAtMillis < SAVED_NOTICE_MILLIS) {
            append(lines, "Saved - active now, no restart needed.", maxWidth, POSITIVE);
        }
        if (!statusText.isEmpty()) {
            append(lines, statusText, maxWidth, statusColor);
        }
        if (!modelsText.isEmpty()) {
            append(lines, modelsText, maxWidth, TEXT_SECONDARY);
        }
        if (lines.isEmpty()) {
            append(lines, MineBro.configManager().path().toString(), maxWidth, TEXT_SECONDARY);
        }

        int y = statusTop;
        for (Line line : lines) {
            if (y + this.font.lineHeight > bottomLimit) {
                break;
            }
            graphics.drawString(this.font, line.text(), x, y, line.color(), false);
            y += this.font.lineHeight;
        }
    }

    private void append(List<Line> lines, String text, int maxWidth, int color) {
        for (FormattedCharSequence seq : this.font.split(Component.literal(text), maxWidth)) {
            lines.add(new Line(seq, color));
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private record Line(FormattedCharSequence text, int color) {}
}
