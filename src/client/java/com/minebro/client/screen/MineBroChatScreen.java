package com.minebro.client.screen;

import com.minebro.MineBro;
import com.minebro.agent.AgentEventSink;
import com.minebro.client.MineBroClient;
import com.minebro.client.hud.AvatarAnimation;
import com.minebro.client.hud.AvatarState;
import com.minebro.tool.ToolResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

/**
 * The chat panel from the design doc §5.2: a left-anchored, fixed-width panel - deliberately not
 * fullscreen and not centered, so it sits next to the HUD avatar rather than replacing the view.
 *
 * <p>Two things here are load-bearing rather than cosmetic:
 *
 * <ul>
 *   <li>{@link #isPauseScreen()} returns {@code false} (§5.1): MineBro UI must never change
 *       gameplay-visible behavior the player didn't ask for, and pausing singleplayer would.
 *   <li>Nothing in this class ever calls {@code get()}/{@code join()} on the future returned by
 *       {@code submit(...)} (§6.3). This screen <em>is</em> the {@link AgentEventSink} for turns it
 *       starts, so progress arrives as callbacks; the one thing the future is used for is the
 *       final text, via {@code whenComplete}, marshalled back with {@code Minecraft#execute}.
 * </ul>
 *
 * <p>The transcript is static on purpose: the keybind toggles this screen open and closed, and an
 * instance-scoped list would blank the conversation every time. It also means a turn started from
 * one instance keeps updating a later instance's view, so the Send/Stop label stays truthful
 * across a close/reopen.
 */
public final class MineBroChatScreen extends Screen implements AgentEventSink {

    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_X = 8;
    private static final int MAX_PANEL_HEIGHT = 220;
    private static final int HEADER_HEIGHT = 14;
    private static final int INPUT_ROW_HEIGHT = 18;
    private static final int PAD = 4;
    private static final int BUBBLE_PAD = 3;
    private static final int BUBBLE_GAP = 3;
    private static final int BADGE = 8;
    private static final int MAX_INPUT_LENGTH = 512;
    /** How far through the open tween the panel contents appear. */
    private static final float CONTENT_REVEAL = 0.6f;

    private static final int PANEL_BG = 0xE6101010;
    private static final int PANEL_BORDER = 0xFF3A3A3A;
    private static final int ACCENT = 0xFFE8A93C;
    private static final int TEXT_PRIMARY = 0xFFE7E1D6;
    private static final int TEXT_SECONDARY = 0xFFA09A90;
    private static final int USER_BUBBLE = 0xFF2B2B33;
    private static final int BRO_BUBBLE = 0xFF191919;
    private static final int BUBBLE_BORDER = 0xFF3A3A3A;
    private static final int POSITIVE = 0xFF6FCB6A;
    private static final int NEGATIVE = 0xFFE05555;

    /** Client-thread-only conversation model, shared across instances of this screen. */
    private static final List<Entry> MESSAGES = new ArrayList<>();
    private static Entry pending;
    private static boolean inFlight;
    private static AvatarState phase = AvatarState.IDLE;

    private EditBox input;
    private Button sendOrStop;
    private String carriedInput = "";

    private int panelWidth;
    private int panelHeight;
    private int panelY;
    private int listTop;
    private int listBottom;

    private int scrollOffset;
    private int maxScroll;
    private boolean userScrolledUp;

    public MineBroChatScreen() {
        super(Component.literal("MineBro"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** No dim/blur: the world stays fully visible behind a panel that occupies a corner of it. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        // intentionally empty
    }

    /**
     * Panel geometry as static functions of the viewport, so {@link #drawCollapsingGhost} can
     * reproduce exactly the same rectangle after this screen has been torn down.
     */
    static int panelWidthFor(int guiWidth) {
        return Math.min(PANEL_WIDTH, guiWidth - 2 * PANEL_X);
    }

    static int panelHeightFor(int guiHeight) {
        return Math.min((int) (guiHeight * 0.6), MAX_PANEL_HEIGHT);
    }

    @Override
    protected void init() {
        if (input != null) {
            carriedInput = input.getValue();
        }

        panelWidth = panelWidthFor(this.width);
        panelHeight = panelHeightFor(this.height);
        panelY = (this.height - panelHeight) / 2;
        listTop = panelY + HEADER_HEIGHT;
        listBottom = panelY + panelHeight - INPUT_ROW_HEIGHT;

        int inputY = panelY + panelHeight - INPUT_ROW_HEIGHT + 2;
        int sendWidth = 34;
        int inputWidth = panelWidth - 2 * PAD - sendWidth - 2;

        input = addRenderableWidget(new EditBox(this.font, PANEL_X + PAD, inputY, inputWidth, 14,
                Component.literal("Ask MineBro")));
        input.setMaxLength(MAX_INPUT_LENGTH);
        input.setHint(Component.literal("Ask MineBro..."));
        input.setResponder(text -> { });
        input.setValue(carriedInput);
        setInitialFocus(input);
        input.setFocused(true);

        sendOrStop = addRenderableWidget(Button.builder(Component.literal(inFlight ? "Stop" : "Send"), b -> onSubmit())
                .bounds(PANEL_X + panelWidth - PAD - sendWidth, inputY, sendWidth, 14)
                .build());

        addRenderableWidget(Button.builder(Component.literal("✕"), b -> onClose())
                .bounds(PANEL_X + panelWidth - PAD - 10, panelY + 2, 10, 10)
                .build());
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean enter = keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER;
        if (enter && input != null && input.isFocused()) {
            onSubmit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    /**
     * Every way out of this screen funnels through here - Esc, the header's X, and the B keybind -
     * so the collapse animation fires once for all three rather than only for the one path that
     * remembered to trigger it.
     */
    @Override
    public void onClose() {
        MineBroClient.avatarAnimation().onPanelClose();
        super.onClose();
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        scrollOffset = clamp(scrollOffset - (int) (scrollY * 12), 0, maxScroll);
        userScrolledUp = scrollOffset < maxScroll;
        return true;
    }

    /**
     * §6.2: while a turn is running this button is "Stop" and cancels instead of submitting, so a
     * player who doesn't know {@code /minebro stop} or the kill-switch keybind still has a way out
     * of a wait they've given up on.
     */
    private void onSubmit() {
        if (inFlight) {
            MineBroClient.conversationController().cancel();
            return;
        }
        String question = input.getValue().trim();
        if (question.isBlank()) {
            return;
        }
        input.setValue("");

        // §6.1: the local echo is synchronous. It is not a round trip and must never wait on one.
        MESSAGES.add(Entry.user(question));
        pending = Entry.bro();
        MESSAGES.add(pending);
        inFlight = true;
        phase = AvatarState.THINKING;
        userScrolledUp = false;
        refreshButton();

        Entry turn = pending;
        MineBroClient.conversationController().submit(question, this)
                .whenComplete((maybeAnswer, error) -> Minecraft.getInstance().execute(() -> {
                    if (error != null) {
                        turn.text = "MineBro hit a problem: " + error.getMessage();
                        turn.error = true;
                        phase = AvatarState.ERROR;
                    } else if (maybeAnswer.isPresent()) {
                        turn.text = maybeAnswer.get();
                        phase = turn.error ? AvatarState.ERROR : AvatarState.SUCCESS;
                    } else {
                        // Cancelled or superseded - the same discipline MineBroClientCommands#ask
                        // follows: display nothing, and leave no half-finished bubble behind.
                        MESSAGES.remove(turn);
                        phase = AvatarState.IDLE;
                    }
                    if (pending == turn) {
                        pending = null;
                        inFlight = false;
                    }
                    userScrolledUp = false;
                    refreshButton();
                }));
    }

    private void refreshButton() {
        if (sendOrStop != null) {
            sendOrStop.setMessage(Component.literal(inFlight ? "Stop" : "Send"));
        }
    }

    // ------------------------------------------------- AgentEventSink (background thread)

    @Override
    public void onThinking() {
        onClient(() -> phase = AvatarState.THINKING);
    }

    @Override
    public void onToolCall(String toolName) {
        onClient(() -> {
            phase = AvatarState.WORKING;
            if (pending != null) {
                pending.steps.add(new Step(toolName));
                userScrolledUp = false;
            }
        });
    }

    @Override
    public void onToolResult(ToolResult result) {
        onClient(() -> {
            phase = AvatarState.WORKING;
            if (pending == null) {
                return;
            }
            Step step = pending.lastIssued(result.tool());
            if (step == null) {
                step = new Step(result.tool());
                pending.steps.add(step);
            }
            step.status = statusOf(result);
            step.detail = result.reason();
            userScrolledUp = false;
        });
    }

    @Override
    public void onFinalAnswer(String text) {
        // Deliberately does not write the text: only a non-empty Optional from submit(...) may be
        // displayed, and that arrives in whenComplete above.
        onClient(() -> phase = AvatarState.RESPONDING);
    }

    @Override
    public void onError(String message) {
        onClient(() -> {
            phase = AvatarState.ERROR;
            if (pending != null) {
                pending.error = true;
            }
        });
    }

    private static void onClient(Runnable action) {
        Minecraft.getInstance().execute(action);
    }

    private static StepStatus statusOf(ToolResult result) {
        if (result.success()) {
            return StepStatus.OK;
        }
        return switch (result.code()) {
            case PERMISSION_DENIED, USER_DENIED, NOT_AVAILABLE_CLIENT_SIDE -> StepStatus.DENIED;
            default -> StepStatus.PROBLEM;
        };
    }

    // ---------------------------------------------------------------- rendering

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        float open = MineBro.configManager().get().reducedMotion
                ? 1.0f
                : MineBroClient.avatarAnimation().openProgress(AvatarAnimation.now());

        int right = PANEL_X + panelWidth;

        // The panel grows out of the avatar's HUD position (§13.2). Both sit on the vertical
        // midline, so a literal slide would travel almost no distance - expanding about that same
        // midline is what actually reads as "the panel *is* the avatar, expanded".
        drawPanelChrome(graphics, PANEL_X, right, panelY + panelHeight / 2, panelHeight, open);

        // Contents join once the frame is most of the way out. Drawing them earlier would render
        // full-height text inside a short box, which reads as a clipping bug rather than an
        // animation - and the whole tween is 120ms, so nothing is actually waiting on this.
        if (open >= CONTENT_REVEAL) {
            renderHeader(graphics);
            graphics.fill(PANEL_X + 1, listTop - 1, right - 1, listTop, PANEL_BORDER);
            graphics.fill(PANEL_X + 1, listBottom, right - 1, listBottom + 1, PANEL_BORDER);
            renderMessages(graphics);
        }

        // Widgets are deliberately left out of the tween rather than animated with it: EditBox and
        // Button hit-test against their own fixed bounds, so a widget drawn anywhere other than
        // where it rests would take clicks at a position it is not being drawn at. They are held
        // back instead, which keeps their hit boxes truthful. Input still reaches them during the
        // 120ms - a fast click or keystroke is captured, not swallowed - it simply isn't painted.
        if (open >= 1.0f) {
            super.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    /**
     * Draws the panel frame at a vertical fraction of its full height, centred on {@code anchorY}.
     * Shared with the closing ghost, so opening and closing trace the same shape in reverse.
     */
    private static void drawPanelChrome(GuiGraphics graphics, int left, int right, int anchorY,
                                        int fullHeight, float fraction) {
        int half = Math.max(2, Math.round(fullHeight * Math.max(0.0f, Math.min(1.0f, fraction)) / 2.0f));
        int top = anchorY - half;
        int bottom = anchorY + half;

        graphics.fill(left, top, right, bottom, PANEL_BG);
        graphics.fill(left, top, right, top + 1, PANEL_BORDER);
        graphics.fill(left, bottom - 1, right, bottom, PANEL_BORDER);
        graphics.fill(left, top, left + 1, bottom, PANEL_BORDER);
        graphics.fill(right - 1, top, right, bottom, PANEL_BORDER);
        graphics.fill(left + 1, top + 1, right - 1, Math.min(top + 3, bottom - 1), ACCENT);
    }

    /**
     * The 120ms collapse after this screen closes, drawn by {@code MineBroHud}. By the time it runs
     * the screen instance is gone, so every dimension is recomputed from the viewport rather than
     * read off a field - which is why the geometry lives in static helpers.
     *
     * @param anchorY  the avatar badge's vertical centre, the point the panel collapses into
     * @param fraction 1 -> 0 as the collapse completes
     */
    public static void drawCollapsingGhost(GuiGraphics graphics, int anchorY, float fraction) {
        int width = panelWidthFor(graphics.guiWidth());
        int height = panelHeightFor(graphics.guiHeight());
        drawPanelChrome(graphics, PANEL_X, PANEL_X + width, anchorY, height, fraction);
    }

    private void renderHeader(GuiGraphics graphics) {
        int badgeX = PANEL_X + PAD;
        int badgeY = panelY + 4;
        graphics.fill(badgeX - 1, badgeY - 1, badgeX + BADGE + 1, badgeY + BADGE + 1, 0xFF101010);
        graphics.fill(badgeX, badgeY, badgeX + BADGE, badgeY + BADGE, colorFor(phase));
        graphics.drawString(this.font, "MineBro", badgeX + BADGE + 4, panelY + 4, TEXT_PRIMARY, true);
    }

    private void renderMessages(GuiGraphics graphics) {
        int listX = PANEL_X + PAD;
        int listWidth = panelWidth - 2 * PAD;
        int viewHeight = listBottom - listTop - 2;

        List<Laid> laid = layout(listWidth);
        int total = 0;
        for (Laid l : laid) {
            total += l.height + BUBBLE_GAP;
        }
        total = Math.max(0, total - BUBBLE_GAP);

        maxScroll = Math.max(0, total - viewHeight);
        if (!userScrolledUp) {
            scrollOffset = maxScroll;
        } else {
            scrollOffset = clamp(scrollOffset, 0, maxScroll);
            if (scrollOffset >= maxScroll) {
                userScrolledUp = false;
            }
        }

        graphics.enableScissor(listX, listTop + 1, listX + listWidth, listBottom);
        int y = listTop + 1 - scrollOffset;
        for (Laid l : laid) {
            int x = l.entry.user ? listX + listWidth - l.width : listX;
            if (y + l.height >= listTop && y <= listBottom) {
                graphics.fill(x, y, x + l.width, y + l.height, BUBBLE_BORDER);
                graphics.fill(x + 1, y + 1, x + l.width - 1, y + l.height - 1,
                        l.entry.user ? USER_BUBBLE : BRO_BUBBLE);
                int lineY = y + BUBBLE_PAD;
                for (Line line : l.lines) {
                    graphics.drawString(this.font, line.text, x + BUBBLE_PAD, lineY, line.color, false);
                    lineY += this.font.lineHeight;
                }
            }
            y += l.height + BUBBLE_GAP;
        }
        graphics.disableScissor();
    }

    private List<Laid> layout(int listWidth) {
        int maxBubbleWidth = listWidth - 10;
        int maxTextWidth = maxBubbleWidth - 2 * BUBBLE_PAD;
        List<Laid> out = new ArrayList<>(MESSAGES.size());

        for (Entry entry : MESSAGES) {
            List<Line> lines = new ArrayList<>();
            for (Step step : entry.steps) {
                append(lines, step.render(), maxTextWidth, step.color());
            }
            if (entry.text != null) {
                append(lines, entry.text, maxTextWidth, entry.error ? NEGATIVE : TEXT_PRIMARY);
            } else if (entry == pending) {
                append(lines, "MineBro is thinking" + ellipsis(), maxTextWidth, TEXT_SECONDARY);
            }
            if (lines.isEmpty()) {
                continue;
            }

            int widest = 0;
            for (Line line : lines) {
                widest = Math.max(widest, this.font.width(line.text));
            }
            int width = Math.min(maxBubbleWidth, widest + 2 * BUBBLE_PAD + 1);
            int height = lines.size() * this.font.lineHeight + 2 * BUBBLE_PAD;
            out.add(new Laid(entry, lines, width, height));
        }
        return out;
    }

    private void append(List<Line> lines, String text, int maxWidth, int color) {
        for (FormattedCharSequence seq : this.font.split(Component.literal(text), maxWidth)) {
            lines.add(new Line(seq, color));
        }
    }

    /**
     * Driven by wall-clock elapsed time read once per frame - the same pattern the HUD avatar
     * already uses, and specifically not a timer, thread or animation system (§6.3).
     */
    private static String ellipsis() {
        return ".".repeat((int) ((System.currentTimeMillis() / 400) % 4));
    }

    private static int colorFor(AvatarState state) {
        return switch (state) {
            case OFFLINE -> 0xFF6B6B6B;
            case IDLE -> 0xFFE8A93C;
            case THINKING -> 0xFF4FD8D0;
            case WORKING -> 0xFFE8A93C;
            case RESPONDING -> 0xFFFFC96B;
            case SUCCESS -> 0xFF6FCB6A;
            case ERROR -> 0xFFE05555;
        };
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    // ------------------------------------------------------------------- model

    private record Line(FormattedCharSequence text, int color) {}

    private record Laid(Entry entry, List<Line> lines, int width, int height) {}

    private enum StepStatus { ISSUED, OK, PROBLEM, DENIED }

    private static final class Entry {
        final boolean user;
        final List<Step> steps = new ArrayList<>();
        String text;
        boolean error;

        private Entry(boolean user, String text) {
            this.user = user;
            this.text = text;
        }

        static Entry user(String text) {
            return new Entry(true, text);
        }

        static Entry bro() {
            return new Entry(false, null);
        }

        Step lastIssued(String tool) {
            for (int i = steps.size() - 1; i >= 0; i--) {
                Step step = steps.get(i);
                if (step.status == StepStatus.ISSUED && step.tool.equals(tool)) {
                    return step;
                }
            }
            return null;
        }
    }

    /**
     * §7.1: a step line never shows the raw tool id or any JSON if it can be helped - it shows a
     * verb plus the {@code reason} the tool already produced for exactly this purpose. An
     * unrecognized tool falls back to its id rather than to silence, so a newly added tool is
     * visibly ugly instead of invisibly missing.
     */
    private static final class Step {
        final String tool;
        StepStatus status = StepStatus.ISSUED;
        String detail;

        Step(String tool) {
            this.tool = tool;
        }

        String render() {
            String verb = verbFor(tool);
            return switch (status) {
                case ISSUED -> "⚒ " + verb + "...";
                case OK -> "✓ " + verb + (detail == null || detail.isBlank() ? "" : " - " + detail);
                case PROBLEM -> "! " + (detail == null || detail.isBlank() ? verb + " didn't work" : detail);
                case DENIED -> "✕ " + (detail == null || detail.isBlank() ? verb + " isn't allowed" : detail);
            };
        }

        int color() {
            return switch (status) {
                case ISSUED -> ACCENT;
                case OK -> POSITIVE;
                case PROBLEM, DENIED -> NEGATIVE;
            };
        }

        private static String verbFor(String toolId) {
            return switch (toolId) {
                case "get_inventory" -> "Checking inventory";
                case "get_player_status" -> "Checking your status";
                case "get_position" -> "Checking where you are";
                case "get_recipe" -> "Looking up the recipe";
                case "check_can_craft" -> "Checking the recipe";
                case "craft_item" -> "Crafting";
                default -> toolId;
            };
        }
    }
}
