package dev.kirothon.resume.apidocs;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

class ApiDocsTest {
    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class Enabled {
        @Autowired
        MockMvc mvc;

        @Test
        void req4_1_openApiDocumentsAnonTokenSchemeAndMainPaths() throws Exception {
            mvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.info.title").value(OpenApiConfig.TITLE))
                    .andExpect(jsonPath("$.info.version").isString())
                    .andExpect(jsonPath("$.components.securitySchemes.anonToken.type").value("apiKey"))
                    .andExpect(jsonPath("$.components.securitySchemes.anonToken.in").value("header"))
                    .andExpect(jsonPath("$.components.securitySchemes.anonToken.name").value("X-Anon-Token"))
                    .andExpect(jsonPath("$.security[0].anonToken").exists())
                    .andExpect(jsonPath("$.paths['/api/users'].post").exists())
                    .andExpect(jsonPath("$.paths['/api/users/me'].get").exists())
                    .andExpect(jsonPath("$.paths['/api/my/resumes'].post").exists())
                    .andExpect(jsonPath("$.paths['/api/my/resumes/{resumeId}/versions/{no}/publish'].post").exists())
                    .andExpect(jsonPath("$.paths['/api/review-queue/next'].get").exists())
                    .andExpect(jsonPath("$.paths['/api/versions/{versionId}/reviews'].post").exists())
                    .andExpect(jsonPath("$.paths['/api/demo'].get").exists());
        }

        @Test
        void req4_1_publicEndpointsHaveNoSecurityRequirement() throws Exception {
            mvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.paths['/api/users'].post.security").isEmpty())
                    .andExpect(jsonPath("$.paths['/api/demo'].get.security").isEmpty())
                    .andExpect(jsonPath("$.paths['/api/users/me'].get.security").doesNotExist());
        }

        @Test
        void req4_1_anonUserAttributeIsNotADocumentedParameter() throws Exception {
            mvc.perform(get("/v3/api-docs"))
                    .andExpect(jsonPath("$.paths['/api/users/me'].get.parameters").doesNotExist())
                    .andExpect(jsonPath("$.paths['/api/review-queue/next'].get.parameters[*].name",
                            Matchers.contains("skip")));
        }

        @Test
        void req4_1_swaggerUiIsServedWithoutToken() throws Exception {
            mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        }
    }

    @Nested
    @SpringBootTest(properties = "API_DOCS_ENABLED=false")
    @AutoConfigureMockMvc
    @ActiveProfiles("test")
    class Disabled {
        @Autowired
        MockMvc mvc;

        @Test
        void req4_2_docsAreNotFoundWhenDisabled() throws Exception {
            mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
            mvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
        }
    }

    @Nested
    @SpringBootTest
    @AutoConfigureMockMvc
    @ActiveProfiles({"test", "prod"})
    class ProdDefault {
        @Autowired
        MockMvc mvc;

        @Test
        void req4_2_prodProfileDisablesDocsByDefault() throws Exception {
            mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        }
    }
}
