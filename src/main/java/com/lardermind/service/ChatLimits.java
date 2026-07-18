package com.lardermind.service;

public final class ChatLimits {

    /** Keep batches small so streaming tool-call JSON is less likely to truncate. */
    public static final int MAX_TOOL_LIST_SIZE = 8;
    public static final int MAX_RECIPE_STEPS = 12;
    public static final int AI_CONTEXT_MESSAGE_LIMIT = 20;
    public static final int UI_HISTORY_MESSAGE_LIMIT = 50;
    public static final int MAX_MESSAGE_LENGTH = 4000;

    private ChatLimits() {
    }
}
