FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY build/libs/global-dashboard-auth-0.1-all.jar application.jar
EXPOSE 8080
CMD ["java", "-jar", "application.jar"]
