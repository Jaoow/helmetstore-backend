# Etapa 1: Build com Maven usando JDK 21
FROM openjdk:21-jdk AS build

# Define diretório de trabalho
WORKDIR /app

# Copia arquivos essenciais primeiro para cache de dependências
COPY pom.xml .
COPY mvnw .
COPY .mvn .mvn

# Permissão de execução do Maven wrapper e cache das dependências
RUN chmod +x ./mvnw && ./mvnw dependency:go-offline

# Agora copia o código-fonte
COPY src src

# Compila o projeto e gera o JAR
RUN ./mvnw clean package -DskipTests

# Etapa 2: Imagem final para execução da aplicação
FROM openjdk:21-jdk

# Cria volume para arquivos temporários
VOLUME /tmp

# Copia o JAR gerado da etapa de build
COPY --from=build /app/target/*.jar app.jar

# Expõe a porta padrão do Spring Boot
EXPOSE 8080

# Comando de entrada
ENTRYPOINT ["java", "-jar", "/app.jar"]
