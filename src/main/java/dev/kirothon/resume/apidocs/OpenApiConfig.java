package dev.kirothon.resume.apidocs;

import dev.kirothon.resume.security.AnonTokenFilter;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.springdoc.core.customizers.GlobalOpenApiCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 요구사항 4.1: OpenAPI 문서 정보와 X-Anon-Token 보안 스킴. 켜고 끄기는 springdoc.* 프로퍼티(4.2)가 맡는다. */
@Configuration
public class OpenApiConfig {
    public static final String TITLE = "이게 다 너 잘되라고 하는 소리야 API";
    public static final String SCHEME = "anonToken";

    @Bean
    OpenAPI resumeReviewOpenApi(ObjectProvider<BuildProperties> build) {
        BuildProperties props = build.getIfAvailable();
        String version = props != null ? props.getVersion() : "dev";
        return new OpenAPI()
                .info(new Info().title(TITLE).version(version)
                        .description("익명 이력서 평가 API. POST /api/users로 받은 토큰을 X-Anon-Token 헤더에 넣는다."))
                .components(new Components().addSecuritySchemes(SCHEME, new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name(AnonTokenFilter.HEADER)
                        .description("POST /api/users 응답의 token")))
                .addSecurityItem(new SecurityRequirement().addList(SCHEME));
    }

    /** AnonTokenFilter가 토큰 없이 통과시키는 두 경로는 보안 요구를 비운다. */
    @Bean
    GlobalOpenApiCustomizer publicEndpointsCustomizer() {
        return openApi -> {
            if (openApi.getPaths() == null) {
                return;
            }
            clear(openApi.getPaths().get("/api/users"), PathItem.HttpMethod.POST);
            clear(openApi.getPaths().get("/api/demo"), PathItem.HttpMethod.GET);
        };
    }

    private static void clear(PathItem item, PathItem.HttpMethod method) {
        if (item == null) {
            return;
        }
        Operation op = item.readOperationsMap().get(method);
        if (op != null) {
            op.setSecurity(List.of());
        }
    }
}
