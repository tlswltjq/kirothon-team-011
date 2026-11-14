# 이미지 digest는 2026-11-11 ARM64에서 검증한 공식 Temurin 21 JRE.
FROM eclipse-temurin:21-jre-noble@sha256:0eda5830ed7b669abc246a8dc520ea8a23cab06f9a8d4b6964109e2f2394e46f
ARG JAR=build/libs/app.jar
# PDF에 글꼴이 포함되지 않은 경우 런타임 렌더링용 한글 글꼴
RUN apt-get update \
 && apt-get install -y --no-install-recommends fonts-noto-cjk \
 && rm -rf /var/lib/apt/lists/*
RUN mkdir -p /app /data && chown -R 10001:10001 /app /data
WORKDIR /app
COPY --chown=10001:10001 ${JAR} /app/app.jar
USER 10001:10001
ENV SERVER_ADDRESS=127.0.0.1 SERVER_PORT=18080 DATA_DIR=/data
ENTRYPOINT ["java","-XX:MaxRAMPercentage=60","-jar","/app/app.jar"]
