# syntax=docker/dockerfile:1
# 멀티 스테이지 빌드: 컨테이너 안에서 Gradle로 bootJar를 만들고, 계층화 jar를 비루트 JRE 이미지에 넣는다.
# digest는 멀티 아키텍처 인덱스(amd64·arm64) 기준이다.

# ---- build: Gradle Wrapper로 bootJar 생성 ----
# eclipse-temurin:21-jdk-noble, 2026-11-25 pull 기준 digest
FROM eclipse-temurin:21-jdk-noble@sha256:dc333cdac241f073941f043792f2a2f9b790bfe7cd98ef2843c08880986708de AS build
WORKDIR /workspace
# 의존성 캐시 레이어: 빌드 스크립트와 wrapper를 먼저 복사한다.
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon dependencies > /dev/null
COPY src ./src
RUN --mount=type=cache,target=/root/.gradle \
    ./gradlew --no-daemon bootJar -x test
# Spring Boot 4 계층 추출(jarmode=tools). 결과: extracted/{dependencies,spring-boot-loader,snapshot-dependencies,application}
RUN java -Djarmode=tools -jar build/libs/app.jar extract --layers --launcher --destination extracted

# ---- runtime: 기존 JRE digest 유지 ----
# 이미지 digest는 2026-11-11 ARM64에서 검증한 공식 Temurin 21 JRE(eclipse-temurin:21-jre-noble).
FROM eclipse-temurin:21-jre-noble@sha256:0eda5830ed7b669abc246a8dc520ea8a23cab06f9a8d4b6964109e2f2394e46f
# PDF에 글꼴이 포함되지 않은 경우 런타임 렌더링용 한글 글꼴
RUN apt-get update \
 && apt-get install -y --no-install-recommends fonts-noto-cjk \
 && rm -rf /var/lib/apt/lists/*
RUN mkdir -p /app /data && chown -R 10001:10001 /app /data
WORKDIR /app
# 자주 바뀌지 않는 계층부터 복사해 이미지 레이어 재사용을 늘린다.
COPY --from=build --chown=10001:10001 /workspace/extracted/dependencies/ ./
COPY --from=build --chown=10001:10001 /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=10001:10001 /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=10001:10001 /workspace/extracted/application/ ./
USER 10001:10001
# 컨테이너 안에서는 0.0.0.0에 바인딩한다. 호스트 공개는 compose에서 127.0.0.1로만 한다.
ENV SERVER_ADDRESS=0.0.0.0 \
    SERVER_PORT=18080 \
    MANAGEMENT_PORT=18081 \
    DATA_DIR=/data \
    HEALTHCHECK_URL=http://127.0.0.1:18081/actuator/health/readiness
EXPOSE 18080 18081
# 기본값은 prod readiness(관리 포트). 기본·demo 프로필은 HEALTHCHECK_URL=http://127.0.0.1:18080/actuator/health 로 덮어쓴다.
# curl은 베이스 이미지에 포함되어 있다.
HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=4 \
  CMD curl -fsS -o /dev/null "$HEALTHCHECK_URL" || exit 1
ENTRYPOINT ["java","-XX:MaxRAMPercentage=60","org.springframework.boot.loader.launch.JarLauncher"]
