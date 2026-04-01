FROM maven:3.9.9-eclipse-temurin-17

WORKDIR /app
COPY . .

RUN mvn -DskipTests clean package

CMD ["sh", "-c", "mvn -DskipTests -Djetty.http.port=\"${PORT:-8080}\" jetty:run"]
