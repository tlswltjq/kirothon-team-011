package dev.kirothon.resume.resume;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublishRaceTest {
    @Autowired
    MockMvc mvc;
    @Autowired
    ResumeVersionRepository versions;
    @MockitoSpyBean
    PdfProcessor processor;

    @Test
    void req4_2_draftChangedAfterVerificationIsNotPublished() throws Exception {
        String body = mvc.perform(post("/api/users").contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\":\"테스터\"}")).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");
        String up = mvc.perform(multipart("/api/my/resumes")
                        .file(new MockMultipartFile("file", "r.pdf", "application/pdf", ResumePdfs.resume("")))
                        .param("title", "t").param("targetRole", "백엔드").header("X-Anon-Token", token))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String rid = JsonPath.read(up, "$.resumeId");
        String hash = JsonPath.read(up, "$.version.preview.previewHash");
        String base = "/api/my/resumes/" + rid + "/versions/1";

        // 게시의 마스킹 검증이 끝난 직후, 트랜잭션 시작 전에 다른 요청이 가릴 단어를 바꾼다.
        doAnswer(inv -> {
            Object result = inv.callRealMethod();
            mvc.perform(put(base + "/mask-terms").header("X-Anon-Token", token)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"terms\":[\"Backend\"]}"))
                    .andExpect(status().isOk());
            return result;
        }).when(processor).analyze(any(), any());

        mvc.perform(post(base + "/publish").header("X-Anon-Token", token).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"previewHash\":\"" + hash + "\",\"confirmed\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PREVIEW_OUTDATED"));
        UUID vid = versions.findByResumeIdAndVersionNo(UUID.fromString(rid), 1).orElseThrow().getId();
        assertThat(versions.findById(vid).orElseThrow().isPublished()).isFalse();
    }
}
