package dev.kirothon.resume.persistence;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.kirothon.resume.ResumeReviewApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

/** 요구사항 1.2: prod 프로필에서 DB_URL·DB_USERNAME·DB_PASSWORD가 없으면 기동에 실패한다. */
class ProdProfileDbEnvTest {
    @Test
    void req1_2_prodWithoutDbEnvFailsToStart() {
        assertThatThrownBy(() -> new SpringApplicationBuilder(ResumeReviewApplication.class)
                .profiles("prod")
                .web(WebApplicationType.NONE)
                .run())
                .hasStackTraceContaining("DB_URL");
    }
}
