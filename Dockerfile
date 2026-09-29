FROM maven:3.9.9-eclipse-temurin-17 AS build

WORKDIR /app

COPY pom.xml .
COPY src ./src

RUN mvn -DskipTests package

FROM eclipse-temurin:17-jre

WORKDIR /app

# 安装 lark-cli，保证 Skill + CLI 在容器里稳定可用，不依赖宿主机环境。
RUN apt-get update \
    && apt-get install -y --no-install-recommends nodejs npm ca-certificates \
    && npm install -g @larksuite/cli --registry=https://registry.npmmirror.com \
    && lark-cli --version \
    && apt-get clean \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
