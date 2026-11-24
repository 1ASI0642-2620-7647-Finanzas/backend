# Cuaderno de Fiado Digital - backend
# Construcción multi-etapa: compila con Maven y ejecuta con un JRE ligero.
# Variables necesarias en producción (Render):
#   DATABASE_URL (host), DATABASE_PORT, DATABASE_NAME, DATABASE_USER, DATABASE_PASSWORD,
#   JWT_SECRET (mínimo 32 caracteres), ADMIN_CLAVE (clave del administrador), CORS_ORIGENES (URL del frontend), PORT.
# Opcionales: ADMIN_USUARIO, SEMILLA_DEMO (true carga los datos de demostración) y DATABASE_PARAMS
# (por ejemplo ?sslmode=require si la base exige SSL, como Neon o Supabase).
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q clean package -DskipTests

FROM eclipse-temurin:25-jre AS runtime
ENV SPRING_PROFILES_ACTIVE=prod
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
# MaxRAMPercentage evita que la JVM use toda la memoria del plan gratuito (512 MB) y el servicio se reinicie.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-jar", "app.jar"]
