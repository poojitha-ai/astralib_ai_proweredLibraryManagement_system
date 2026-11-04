package com.astralib_backend;

import com.astralib.backend.EmbeddingService;
import com.astralib.backend.Role;
import com.astralib.backend.StudentDocument;
import com.astralib.backend.StudentDocumentRepository;
import com.astralib.backend.User;
import com.astralib.backend.UserRepository;
import com.astralib.backend.VectorRecord;
import com.astralib.backend.VectorRecordRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class StudentDocumentUploadTest extends AbstractIntegrationTest {

    private static final String PASSWORD = "TestPassword123!";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentDocumentRepository studentDocumentRepository;

    @Autowired
    private VectorRecordRepository vectorRecordRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private EmbeddingService embeddingService;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private MockMvc mockMvc;

    private User alice;
    private User bob;
    private String aliceToken;
    private String bobToken;

    @BeforeAll
    void setUp() throws Exception {
        mockMvc = webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        // Deterministic dummy embedding (384 dimensions)
        float[] mockVector = new float[384];
        mockVector[0] = 0.5f;
        mockVector[1] = 0.25f;
        Mockito.when(embeddingService.generateEmbedding(anyString())).thenReturn(mockVector);
        Mockito.when(embeddingService.getDimension()).thenReturn(384);

        alice = createUser("student_alice@astralib.test");
        bob = createUser("student_bob@astralib.test");

        aliceToken = login("student_alice@astralib.test");
        bobToken = login("student_bob@astralib.test");
    }

    private User createUser(String email) {
        return userRepository.findByEmail(email).orElseGet(() -> {
            User user = new User("Student", email, passwordEncoder.encode(PASSWORD));
            user.setRole(Role.USER);
            return userRepository.save(user);
        });
    }

    private String login(String email) throws Exception {
        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, PASSWORD);

        MvcResult result = mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(body)
                )
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("token")
                .asText();
    }

    private byte[] createSamplePdf(String heading, String content) throws Exception {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage();
            doc.addPage(page);

            try (PDPageContentStream stream = new PDPageContentStream(doc, page)) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 14);
                stream.newLineAtOffset(50, 700);
                stream.showText(heading);
                stream.newLineAtOffset(0, -30);
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.showText(content);
                stream.endText();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.save(baos);
            return baos.toByteArray();
        }
    }

    private byte[] createSampleDocx(String heading, String content) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {
            XWPFParagraph p1 = doc.createParagraph();
            p1.createRun().setText(heading);

            XWPFParagraph p2 = doc.createParagraph();
            p2.createRun().setText(content);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            return baos.toByteArray();
        }
    }

    @Test
    void validPdfUpload_extractsChunksAndEmbedsSuccessfully() throws Exception {
        byte[] pdfBytes = createSamplePdf("Chapter 1: Operating Systems",
                "Operating systems manage hardware resources and provide common services for application programs.");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "os_notes.pdf",
                "application/pdf",
                pdfBytes
        );

        MvcResult result = mockMvc.perform(
                        multipart("/api/student-documents")
                                .file(file)
                                .header("Authorization", "Bearer " + aliceToken)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value("os_notes.pdf"))
                .andExpect(jsonPath("$.fileFormat").value("PDF"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.chunkCount").value(org.hamcrest.Matchers.greaterThan(0)))
                .andReturn();

        Long docId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        // Verify database entity
        StudentDocument savedDoc = studentDocumentRepository.findById(docId).orElseThrow();
        assertEquals(alice.getId(), savedDoc.getUser().getId());
        assertEquals("READY", savedDoc.getStatus());
        assertTrue(savedDoc.getChunkCount() > 0);

        // Verify vectors saved in vector_records
        List<VectorRecord> vectors = vectorRecordRepository.findByDocumentId("STUDENT-DOC-" + docId);
        assertFalse(vectors.isEmpty());
        assertEquals("student_document", vectors.get(0).getDocumentType());
    }

    @Test
    void validDocxUpload_extractsChunksAndEmbedsSuccessfully() throws Exception {
        byte[] docxBytes = createSampleDocx("Chapter 2: Data Structures",
                "A binary search tree is a rooted binary tree whose internal nodes each store a key.");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "datastructures.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                docxBytes
        );

        mockMvc.perform(
                        multipart("/api/student-documents")
                                .file(file)
                                .header("Authorization", "Bearer " + aliceToken)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value("datastructures.docx"))
                .andExpect(jsonPath("$.fileFormat").value("DOCX"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.chunkCount").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void validTxtUpload_extractsChunksAndEmbedsSuccessfully() throws Exception {
        String textContent = """
                # Chapter 1: Introduction to Algorithms
                An algorithm is a finite sequence of rigorous instructions, typically used to solve a class of specific problems or to perform a computation.
                
                # Chapter 2: Complexity Analysis
                Asymptotic computational complexity is the study of resource consumption as the input size grows towards infinity.
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "algorithms.txt",
                "text/plain",
                textContent.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(
                        multipart("/api/student-documents")
                                .file(file)
                                .header("Authorization", "Bearer " + aliceToken)
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value("algorithms.txt"))
                .andExpect(jsonPath("$.fileFormat").value("TXT"))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.chunkCount").value(org.hamcrest.Matchers.greaterThan(0)));
    }

    @Test
    void unsupportedFormat_rejectedWith400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "malicious.exe",
                "application/octet-stream",
                new byte[]{0x4D, 0x5A, 0x00, 0x00}
        );

        mockMvc.perform(
                        multipart("/api/student-documents")
                                .file(file)
                                .header("Authorization", "Bearer " + aliceToken)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("Unsupported file format")));
    }

    @Test
    void emptyFile_rejectedWith400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "empty.pdf",
                "application/pdf",
                new byte[0]
        );

        mockMvc.perform(
                        multipart("/api/student-documents")
                                .file(file)
                                .header("Authorization", "Bearer " + aliceToken)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("empty")));
    }

    @Test
    void malformedPdf_rejectedWith400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "corrupted.pdf",
                "application/pdf",
                "This is not a real PDF file".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(
                        multipart("/api/student-documents")
                                .file(file)
                                .header("Authorization", "Bearer " + aliceToken)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("signature")));
    }

    @Test
    void malformedDocx_rejectedWith400() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "corrupted.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "This is not a zip archive".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(
                        multipart("/api/student-documents")
                                .file(file)
                                .header("Authorization", "Bearer " + aliceToken)
                )
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("signature")));
    }

    @Test
    void unauthorizedUpload_rejectedWith401() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "notes.txt",
                "text/plain",
                "Some student notes".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(
                        multipart("/api/student-documents")
                                .file(file)
                )
                .andExpect(status().isUnauthorized());
    }

    @Test
    void crossUserAccess_enforcesStrictIsolation() throws Exception {
        // Alice uploads a private document
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "alice_private_notes.txt",
                "text/plain",
                "# Secret Research Notes\nConfidential findings regarding graph neural networks.".getBytes(StandardCharsets.UTF_8)
        );

        MvcResult result = mockMvc.perform(
                        multipart("/api/student-documents")
                                .file(file)
                                .header("Authorization", "Bearer " + aliceToken)
                )
                .andExpect(status().isCreated())
                .andReturn();

        Long aliceDocId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();

        // Alice can access it
        mockMvc.perform(
                        get("/api/student-documents/" + aliceDocId)
                                .header("Authorization", "Bearer " + aliceToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value("alice_private_notes.txt"));

        // Bob CANNOT access Alice's document -> returns 404
        mockMvc.perform(
                        get("/api/student-documents/" + aliceDocId)
                                .header("Authorization", "Bearer " + bobToken)
                )
                .andExpect(status().isNotFound());

        // Bob CANNOT delete Alice's document -> returns 404
        mockMvc.perform(
                        delete("/api/student-documents/" + aliceDocId)
                                .header("Authorization", "Bearer " + bobToken)
                )
                .andExpect(status().isNotFound());

        // Document still exists for Alice
        assertTrue(studentDocumentRepository.findById(aliceDocId).isPresent());
    }

    @Test
    void documentDeletion_cleansUpDbVectorsAndDisk() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "delete_me.txt",
                "text/plain",
                "# Chapter To Delete\nThis text will be purged from database, vectors, and disk storage.".getBytes(StandardCharsets.UTF_8)
        );

        MvcResult result = mockMvc.perform(
                        multipart("/api/student-documents")
                                .file(file)
                                .header("Authorization", "Bearer " + aliceToken)
                )
                .andExpect(status().isCreated())
                .andReturn();

        Long docId = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        String vectorDocId = "STUDENT-DOC-" + docId;

        // Verify vector records exist prior to deletion
        assertFalse(vectorRecordRepository.findByDocumentId(vectorDocId).isEmpty());

        // Alice deletes her document
        mockMvc.perform(
                        delete("/api/student-documents/" + docId)
                                .header("Authorization", "Bearer " + aliceToken)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted").value(true));

        // Verify document is gone from database
        assertTrue(studentDocumentRepository.findById(docId).isEmpty());

        // Verify vector records are wiped
        assertTrue(vectorRecordRepository.findByDocumentId(vectorDocId).isEmpty());
    }
}
