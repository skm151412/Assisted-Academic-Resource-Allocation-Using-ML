FROM maven:3.9.9-eclipse-temurin-17

WORKDIR /app
COPY . .

RUN mvn -DskipTests clean package

CMD ["sh", "-c", "mvn -DskipTests -Djetty.http.port=${PORT:-8080} -Ddb.url=${DB_URL} -Ddb.username=${DB_USERNAME} -Ddb.password=${DB_PASSWORD} -Ddb.driverClassName=${DB_DRIVER} -Dhibernate.dialect=${HIBERNATE_DIALECT} jetty:run"]
