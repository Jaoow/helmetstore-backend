# HelmetStore Backend

Bem-vindo ao repositório do backend do HelmetStore! Este projeto é uma API desenvolvida em Java para gerenciar funcionalidades relacionadas a uma loja de capacetes.

## Tecnologias Utilizadas

- **Java**: Linguagem principal do projeto.
- **Spring Boot**: Framework para desenvolvimento backend.
- **Docker**: Para containerização e fácil implantação.
- **Maven**: Gerenciador de dependências e build da aplicação.

## Funcionalidades

- **Cadastro e Atualização de Produtos**: Gerenciamento de capacetes disponíveis na loja.
- **Controle de Estoque**: Atualização dinâmica de inventário.
- **Processamento de Pedidos**: Criação e gerenciamento de compras.
- **API RESTful**: Estruturada para facilitar integrações.
- **🔍 Observabilidade & Performance**: Sistema completo de monitoramento e análise de performance.

## 📊 Observabilidade e Monitoramento

Este projeto implementa um **sistema completo de observabilidade** para monitoramento e otimização de performance:

- ✅ **Detecção Automática de N+1 Queries**: Identifica e alerta sobre problemas de lazy loading
- ✅ **Monitoramento de Slow Queries**: Detecta queries lentas (> 100ms)
- ✅ **Métricas de HTTP**: Latência, throughput, requisições lentas
- ✅ **Análise de Cache**: Hits, misses, eficiência
- ✅ **Métricas de JVM**: CPU, memória, threads, GC
- ✅ **Hibernate Statistics**: Estatísticas completas do ORM
- ✅ **Endpoints de Diagnóstico**: API REST para visualizar métricas em tempo real
- ✅ **Integração Prometheus/Grafana**: Exportação de métricas

### Guias Disponíveis

| Documento | Descrição |
|-----------|-----------|
| [OBSERVABILITY_SUMMARY.md](OBSERVABILITY_SUMMARY.md) | 📋 Sumário da implementação |
| [OBSERVABILITY_QUICKSTART.md](OBSERVABILITY_QUICKSTART.md) | ⚡ Guia rápido (5 min) |
| [OBSERVABILITY.md](OBSERVABILITY.md) | 📚 Guia completo (60+ páginas) |
| [PERFORMANCE_OPTIMIZATION_GUIDE.md](PERFORMANCE_OPTIMIZATION_GUIDE.md) | 🎯 Guia de otimizações |
| [OPTIMIZATION_NOTES.md](OPTIMIZATION_NOTES.md) | 📝 Otimizações já realizadas |

### Quick Start - Monitoramento

```bash
# 1. Iniciar aplicação
./mvnw spring-boot:run

# 2. Ver health check
curl http://localhost:8080/actuator/health

# 3. Ver métricas completas
curl http://localhost:8080/api/diagnostics/performance

# 4. Ver métricas Prometheus
curl http://localhost:8080/actuator/prometheus
```

### Endpoints de Diagnóstico

```
GET /api/diagnostics/performance    # Visão geral completa
GET /api/diagnostics/hibernate      # Estatísticas do Hibernate
GET /api/diagnostics/queries        # Top queries e slow queries
GET /api/diagnostics/cache          # Estatísticas de cache
GET /api/diagnostics/jvm            # Recursos da JVM
GET /api/diagnostics/http           # Métricas de requisições
```

**📖 Para mais detalhes, consulte [OBSERVABILITY_QUICKSTART.md](OBSERVABILITY_QUICKSTART.md)**

## Como Executar o Projeto

### Pré-requisitos

Certifique-se de que você tem as seguintes ferramentas instaladas em sua máquina:
- [Java 17+](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
- [Maven](https://maven.apache.org/)
- [Docker](https://www.docker.com/)

### Passos para execução

1. Clone este repositório:
   ```bash
   git clone https://github.com/Jaoow/helmetstore-backend.git
   cd helmetstore-backend
   ```

2. Compile e empacote o projeto usando o Maven:
   ```bash
   mvn clean package
   ```

3. Execute a aplicação:
   ```bash
   java -jar target/helmetstore-backend-0.0.1-SNAPSHOT.jar
   ```

4. Alternativamente, você pode usar Docker para subir a aplicação:
   ```bash
   docker build -t helmetstore-backend .
   docker run -p 8080:8080 helmetstore-backend
   ```

5. A API estará disponível em `http://localhost:8080`.


## Contribuição

Contribuições são sempre bem-vindas! Sinta-se à vontade para abrir uma [issue](https://github.com/Jaoow/helmetstore-backend/issues) ou enviar um pull request.

## Licença

Este projeto está sob a licença MIT. Consulte o arquivo `LICENSE` para mais informações.

---

Desenvolvido com ♥ por [Jaoow](https://github.com/Jaoow).
