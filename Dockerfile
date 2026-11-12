# Build: Maven + JDK 17. output.css is committed, so no npm step here.
FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline
COPY src src
# Tests already run in GitHub Actions CI on every PR.
RUN ./mvnw -q -B -DskipTests package

# Run: JRE only.
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
# Render free tier has 512 MB RAM.
# Times (createdAt) are saved in the JVM zone; Render runs in UTC, users are in Vietnam.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=Asia/Ho_Chi_Minh"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
