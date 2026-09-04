# ZETA 番 を 1 コンテナで動かす。
# 無料ホスティングはサービス 1 つしか持てないので、
# フロントエンドのビルド成果物を Spring Boot の static に入れて同一オリジンで配信する。
# こうすると CORS も不要になり、Service Worker のスコープも素直になる。

# ---- 1. フロントエンドをビルド --------------------------------------------
FROM node:22-alpine AS web
WORKDIR /web
COPY owcs/frontend/package.json owcs/frontend/package-lock.json ./
RUN npm ci
COPY owcs/frontend/ ./
RUN npm run build

# ---- 2. バックエンドをビルド（static にフロントを同梱） --------------------
FROM maven:3.9-eclipse-temurin-21 AS api
WORKDIR /api
# 依存だけ先に解決してレイヤーキャッシュを効かせる
COPY owcs/backend/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY owcs/backend/src ./src
COPY --from=web /web/dist ./src/main/resources/static
RUN mvn -B -q package -DskipTests

# ---- 3. 実行 ---------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=api /api/target/*.jar app.jar

# 無料枠は 512MB なので、ヒープをコンテナ上限からの割合で抑える
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -Xss512k"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
