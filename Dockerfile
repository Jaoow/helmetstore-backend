# Etapa 1: Build com Maven usando JDK 21
FROM amazoncorretto:21-alpine-jdk AS build

# Define diretório de trabalho
WORKDIR /app

# Copia arquivos essenciais primeiro para cache de dependências
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

# Permissão de execução do Maven wrapper e cache das dependências
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline -B

# Agora copia o código-fonte
COPY src src

# Compila o projeto e gera o JAR
RUN ./mvnw clean package -DskipTests -B

# Etapa 2: Imagem final para execução da aplicação
FROM amazoncorretto:21-alpine-jre

# Cria usuário não-root
RUN addgroup -S app && adduser -S app -G app

# Cria volume para arquivos temporários
VOLUME /tmp

# Copia o JAR gerado da etapa de build
COPY --from=build --chown=app:app /app/target/*.jar app.jar

# Muda para usuário não-root
USER app

# Expõe a porta padrão do Spring Boot
EXPOSE 8080

# Comando de entrada
ENTRYPOINT ["java", "-jar", "/app.jar"]
