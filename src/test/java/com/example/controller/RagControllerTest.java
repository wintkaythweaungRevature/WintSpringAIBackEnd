package com.example.controller;

import com.example.entity.DocumentMetadata;
import com.example.entity.User;
import com.example.service.RagService;
import com.example.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    value = RagController.class,
    excludeAutoConfiguration = {SecurityAutoConfiguration.class, SecurityFilterAutoConfiguration.class}
)
@TestPropertySource(properties = "rag.enabled=true")
class RagControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean RagService ragService;
    @MockBean UserService userService;

    private User memberUser;
    private User freeUser;

    @BeforeEach
    void setUp() {
        memberUser = new User();
        memberUser.setId(1L);
        memberUser.setEmail("member@example.com");
        memberUser.setPassword("hashed");

        freeUser = new User();
        freeUser.setId(2L);
        freeUser.setEmail("free@example.com");
        freeUser.setPassword("hashed");
    }

    // ─── POST /api/rag/upload ─────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "member@example.com")
    void upload_memberUser_returnsDocId() throws Exception {
        when(userService.findByEmail("member@example.com")).thenReturn(memberUser);
        when(userService.hasActivePaidAccess(memberUser)).thenReturn(true);
        when(ragService.ingestPdf(eq(1L), any())).thenReturn("doc-uuid-123");

        MockMultipartFile pdf = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "PDF content".getBytes());

        mockMvc.perform(multipart("/api/rag/upload").file(pdf))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.docId").value("doc-uuid-123"))
                .andExpect(jsonPath("$.filename").value("test.pdf"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(username = "free@example.com")
    void upload_freeUser_returns403() throws Exception {
        when(userService.findByEmail("free@example.com")).thenReturn(freeUser);
        when(userService.hasActivePaidAccess(freeUser)).thenReturn(false);

        MockMultipartFile pdf = new MockMultipartFile(
                "file", "test.pdf", "application/pdf", "PDF content".getBytes());

        mockMvc.perform(multipart("/api/rag/upload").file(pdf))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value("Member subscription required to use Document Q&A"));
    }

    @Test
    @WithMockUser(username = "member@example.com")
    void upload_emptyFile_returns400() throws Exception {
        when(userService.findByEmail("member@example.com")).thenReturn(memberUser);
        when(userService.hasActivePaidAccess(memberUser)).thenReturn(true);

        MockMultipartFile empty = new MockMultipartFile("file", "test.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/api/rag/upload").file(empty))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("File is empty"));
    }

    @Test
    @WithMockUser(username = "member@example.com")
    void upload_nonPdfFile_returns400() throws Exception {
        when(userService.findByEmail("member@example.com")).thenReturn(memberUser);
        when(userService.hasActivePaidAccess(memberUser)).thenReturn(true);

        MockMultipartFile txt = new MockMultipartFile(
                "file", "notes.txt", "text/plain", "some text".getBytes());

        mockMvc.perform(multipart("/api/rag/upload").file(txt))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Only PDF files are supported"));
    }

    @Test
    @WithMockUser(username = "member@example.com")
    void upload_serviceThrowsIllegalArgument_returns400() throws Exception {
        when(userService.findByEmail("member@example.com")).thenReturn(memberUser);
        when(userService.hasActivePaidAccess(memberUser)).thenReturn(true);
        when(ragService.ingestPdf(any(), any())).thenThrow(new IllegalArgumentException("Empty PDF"));

        MockMultipartFile pdf = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", "%PDF-".getBytes());

        mockMvc.perform(multipart("/api/rag/upload").file(pdf))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Empty PDF"));
    }

    // ─── POST /api/rag/query ──────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "member@example.com")
    void query_memberUser_returnsAnswer() throws Exception {
        when(userService.findByEmail("member@example.com")).thenReturn(memberUser);
        when(userService.hasActivePaidAccess(memberUser)).thenReturn(true);
        when(ragService.query(1L, "What is AI?")).thenReturn("AI stands for Artificial Intelligence.");

        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("question", "What is AI?"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("AI stands for Artificial Intelligence."))
                .andExpect(jsonPath("$.question").value("What is AI?"));
    }

    @Test
    @WithMockUser(username = "free@example.com")
    void query_freeUser_returns403() throws Exception {
        when(userService.findByEmail("free@example.com")).thenReturn(freeUser);
        when(userService.hasActivePaidAccess(freeUser)).thenReturn(false);

        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("question", "What is AI?"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "member@example.com")
    void query_blankQuestion_returns400() throws Exception {
        when(userService.findByEmail("member@example.com")).thenReturn(memberUser);
        when(userService.hasActivePaidAccess(memberUser)).thenReturn(true);

        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("question", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Question is required"));
    }

    @Test
    @WithMockUser(username = "member@example.com")
    void query_missingQuestionField_returns400() throws Exception {
        when(userService.findByEmail("member@example.com")).thenReturn(memberUser);
        when(userService.hasActivePaidAccess(memberUser)).thenReturn(true);

        mockMvc.perform(post("/api/rag/query")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Question is required"));
    }

    // ─── GET /api/rag/documents ───────────────────────────────────────────────

    @Test
    @WithMockUser(username = "member@example.com")
    void listDocuments_returnsDocumentList() throws Exception {
        when(userService.findByEmail("member@example.com")).thenReturn(memberUser);

        DocumentMetadata doc = new DocumentMetadata("doc-1", 1L, "report.pdf");
        doc.setChunkCount(5);
        when(ragService.listDocuments(1L)).thenReturn(List.of(doc));

        mockMvc.perform(get("/api/rag/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].docId").value("doc-1"))
                .andExpect(jsonPath("$[0].filename").value("report.pdf"))
                .andExpect(jsonPath("$[0].chunkCount").value(5))
                .andExpect(jsonPath("$[0].uploadedAt").exists());
    }

    @Test
    @WithMockUser(username = "member@example.com")
    void listDocuments_emptyList_returnsEmptyArray() throws Exception {
        when(userService.findByEmail("member@example.com")).thenReturn(memberUser);
        when(ragService.listDocuments(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/rag/documents"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ─── DELETE /api/rag/documents/{docId} ───────────────────────────────────

    @Test
    @WithMockUser(username = "member@example.com")
    void deleteDocument_existingDoc_returnsSuccess() throws Exception {
        when(userService.findByEmail("member@example.com")).thenReturn(memberUser);
        doNothing().when(ragService).deleteDocument(1L, "doc-1");

        mockMvc.perform(delete("/api/rag/documents/doc-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Document deleted successfully"));
    }

    @Test
    @WithMockUser(username = "member@example.com")
    void deleteDocument_notOwned_returns400() throws Exception {
        when(userService.findByEmail("member@example.com")).thenReturn(memberUser);
        doThrow(new IllegalArgumentException("Document not found"))
                .when(ragService).deleteDocument(1L, "other-user-doc");

        mockMvc.perform(delete("/api/rag/documents/other-user-doc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Document not found"));
    }
}
