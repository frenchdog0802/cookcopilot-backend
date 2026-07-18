package com.lardermind.integration;

import com.lardermind.common.ApiResponse;
import com.lardermind.dto.ChatHistoryResponse;
import com.lardermind.dto.ChatSendRequest;
import com.lardermind.dto.ChatSendResponse;
import com.lardermind.entity.AIMessage;
import com.lardermind.repository.AIMessageRepository;
import com.lardermind.repository.RecipeRepository;
import com.lardermind.service.ChatLimits;
import com.lardermind.service.CookingTools;
import com.lardermind.service.UserContext;
import com.lardermind.service.ai.CookingAssistant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ChatIntegrationTest {

    @Autowired
    private com.lardermind.controller.ChatController chatController;

    @Autowired
    private AIMessageRepository aiMessageRepository;

    @Autowired
    private RecipeRepository recipeRepository;

    @Autowired
    private CookingTools cookingTools;

    @Autowired
    private UserContext userContext;

    @MockBean
    private CookingAssistant cookingAssistant;

    private UUID userId;
    private Authentication authentication;

    @BeforeEach
    void setUp() {
        userId = UUID.randomUUID();
        authentication = new UsernamePasswordAuthenticationToken(userId, null, List.of());
        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        userContext.setUserId(userId);
    }

    @AfterEach
    void tearDown() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void sendWithoutTool_persistsMessages() {
        when(cookingAssistant.chat(eq(userId), any())).thenReturn("Try adding garlic.");

        ApiResponse<ChatSendResponse> response = chatController.send(authentication, ChatSendRequest.builder()
                .message("Any tips for pasta?")
                .build());

        assertTrue(response.isSuccess());
        assertEquals("text", response.getData().getType());
        assertEquals(2, aiMessageRepository.findByUserIdOrderByCreatedAtAsc(userId).size());
    }

    @Test
    void createRecipeTool_persistsRecipe() {
        cookingTools.createRecipe(
                userId,
                "AI Pasta",
                "Quick dinner",
                List.of(new CookingTools.IngredientInput("Tomato", "2", "pcs", null)),
                List.of("Boil pasta", "Serve"));

        assertEquals(1, recipeRepository.findByUserId(userId).size());
        assertEquals("AI Pasta", recipeRepository.findByUserId(userId).get(0).getMealName());
    }

    @Test
    void historyEndpoint_returnsLastFiftyAscending() {
        for (int index = 0; index < 55; index++) {
            AIMessage message = aiMessageRepository.save(AIMessage.builder()
                    .userId(userId)
                    .role(index % 2 == 0 ? "user" : "assistant")
                    .content("message-" + index)
                    .build());
            message.setCreatedAt((long) index);
            aiMessageRepository.save(message);
        }

        ApiResponse<ChatHistoryResponse> history = chatController.history(authentication);

        assertTrue(history.isSuccess());
        assertEquals(ChatLimits.UI_HISTORY_MESSAGE_LIMIT, history.getData().getMessages().size());
        assertEquals("message-5", history.getData().getMessages().get(0).getContent());
        assertEquals("message-54", history.getData().getMessages().get(history.getData().getMessages().size() - 1).getContent());
    }

    @Test
    void historyEndpoint_isolatesUsers() {
        UUID otherUser = UUID.randomUUID();
        AIMessage message = aiMessageRepository.save(AIMessage.builder()
                .userId(otherUser)
                .role("user")
                .content("secret")
                .build());
        message.setCreatedAt(1L);
        aiMessageRepository.save(message);

        ApiResponse<ChatHistoryResponse> history = chatController.history(authentication);

        assertTrue(history.isSuccess());
        assertTrue(history.getData().getMessages().isEmpty());
    }

    @Test
    void openAiFailure_returnsErrorWithoutAssistantRow() {
        when(cookingAssistant.chat(eq(userId), any())).thenThrow(new RuntimeException("upstream failure"));

        ApiResponse<ChatSendResponse> response = chatController.send(authentication, ChatSendRequest.builder()
                .message("Help")
                .build());

        assertTrue(response.isSuccess());
        assertEquals("error", response.getData().getType());
        List<AIMessage> messages = aiMessageRepository.findByUserIdOrderByCreatedAtAsc(userId);
        assertEquals(1, messages.size());
        assertEquals("user", messages.get(0).getRole());
    }
}
