# 🔍 Analisador de Performance - HelmetStore Backend

Script Python para análise automática de performance baseado nas métricas do endpoint de diagnóstico.

## 📋 Pré-requisitos

- Python 3.7+
- Backend rodando em http://localhost:8080
- Biblioteca `requests` (instalada automaticamente pelo script batch)

## 🚀 Como Usar

### Opção 1: Script Batch (Recomendado para Windows)

```bash
.\analyze.bat
```

### Opção 2: Diretamente com Python

```bash
# Instalar dependências
pip install requests

# Executar análise
python analyze_performance.py
```

## 📊 O que o Script Analisa

### 1. Hibernate/JPA
- ✅ Total de queries executadas
- ✅ Query mais lenta
- ✅ Cache hit ratio
- ✅ Relação entre loads e fetches (lazy loading)
- 🚨 Detecta problemas de cache
- 🚨 Detecta excesso de lazy loading

### 2. Queries SQL
- ✅ Top 5 queries mais executadas
- ✅ Top 5 queries mais lentas
- ✅ Total de queries únicas
- 🚨 Detecta possíveis N+1 queries
- 🚨 Detecta queries lentas (> 100ms)

### 3. Requisições HTTP
- ✅ Total de requisições
- ✅ Requisições lentas
- ✅ Performance por endpoint (mean, max, count)
- 🚨 Detecta endpoints lentos (> 500ms)
- 🚨 Detecta alta taxa de requisições lentas

### 4. JVM
- ✅ Uso de memória heap
- ✅ Contagem de threads
- 🚨 Detecta uso crítico de memória (> 85%)
- 🚨 Detecta uso alto de memória (> 70%)
- 🚨 Detecta excesso de threads (> 100)

### 5. Cache
- ✅ Lista de caches configurados
- ✅ Tipo de cada cache
- 🚨 Detecta ausência de cache

## 📈 Output do Script

### Console
O script exibe:
- 🚀 Status da conexão
- 📊 Análise de cada área (Hibernate, Queries, HTTP, JVM, Cache)
- 🎯 Relatório consolidado com:
  - 🔴 Problemas críticos
  - 🟠 Avisos
  - 🎯 Recomendações priorizadas
  - ✅ Status geral do sistema

### Arquivo JSON
Salva um arquivo `performance_report_YYYYMMDD_HHMMSS.json` contendo:
```json
{
  "timestamp": "2026-01-26T10:30:00",
  "summary": {
    "critical_issues": 2,
    "warnings": 5,
    "total_issues": 7
  },
  "issues": [...],
  "recommendations": [...],
  "raw_data": {...}
}
```

## 🎯 Thresholds Configurados

Você pode modificar os thresholds no arquivo `analyze_performance.py`:

```python
THRESHOLDS = {
    'slow_query_avg_time': 100,      # ms
    'slow_request_mean': 500,        # ms
    'memory_usage_critical': 85,     # %
    'memory_usage_warning': 70,      # %
    'n_plus_one_critical': 10,       # queries
    'query_count_per_request': 20,   # queries
    'cache_hit_ratio_warning': 70,   # %
    'thread_count_warning': 100      # threads
}
```

## 📝 Exemplo de Output

```
🚀 ANALISADOR DE PERFORMANCE - HelmetStore Backend
================================================================================

🚀 Conectando ao endpoint: http://localhost:8080/api/diagnostics/performance
✅ Dados coletados com sucesso!

📊 === ANÁLISE DO HIBERNATE ===

Total de Queries: 156
Query mais lenta: 245ms

📊 === ANÁLISE DE QUERIES ===

Total de queries únicas: 23

🔥 Top 5 Queries Mais Executadas:
  1. Count: 45 | Avg: 12ms | select si from SaleItem si where si.sale.id = ?...
  2. Count: 23 | Avg: 8ms | select p from Product p where p.id = ?...

🐌 Top 5 Queries Mais Lentas:
  1. Avg: 245ms | Count: 2 | select s from Sale s left join fetch s.items...

📊 === ANÁLISE HTTP ===

Total de Requisições: 89
Requisições Lentas: 7

🎯 Endpoints com Performance:
  🟠 GET /api/sales
     Mean: 427ms | Max: 1250ms | Count: 15
  🟢 GET /api/products
     Mean: 45ms | Max: 120ms | Count: 34

📊 === ANÁLISE JVM ===

💾 Memória Heap: 512MB / 2GB (25.6%)
⚡ Threads: 45 (pico: 52)

================================================================================
🎯 RELATÓRIO DE ANÁLISE DE PERFORMANCE
================================================================================

📊 Resumo:
  🔴 Problemas Críticos: 2
  🟠 Avisos: 5
  Total de Issues: 7

🚨 Problemas Identificados:

  🔴 CRÍTICOS:
    • Possível N+1: Query executada 45 vezes
      Query: select si from SaleItem si where si.sale.id = ?...

  🟠 AVISOS:
    • Endpoint lento: GET /api/sales (427ms)
    • Query lenta: 245ms (média)

🎯 Recomendações de Otimização (Priorizadas):

  1. 🔴 [CRITICAL] Corrigir N+1 Query
     Query executada 45x. Usar @EntityGraph ou JOIN FETCH
     Impacto Esperado: 85% de melhoria

  2. 🟠 [HIGH] Otimizar GET /api/sales
     Latência média de 427ms. Verificar queries e lógica de negócio
     Impacto Esperado: 60% de melhoria

  3. 🟠 [HIGH] Otimizar Query Lenta
     Query com 245ms (média). Adicionar índices no banco
     Impacto Esperado: 70% de melhoria

📊 Status Geral:
  🔴 Sistema requer atenção imediata!

================================================================================

✅ Relatório salvo em: performance_report_20260126_103000.json
```

## 🔄 Workflow Recomendado

1. **Coleta de Dados** (1-2 dias)
   ```bash
   # Use a aplicação normalmente
   # Execute o script algumas vezes ao dia
   .\analyze.bat
   ```

2. **Análise dos Relatórios**
   - Compare arquivos JSON de diferentes momentos
   - Identifique padrões recorrentes
   - Priorize problemas críticos

3. **Implementação de Otimizações**
   - Siga as recomendações do script
   - Implemente uma otimização por vez
   - Execute o script antes e depois

4. **Validação**
   ```bash
   # Execute análise antes da otimização
   .\analyze.bat
   
   # Implemente a otimização
   
   # Execute análise depois
   .\analyze.bat
   
   # Compare os relatórios JSON
   ```

## 🛠️ Customização

### Modificar URL do Backend
```python
BASE_URL = "http://localhost:8080"  # Altere aqui
```

### Adicionar Novas Análises
Adicione métodos na classe `PerformanceAnalyzer`:
```python
def analyze_custom_metric(self):
    """Sua análise customizada"""
    custom_data = self.data.get('custom', {})
    # Sua lógica aqui
```

E chame no método `run()`:
```python
def run(self):
    # ...
    self.analyze_custom_metric()
    # ...
```

## 📚 Documentação Relacionada

- [OBSERVABILITY.md](OBSERVABILITY.md) - Guia completo de observabilidade
- [PERFORMANCE_OPTIMIZATION_GUIDE.md](PERFORMANCE_OPTIMIZATION_GUIDE.md) - Como otimizar
- [OBSERVABILITY_SUMMARY.md](OBSERVABILITY_SUMMARY.md) - Sumário da implementação

## 🆘 Troubleshooting

### Erro: "requests module not found"
```bash
pip install requests
```

### Erro: "Connection refused"
Certifique-se que o backend está rodando:
```bash
.\run-backend.bat
```

### Erro: "401 Unauthorized"
Os endpoints de diagnóstico devem estar públicos. Verifique `SecurityConfig.java`:
```java
private static final String[] PUBLIC_ENDPOINTS = {
    "/actuator/**",
    "/api/diagnostics/**"
};
```

## 📈 Integração com CI/CD

Você pode executar o script no pipeline para monitorar performance continuamente:

```yaml
# GitHub Actions example
- name: Performance Analysis
  run: |
    python analyze_performance.py
    # Falha se houver problemas críticos
    python -c "import json; report=json.load(open('performance_report_*.json')); exit(report['summary']['critical_issues'])"
```

---

**Criado**: Janeiro 2026  
**Versão**: 1.0  
**Status**: ✅ Pronto para uso
