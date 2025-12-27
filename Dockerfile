FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
COPY build/libs/global-dashboard-authentication-0.1-all.jar application.jar
EXPOSE 8080 50051
CMD ["java", "-jar", "application.jar"]
