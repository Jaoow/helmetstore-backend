#!/usr/bin/env python3
"""
Script para análise automática de performance do HelmetStore Backend
Coleta métricas do endpoint de diagnóstico e gera relatório com recomendações
"""

import requests
import json
from datetime import datetime
from typing import Dict, List, Tuple

# Configurações
BASE_URL = "http://localhost:8080"
DIAGNOSTICS_ENDPOINT = f"{BASE_URL}/api/diagnostics/performance"
HISTORY_ENDPOINT = f"{BASE_URL}/api/diagnostics/history"
SLOWEST_ENDPOINT = f"{BASE_URL}/api/diagnostics/history/slowest"

# Thresholds para análise
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

# Emojis para output
EMOJI = {
    'critical': '🔴',
    'warning': '🟠',
    'info': '🟡',
    'success': '🟢',
    'rocket': '🚀',
    'chart': '📊',
    'alert': '🚨',
    'target': '🎯',
    'check': '✅',
    'fire': '🔥',
    'snail': '🐌',
    'memory': '💾',
    'cpu': '⚡',
    'cache': '📦'
}

class PerformanceAnalyzer:
    def __init__(self, url: str, history_url: str, slowest_url: str):
        self.url = url
        self.history_url = history_url
        self.slowest_url = slowest_url
        self.data = None
        self.history_data = None
        self.slowest_data = None
        self.issues = []
        self.recommendations = []

    def fetch_data(self) -> bool:
        """Busca dados do endpoint de diagnóstico"""
        try:
            print(f"{EMOJI['rocket']} Conectando aos endpoints de diagnóstico...")

            # Busca dados de performance
            response = requests.get(self.url, timeout=10)
            response.raise_for_status()
            self.data = response.json()
            print(f"{EMOJI['check']} Dados de performance coletados!")

            # Busca histórico de requisições
            try:
                history_response = requests.get(self.history_url, timeout=10)
                history_response.raise_for_status()
                self.history_data = history_response.json()
                print(f"{EMOJI['check']} Histórico de requisições coletado!")
            except:
                print(f"{EMOJI['info']} Histórico não disponível (normal se backend foi recém-iniciado)")
                self.history_data = None

            # Busca requisições mais lentas
            try:
                slowest_response = requests.get(self.slowest_url + "?limit=10", timeout=10)
                slowest_response.raise_for_status()
                self.slowest_data = slowest_response.json()
                print(f"{EMOJI['check']} Top requisições lentas coletadas!")
            except:
                print(f"{EMOJI['info']} Dados de requisições lentas não disponíveis")
                self.slowest_data = None

            print()
            return True
        except requests.exceptions.RequestException as e:
            print(f"{EMOJI['alert']} Erro ao conectar: {e}")
            return False

    def analyze_hibernate(self):
        """Analisa estatísticas do Hibernate"""
        print(f"\n{EMOJI['chart']} === ANÁLISE DO HIBERNATE ===\n")

        hibernate = self.data.get('hibernate', {})

        if not hibernate.get('enabled'):
            print(f"{EMOJI['warning']} Hibernate Statistics está desabilitado")
            self.recommendations.append({
                'priority': 'medium',
                'title': 'Habilitar Hibernate Statistics',
                'description': 'spring.jpa.properties.hibernate.generate_statistics=true'
            })
            return

        queries = hibernate.get('queries', {})
        entities = hibernate.get('entities', {})
        collections = hibernate.get('collections', {})
        cache = hibernate.get('secondLevelCache', {})

        # Análise de queries
        total_queries = queries.get('total', 0)
        max_time = queries.get('maxExecutionTime', '0ms')
        slowest_query = queries.get('slowestQuery', 'N/A')

        print(f"Total de Queries: {total_queries}")
        print(f"Query mais lenta: {max_time}")

        if slowest_query != 'N/A':
            max_time_value = int(max_time.replace('ms', ''))
            if max_time_value > THRESHOLDS['slow_query_avg_time']:
                self.issues.append({
                    'severity': 'critical' if max_time_value > 500 else 'warning',
                    'type': 'slow_query',
                    'message': f"Query mais lenta: {max_time}",
                    'query': slowest_query[:100]
                })
                self.recommendations.append({
                    'priority': 'high',
                    'title': 'Otimizar Query Lenta',
                    'description': f'Query com {max_time} detectada. Adicionar índices ou otimizar JOIN FETCH'
                })

        # Análise de cache
        cache_hits = cache.get('hits', 0)
        cache_misses = cache.get('misses', 0)
        cache_total = cache_hits + cache_misses

        if cache_total > 0:
            cache_hit_ratio = (cache_hits / cache_total) * 100
            print(f"Cache Hit Ratio: {cache_hit_ratio:.1f}%")

            if cache_hit_ratio < THRESHOLDS['cache_hit_ratio_warning']:
                self.issues.append({
                    'severity': 'warning',
                    'type': 'cache_efficiency',
                    'message': f"Cache Hit Ratio baixo: {cache_hit_ratio:.1f}%"
                })
                self.recommendations.append({
                    'priority': 'medium',
                    'title': 'Melhorar Eficiência do Cache',
                    'description': 'Aumentar TTL, adicionar cache em mais lugares ou revisar invalidação'
                })

        # Análise de entidades
        entity_loads = entities.get('loads', 0)
        entity_fetches = entities.get('fetches', 0)

        if entity_loads > 0 and entity_fetches > entity_loads * 2:
            self.issues.append({
                'severity': 'warning',
                'type': 'lazy_loading',
                'message': f"Muitos fetches ({entity_fetches}) vs loads ({entity_loads})"
            })
            self.recommendations.append({
                'priority': 'medium',
                'title': 'Reduzir Lazy Loading',
                'description': 'Usar @EntityGraph ou JOIN FETCH para evitar queries adicionais'
            })

    def analyze_queries(self):
        """Analisa queries executadas"""
        print(f"\n{EMOJI['chart']} === ANÁLISE DE QUERIES ===\n")

        queries_data = self.data.get('queries', {})

        top_executed = queries_data.get('topExecuted', [])
        slowest = queries_data.get('slowest', [])
        total_unique = queries_data.get('totalUniqueQueries', 0)

        print(f"Total de queries únicas: {total_unique}")

        # Analisa top executadas (possível N+1)
        if top_executed:
            print(f"\n{EMOJI['fire']} Top 5 Queries Mais Executadas:")
            for i, query_info in enumerate(top_executed[:5], 1):
                count = query_info.get('count', 0)
                avg_time = query_info.get('avgTime', '0ms')
                query = query_info.get('query', '')[:80]

                print(f"  {i}. Count: {count} | Avg: {avg_time} | {query}...")

                if count > THRESHOLDS['n_plus_one_critical']:
                    self.issues.append({
                        'severity': 'critical',
                        'type': 'n_plus_one',
                        'message': f"Possível N+1: Query executada {count} vezes",
                        'query': query
                    })
                    self.recommendations.append({
                        'priority': 'critical',
                        'title': 'Corrigir N+1 Query',
                        'description': f'Query executada {count}x. Usar @EntityGraph ou JOIN FETCH',
                        'impact': '85%'
                    })

        # Analisa queries lentas
        if slowest:
            print(f"\n{EMOJI['snail']} Top 5 Queries Mais Lentas:")
            for i, query_info in enumerate(slowest[:5], 1):
                avg_time = query_info.get('avgTime', '0ms')
                count = query_info.get('count', 0)
                query = query_info.get('query', '')[:80]

                print(f"  {i}. Avg: {avg_time} | Count: {count} | {query}...")

                avg_time_value = int(avg_time.replace('ms', ''))
                if avg_time_value > THRESHOLDS['slow_query_avg_time']:
                    self.issues.append({
                        'severity': 'warning',
                        'type': 'slow_query',
                        'message': f"Query lenta: {avg_time} (média)",
                        'query': query
                    })
                    self.recommendations.append({
                        'priority': 'high',
                        'title': 'Otimizar Query Lenta',
                        'description': f'Query com {avg_time} (média). Adicionar índices no banco',
                        'impact': '70%'
                    })

    def analyze_http(self):
        """Analisa requisições HTTP"""
        print(f"\n{EMOJI['chart']} === ANÁLISE HTTP ===\n")

        http_data = self.data.get('http', {})

        endpoints = http_data.get('endpoints', [])
        total_requests = http_data.get('totalRequests', 0)
        slow_requests = http_data.get('slowRequests', 0)
        exceptions = http_data.get('exceptions', 0)

        print(f"Total de Requisições: {total_requests}")
        print(f"Requisições Lentas: {slow_requests}")
        print(f"Exceptions: {exceptions}")

        # Verifica se há dados HTTP
        if total_requests == 0 and not endpoints:
            print(f"\n{EMOJI['info']} Nenhuma métrica HTTP coletada ainda.")
            print(f"  Isso é normal após reiniciar a aplicação.")
            print(f"  Faça algumas requisições e execute a análise novamente.")
            return

        if total_requests > 0:
            slow_percentage = (slow_requests / total_requests) * 100
            slow_pct_str = http_data.get('slowPercentage', f'{slow_percentage:.1f}%')
            print(f"% Lentas: {slow_pct_str}")

            if slow_percentage > 10:
                self.issues.append({
                    'severity': 'critical' if slow_percentage > 20 else 'warning',
                    'type': 'slow_requests',
                    'message': f"{slow_percentage:.1f}% das requisições estão lentas (> 500ms)"
                })

        # Verifica exceptions
        if exceptions > 0:
            self.issues.append({
                'severity': 'critical',
                'type': 'http_exceptions',
                'message': f"{exceptions} exceptions em requisições HTTP"
            })

            exception_types = http_data.get('exceptionsByType', {})
            if exception_types:
                print(f"\n{EMOJI['alert']} Tipos de Exceptions:")
                for exc_type, count in exception_types.items():
                    print(f"  • {exc_type}: {count}")

        if endpoints:
            print(f"\n{EMOJI['target']} Top Endpoints por Latência:")

            # Já vem ordenado do backend, mas podemos ordenar novamente para garantir
            for i, endpoint in enumerate(endpoints[:10], 1):
                uri = endpoint.get('uri', 'N/A')
                method = endpoint.get('method', 'N/A')
                status = endpoint.get('status', 'N/A')
                mean = endpoint.get('mean', '0ms')
                max_time = endpoint.get('max', '0ms')
                total_time = endpoint.get('total', '0ms')
                count = endpoint.get('count', 0)

                mean_value = float(mean.replace('ms', ''))
                max_value = float(max_time.replace('ms', ''))

                # Define emoji baseado na latência
                emoji = EMOJI['success']
                if mean_value > 3000:
                    emoji = EMOJI['critical']
                elif mean_value > THRESHOLDS['slow_request_mean']:
                    emoji = EMOJI['warning']

                print(f"\n  {i}. {emoji} {method} {uri}")
                print(f"     Status: {status} | Count: {count}")
                print(f"     Mean: {mean} | Max: {max_time} | Total: {total_time}")

                # Alerta para endpoints muito lentos
                if mean_value > 3000:
                    self.issues.append({
                        'severity': 'critical',
                        'type': 'very_slow_endpoint',
                        'message': f"Endpoint MUITO lento: {method} {uri} ({mean} média)",
                        'endpoint': f"{method} {uri}"
                    })
                    self.recommendations.append({
                        'priority': 'critical',
                        'title': f'🔥 URGENTE: Otimizar {method} {uri}',
                        'description': f'Latência média de {mean}! Verificar N+1 queries, adicionar índices, usar cache',
                        'impact': '90%'
                    })
                elif mean_value > THRESHOLDS['slow_request_mean']:
                    self.issues.append({
                        'severity': 'warning',
                        'type': 'slow_endpoint',
                        'message': f"Endpoint lento: {method} {uri} ({mean})",
                        'endpoint': f"{method} {uri}"
                    })
                    self.recommendations.append({
                        'priority': 'high',
                        'title': f'Otimizar {method} {uri}',
                        'description': f'Latência média de {mean}. Verificar queries e lógica de negócio',
                        'impact': '60%'
                    })

                # Alerta se max é muito maior que mean (inconsistência)
                if max_value > mean_value * 3 and mean_value > 100:
                    self.issues.append({
                        'severity': 'info',
                        'type': 'inconsistent_latency',
                        'message': f"Latência inconsistente em {method} {uri}: max ({max_time}) >> mean ({mean})"
                    })

            # Mostra slowest endpoint se disponível
            slowest = http_data.get('slowestEndpoint')
            if slowest:
                print(f"\n{EMOJI['snail']} Endpoint Mais Lento:")
                print(f"  {slowest.get('method')} {slowest.get('uri')}")
                print(f"  Mean: {slowest.get('mean')} | Max: {slowest.get('max')}")
        else:
            print(f"\n{EMOJI['info']} Nenhum endpoint registrado ainda.")

    def analyze_jvm(self):
        """Analisa recursos da JVM"""
        print(f"\n{EMOJI['chart']} === ANÁLISE JVM ===\n")

        jvm_data = self.data.get('jvm', {})

        memory = jvm_data.get('memory', {})
        heap = memory.get('heap', {})
        threads = jvm_data.get('threads', {})

        # Análise de memória
        usage_percent = heap.get('usagePercent', '0%')
        usage_value = float(usage_percent.replace('%', ''))

        used = heap.get('used', 'N/A')
        max_mem = heap.get('max', 'N/A')

        print(f"{EMOJI['memory']} Memória Heap: {used} / {max_mem} ({usage_percent})")

        if usage_value > THRESHOLDS['memory_usage_critical']:
            self.issues.append({
                'severity': 'critical',
                'type': 'memory_usage',
                'message': f"Uso de memória crítico: {usage_percent}"
            })
            self.recommendations.append({
                'priority': 'critical',
                'title': 'Reduzir Uso de Memória',
                'description': 'Usar paginação, projeções (DTOs), ou aumentar heap size',
                'impact': '75%'
            })
        elif usage_value > THRESHOLDS['memory_usage_warning']:
            self.issues.append({
                'severity': 'warning',
                'type': 'memory_usage',
                'message': f"Uso de memória alto: {usage_percent}"
            })

        # Análise de threads
        current_threads = threads.get('current', 0)
        peak_threads = threads.get('peak', 0)

        print(f"{EMOJI['cpu']} Threads: {current_threads} (pico: {peak_threads})")

        if current_threads > THRESHOLDS['thread_count_warning']:
            self.issues.append({
                'severity': 'warning',
                'type': 'thread_count',
                'message': f"Muitas threads ativas: {current_threads}"
            })

    def analyze_cache(self):
        """Analisa uso de cache"""
        print(f"\n{EMOJI['chart']} === ANÁLISE DE CACHE ===\n")

        cache_data = self.data.get('cache', {})
        caches = cache_data.get('caches', [])

        if not caches:
            print(f"{EMOJI['info']} Nenhum cache configurado")
            self.recommendations.append({
                'priority': 'medium',
                'title': 'Configurar Cache',
                'description': 'Adicionar cache para dados frequentemente acessados',
                'impact': '50%'
            })
            return

        print(f"Total de caches: {len(caches)}")
        for cache in caches:
            name = cache.get('name', 'N/A')
            cache_type = cache.get('type', 'N/A')
            print(f"  {EMOJI['cache']} {name} ({cache_type})")

    def analyze_request_history(self):
        """Analisa histórico de requisições"""
        print(f"\n{EMOJI['chart']} === HISTÓRICO DE REQUISIÇÕES ===\n")

        if not self.history_data:
            print(f"{EMOJI['info']} Histórico não disponível")
            return

        stats = self.history_data.get('stats', {})
        recent = self.history_data.get('recentRequests', [])

        print(f"Total de requisições rastreadas: {stats.get('totalRequests', 0)}")
        print(f"Latência média: {stats.get('avgDuration', 0)}ms")
        print(f"Latência máxima: {stats.get('maxDuration', 0)}ms")
        print(f"Requisições lentas: {stats.get('slowRequests', 0)}")
        print(f"Requisições com N+1: {stats.get('nPlusOneRequests', 0)}")

        # Analisa slowest do histórico se disponível
        if self.slowest_data:
            slowest_list = self.slowest_data.get('slowest', [])

            if slowest_list:
                print(f"\n{EMOJI['fire']} TOP 5 REQUISIÇÕES MAIS LENTAS (Histórico):")

                for i, req in enumerate(slowest_list[:5], 1):
                    uri = req.get('uri', 'N/A')
                    method = req.get('method', 'N/A')
                    duration = req.get('durationMs', 0)
                    timestamp = req.get('timestamp', 'N/A')
                    query_count = req.get('queryCount', 0)
                    had_n_plus_one = req.get('hadNPlusOne', False)

                    emoji = EMOJI['success']
                    severity = 'info'
                    if duration > 5000:
                        emoji = EMOJI['critical']
                        severity = 'critical'
                    elif duration > 1000:
                        emoji = EMOJI['warning']
                        severity = 'warning'

                    print(f"\n  {i}. {emoji} {method} {uri}")
                    print(f"     Duração: {duration}ms | Queries: {query_count} | N+1: {'SIM' if had_n_plus_one else 'não'}")
                    print(f"     Timestamp: {timestamp}")

                    # Adiciona como issue se for muito lento
                    if duration > 3000:
                        self.issues.append({
                            'severity': severity,
                            'type': 'very_slow_historical_request',
                            'message': f"Requisição MUITO lenta detectada no histórico: {method} {uri} ({duration}ms)",
                            'endpoint': f"{method} {uri}",
                            'timestamp': timestamp
                        })
                        self.recommendations.append({
                            'priority': 'critical' if duration > 5000 else 'high',
                            'title': f'🔥 OTIMIZAR {method} {uri}',
                            'description': f'Requisição demorou {duration}ms! Queries: {query_count}, N+1: {"SIM" if had_n_plus_one else "não"}',
                            'impact': '90%'
                        })


    def generate_report(self):
        """Gera relatório consolidado"""
        print("\n" + "="*80)
        print(f"{EMOJI['target']} RELATÓRIO DE ANÁLISE DE PERFORMANCE")
        print("="*80)

        # Resumo de problemas
        critical_count = len([i for i in self.issues if i['severity'] == 'critical'])
        warning_count = len([i for i in self.issues if i['severity'] == 'warning'])

        print(f"\n{EMOJI['chart']} Resumo:")
        print(f"  {EMOJI['critical']} Problemas Críticos: {critical_count}")
        print(f"  {EMOJI['warning']} Avisos: {warning_count}")
        print(f"  Total de Issues: {len(self.issues)}")

        # Lista problemas por severidade
        if self.issues:
            print(f"\n{EMOJI['alert']} Problemas Identificados:")

            # Críticos primeiro
            critical_issues = [i for i in self.issues if i['severity'] == 'critical']
            if critical_issues:
                print(f"\n  {EMOJI['critical']} CRÍTICOS:")
                for issue in critical_issues:
                    print(f"    • {issue['message']}")
                    if 'query' in issue:
                        print(f"      Query: {issue['query'][:80]}...")

            # Warnings
            warning_issues = [i for i in self.issues if i['severity'] == 'warning']
            if warning_issues:
                print(f"\n  {EMOJI['warning']} AVISOS:")
                for issue in warning_issues:
                    print(f"    • {issue['message']}")

        # Recomendações priorizadas
        if self.recommendations:
            print(f"\n{EMOJI['target']} Recomendações de Otimização (Priorizadas):")

            # Ordena por prioridade
            priority_order = {'critical': 0, 'high': 1, 'medium': 2, 'low': 3}
            sorted_recs = sorted(
                self.recommendations,
                key=lambda x: priority_order.get(x['priority'], 99)
            )

            for i, rec in enumerate(sorted_recs[:10], 1):
                priority = rec['priority']
                emoji_map = {
                    'critical': EMOJI['critical'],
                    'high': EMOJI['warning'],
                    'medium': EMOJI['info'],
                    'low': EMOJI['success']
                }

                print(f"\n  {i}. {emoji_map.get(priority, '')} [{priority.upper()}] {rec['title']}")
                print(f"     {rec['description']}")
                if 'impact' in rec:
                    print(f"     Impacto Esperado: {rec['impact']} de melhoria")

        # Status geral
        print(f"\n{EMOJI['chart']} Status Geral:")
        if critical_count == 0 and warning_count == 0:
            print(f"  {EMOJI['check']} Sistema está saudável!")
        elif critical_count == 0:
            print(f"  {EMOJI['warning']} Sistema estável mas com áreas de melhoria")
        else:
            print(f"  {EMOJI['critical']} Sistema requer atenção imediata!")

        print("\n" + "="*80 + "\n")

        # Salva relatório em arquivo
        self.save_report()

    def save_report(self):
        """Salva relatório em arquivo JSON"""
        timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
        filename = f"performance_report_{timestamp}.json"

        report = {
            'timestamp': datetime.now().isoformat(),
            'summary': {
                'critical_issues': len([i for i in self.issues if i['severity'] == 'critical']),
                'warnings': len([i for i in self.issues if i['severity'] == 'warning']),
                'total_issues': len(self.issues)
            },
            'issues': self.issues,
            'recommendations': self.recommendations,
            'raw_data': self.data
        }

        with open(filename, 'w', encoding='utf-8') as f:
            json.dump(report, f, indent=2, ensure_ascii=False)

        print(f"{EMOJI['check']} Relatório salvo em: {filename}")

    def run(self):
        """Executa análise completa"""
        print(f"\n{EMOJI['rocket']} ANALISADOR DE PERFORMANCE - HelmetStore Backend")
        print(f"{'='*80}\n")

        if not self.fetch_data():
            return False

        # Executa todas as análises
        self.analyze_hibernate()
        self.analyze_queries()
        self.analyze_http()
        self.analyze_request_history()  # Nova análise!
        self.analyze_jvm()
        self.analyze_cache()

        # Gera relatório final
        self.generate_report()

        return True


def main():
    analyzer = PerformanceAnalyzer(
        DIAGNOSTICS_ENDPOINT,
        HISTORY_ENDPOINT,
        SLOWEST_ENDPOINT
    )

    try:
        success = analyzer.run()
        exit(0 if success else 1)
    except KeyboardInterrupt:
        print(f"\n\n{EMOJI['warning']} Análise interrompida pelo usuário")
        exit(130)
    except Exception as e:
        print(f"\n{EMOJI['alert']} Erro durante análise: {e}")
        import traceback
        traceback.print_exc()
        exit(1)


if __name__ == "__main__":
    main()
