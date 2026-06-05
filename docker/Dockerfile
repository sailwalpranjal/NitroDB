FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build
COPY pom.xml ./
COPY nitrodb-core/ ./nitrodb-core/
COPY nitrodb-benchmarks/ ./nitrodb-benchmarks/
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
COPY --from=builder /build/nitrodb-benchmarks/target/*.jar ./nitrodb-bench.jar
ENV JVM_OPTS="-server -XX:+UseG1GC -XX:MaxDirectMemorySize=2g -Xms512m -Xmx1g"
ENTRYPOINT ["sh", "-c", "java $JVM_OPTS -jar nitrodb-bench.jar"]
