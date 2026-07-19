package com.lardermind.service;

public final class ChatLimits {

    /** Keep batches small so streaming tool-call JSON is less likely to truncate. */
    public static final int MAX_TOOL_LIST_SIZE = 8;
    public static final int MAX_RECIPE_STEPS = 12;
    /**
     * In-process LangChain4j window. Must be large enough for a multi-tool turn
     * (prefs + lists + several createRecipe/planMeals rounds) without evicting
     * mid tool-call / tool-result pairs.
     */
    public static final int AI_CONTEXT_MESSAGE_LIMIT = 80;
    /**
     * How many DB user/assistant text messages to preload. Leave headroom in
     * {@link #AI_CONTEXT_MESSAGE_LIMIT} for tool messages generated during the turn.
     */
    public static final int AI_CONTEXT_SEED_LIMIT = 16;
    public static final int UI_HISTORY_MESSAGE_LIMIT = 50;
    public static final int MAX_MESSAGE_LENGTH = 4000;

    private ChatLimits() {
    }
}
