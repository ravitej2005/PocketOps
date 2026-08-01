FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Build context is the monorepo root. Copy backend build files first
# so dependency resolution is cached independently of source changes.
COPY backend/.mvn/ .mvn
COPY backend/mvnw backend/pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline

# Copy backend source and the shared proto contract.
# The protobuf-maven-plugin reads ${project.basedir}/../proto which resolves
# to /app/../proto = /proto inside this container.
COPY backend/src ./src
COPY proto /proto

RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
