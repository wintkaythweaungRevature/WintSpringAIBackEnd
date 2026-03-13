package com.example;

import com.example.entity.User;
import com.example.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.openai.OpenAiAudioTranscriptionModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = ChatController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
class ChatControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean OpenAiChatModel chatModel;
    @MockBean ImageModel imageModel;
    @MockBean OpenAiAudioTranscriptionModel transcriptionModel;
    @MockBean EmailGeneratorService emailGeneratorService;
    @MockBean UserService userService;
    @MockBean com.example.service.JwtService jwtService;

    // ─── GET /api/ai/test ─────────────────────────────────────────────────────

    @Test
    void test_returnsAliveMessage() throws Exception {
        mockMvc.perform(get("/api/ai/test"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Backend is alive")));
    }

    // ─── GET /api/ai/ask-ai ───────────────────────────────────────────────────

    @Test
    void askAi_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/ai/ask-ai").param("prompt", "Hello"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void askAi_withAuth_returnsAiResponse() throws Exception {
        when(chatModel.call("Hello AI")).thenReturn("Hello Human");

        mockMvc.perform(get("/api/ai/ask-ai").param("prompt", "Hello AI"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello Human"));
    }

    @Test
    @WithMockUser(username = "user@example.com")
    void askAi_callsChatModelWithPrompt() throws Exception {
        when(chatModel.call("What is Java?")).thenReturn("Java is a programming language.");

        mockMvc.perform(get("/api/ai/ask-ai").param("prompt", "What is Java?"))
                .andExpect(status().isOk());

        verify(chatModel).call("What is Java?");
    }

    // ─── GET /api/ai/generate-image ───────────────────────────────────────────

    @Test
    void generateImage_withoutAuth_returns401() throws Exception {
        mockMvc.perform(get("/api/ai/generate-image").param("prompt", "A cat"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "free@example.com")
    void generateImage_withFreeUser_returns403() throws Exception {
        User freeUser = new User("free@example.com", "pass", "Free", "User");
        freeUser.setId(1L);
        freeUser.setMembershipType("FREE");

        when(userService.findByEmail("free@example.com")).thenReturn(freeUser);
        when(userService.hasActivePaidAccess(freeUser)).thenReturn(false);

        mockMvc.perform(get("/api/ai/generate-image").param("prompt", "A cat"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(
                        org.hamcrest.Matchers.containsString("subscription required")));
    }

    @Test
    @WithMockUser(username = "member@example.com")
    void generateImage_withMember_returnsImageUrl() throws Exception {
        User memberUser = new User("member@example.com", "pass", "Member", "User");
        memberUser.setId(2L);
        memberUser.setMembershipType("MEMBER");

        Image image = mock(Image.class);
        when(image.getUrl()).thenReturn("https://oaidalleapiprodscus.blob.core.windows.net/image.png");

        org.springframework.ai.image.ImageResult imageResult = mock(org.springframework.ai.image.ImageResult.class);
        when(imageResult.getOutput()).thenReturn(image);

        ImageResponse imageResponse = mock(ImageResponse.class);
        when(imageResponse.getResult()).thenReturn(imageResult);

        when(userService.findByEmail("member@example.com")).thenReturn(memberUser);
        when(userService.hasActivePaidAccess(memberUser)).thenReturn(true);
        when(imageModel.call(any())).thenReturn(imageResponse);

        mockMvc.perform(get("/api/ai/generate-image").param("prompt", "A cat"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.url").value("https://oaidalleapiprodscus.blob.core.windows.net/image.png"));
    }

    // ─── POST /api/ai/reply ───────────────────────────────────────────────────

    @Test
    void generateReply_withoutAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/ai/reply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("emailContent", "Hello", "tone", "professional"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "free@example.com")
    void generateReply_withFreeUser_returns403() throws Exception {
        User freeUser = new User("free@example.com", "pass", "Free", "User");
        freeUser.setId(1L);
        freeUser.setMembershipType("FREE");

        when(userService.findByEmail("free@example.com")).thenReturn(freeUser);
        when(userService.hasActivePaidAccess(freeUser)).thenReturn(false);

        mockMvc.perform(post("/api/ai/reply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("emailContent", "Hello", "tone", "professional"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "member@example.com")
    void generateReply_withMember_returnsGeneratedReply() throws Exception {
        User memberUser = new User("member@example.com", "pass", "Member", "User");
        memberUser.setId(2L);
        memberUser.setMembershipType("MEMBER");

        when(userService.findByEmail("member@example.com")).thenReturn(memberUser);
        when(userService.hasActivePaidAccess(memberUser)).thenReturn(true);
        when(emailGeneratorService.generateEmailReply("Hello there", "professional"))
                .thenReturn("Dear Sir, thank you for your message.");

        mockMvc.perform(post("/api/ai/reply")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                Map.of("emailContent", "Hello there", "tone", "professional"))))
                .andExpect(status().isOk());
    }

    // ─── POST /api/ai/transcribe ──────────────────────────────────────────────

    @Test
    void transcribe_withoutAuth_returns401() throws Exception {
        MockMultipartFile audioFile = new MockMultipartFile(
                "file", "audio.wav", "audio/wav", "fake-audio-content".getBytes());

        mockMvc.perform(multipart("/api/ai/transcribe").file(audioFile))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "free@example.com")
    void transcribe_withFreeUser_returns403() throws Exception {
        User freeUser = new User("free@example.com", "pass", "Free", "User");
        freeUser.setId(1L);
        freeUser.setMembershipType("FREE");

        when(userService.findByEmail("free@example.com")).thenReturn(freeUser);
        when(userService.hasActivePaidAccess(freeUser)).thenReturn(false);

        MockMultipartFile audioFile = new MockMultipartFile(
                "file", "audio.wav", "audio/wav", "fake-audio-content".getBytes());

        mockMvc.perform(multipart("/api/ai/transcribe").file(audioFile))
                .andExpect(status().isForbidden());
    }

    // ─── POST /api/ai/analyze-pdf ─────────────────────────────────────────────

    @Test
    void analyzePdf_withoutAuth_returns401() throws Exception {
        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "%PDF-1.4 fake pdf content".getBytes());

        mockMvc.perform(multipart("/api/ai/analyze-pdf").file(pdfFile))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "free@example.com")
    void analyzePdf_withFreeUser_returns403() throws Exception {
        User freeUser = new User("free@example.com", "pass", "Free", "User");
        freeUser.setId(1L);
        freeUser.setMembershipType("FREE");

        when(userService.findByEmail("free@example.com")).thenReturn(freeUser);
        when(userService.hasActivePaidAccess(freeUser)).thenReturn(false);

        MockMultipartFile pdfFile = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", "%PDF-1.4 fake pdf".getBytes());

        mockMvc.perform(multipart("/api/ai/analyze-pdf").file(pdfFile))
                .andExpect(status().isForbidden());
    }
}
