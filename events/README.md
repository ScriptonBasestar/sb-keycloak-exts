# Keycloak Event Extensions

Keycloak에서 발생하는 사용자/관리자 이벤트를 다양한 메시징 시스템으로 전송하는 확장 모듈입니다.

## 🚀 Quick Start

```bash
# 1. JAR 다운로드
wget https://github.com/scriptonbasestar/sb-keycloak-exts/releases/download/v0.0.3/event-listener-kafka-all.jar
cp event-listener-kafka-all.jar $KEYCLOAK_HOME/providers/

# 2. 설정 (keycloak.conf)
spi-events-listener-kafka-bootstrap-servers=localhost:9092
spi-events-listener-kafka-event-topic=keycloak-events

# 3. 빌드 및 시작
bin/kc.sh build
bin/kc.sh start-dev

# 4. Admin Console에서 활성화
# Realm Settings → Events → Event Listeners → kafka-event-listener 체크
```

**상세 가이드**: [Quick Start Guide](docs/quickstart.md)

## 📚 Documentation

### 시작하기
- **[Quick Start](docs/quickstart.md)** - 5분 안에 시작하기
- **[Architecture](docs/architecture.md)** - 시스템 구조 이해

### 설정 및 운영
- **[Configuration](docs/configuration.md)** - 전체 설정 옵션
- **[Resilience Patterns](docs/resilience.md)** - Circuit Breaker, Retry, DLQ
- **[Monitoring](docs/monitoring.md)** - Prometheus & Grafana
- **[Troubleshooting](docs/troubleshooting.md)** - 문제 해결

### 개발
- **[Refactoring History](docs/refactoring-history.md)** - 주요 변경 사항

## 🏗️ Supported Transports

| Transport | Port | Use Case |
|-----------|------|----------|
| **Kafka** | 9090 | 대용량 이벤트 스트리밍 |
| **RabbitMQ** | 9091 | AMQP 기반 메시징 |
| **NATS** | 9092 | 경량 메시징, JetStream |
| **Redis Streams** | 9093 | 간단한 이벤트 큐 |
| **Azure Service Bus** | 9094 | Azure 클라우드 통합 |
| **AWS SQS/SNS** | 9095 | AWS 클라우드 통합 |

**Status**: ✅ All Production Ready

## 🔥 Key Features

### Resilience Patterns
- ✅ **Circuit Breaker** - 장애 전파 방지, Fast Fail
- ✅ **Retry Policy** - 자동 재시도 (Exponential Backoff)
- ✅ **Dead Letter Queue** - 실패 이벤트 보관 및 재처리
- ✅ **Batch Processing** - 처리량 최적화 (선택적)

### Observability
- ✅ **Prometheus Metrics** - 메트릭 노출 (각 Transport별 독립 포트)
- ✅ **Grafana Dashboard** - 사전 구성된 대시보드 제공
- ✅ **Event Filtering** - 타입/Realm 기반 필터링

### Production Ready
- ✅ **Zero Data Loss** - DLQ를 통한 데이터 손실 방지
- ✅ **High Availability** - Circuit Breaker를 통한 장애 격리
- ✅ **Performance** - Batch 처리로 10K-50K events/sec

## 📊 Architecture

```
Keycloak Event
    ↓
EventListenerProvider (SPI)
    ↓
Circuit Breaker → Retry Policy → ConnectionManager
    ↓                    ↓
    DLQ           Message Broker
```

**자세한 구조**: [Architecture Overview](docs/architecture.md)

## 📖 Module Structure

```
events/
├── event-listener-common/     # 공통 라이브러리
│   ├── resilience/            # CircuitBreaker, RetryPolicy
│   ├── dlq/                   # DeadLetterQueue
│   ├── batch/                 # BatchProcessor
│   └── metrics/               # Prometheus 메트릭
│
├── event-listener-kafka/      # Kafka Transport
├── event-listener-rabbitmq/   # RabbitMQ Transport
├── event-listener-nats/       # NATS Transport
├── event-listener-redis/      # Redis Transport
├── event-listener-azure/      # Azure Service Bus Transport
├── event-listener-aws/        # AWS SQS/SNS Transport
│
├── docs/                      # 📚 공식 문서
│   ├── README.md             # 문서 인덱스
│   ├── quickstart.md         # 빠른 시작
│   ├── architecture.md       # 아키텍처
│   ├── configuration.md      # 설정 가이드
│   ├── resilience.md         # Resilience 패턴
│   ├── monitoring.md         # 모니터링
│   ├── troubleshooting.md    # 문제 해결
│   └── refactoring-history.md # 변경 이력
│
├── examples/                  # 예제 및 학습 자료
│   ├── docker-compose/       # Docker Compose 스택
│   └── scripts/              # 유틸리티 스크립트
│
└── archive/                   # 구버전 문서 (참고용)
```

## 🔧 Configuration Example

### Kafka (Production)

```properties
# Connection
spi-events-listener-kafka-bootstrap-servers=kafka1:9092,kafka2:9092,kafka3:9092
spi-events-listener-kafka-acks=all
spi-events-listener-kafka-compression-type=gzip

# Topics
spi-events-listener-kafka-event-topic=prod-keycloak-events
spi-events-listener-kafka-admin-event-topic=prod-keycloak-admin

# Resilience
spi-events-listener-kafka-enable-circuit-breaker=true
spi-events-listener-kafka-enable-retry=true
spi-events-listener-kafka-enable-dead-letter-queue=true
spi-events-listener-kafka-dlq-persist-to-file=true
spi-events-listener-kafka-dlq-path=/var/keycloak/dlq/kafka

# Monitoring
spi-events-listener-kafka-enable-prometheus=true
spi-events-listener-kafka-prometheus-port=9090
```

**전체 옵션**: [Configuration Guide](docs/configuration.md)

## 📈 Monitoring

### Prometheus Metrics

```bash
# 메트릭 확인
curl http://localhost:9090/metrics

# 주요 메트릭
keycloak_events_total{event_type="LOGIN",realm="master"} 15234
keycloak_events_failed_total{error_type="ConnectException"} 42
keycloak_circuit_breaker_state{transport="kafka"} 0
keycloak_dlq_size{transport="kafka"} 0
```

### Grafana Dashboard

사전 구성된 대시보드 제공:
- Event Throughput
- Failure Rate
- Circuit Breaker State
- DLQ Size
- Processing Latency (P50, P95, P99)

**설정 방법**: [Monitoring Guide](docs/monitoring.md)

## 🐛 Troubleshooting

### Circuit Breaker OPEN?

```bash
# 상태 확인
curl http://localhost:9090/metrics | grep circuit_breaker_state

# Kafka 연결 테스트
telnet localhost 9092

# Keycloak 재시작 (Circuit Breaker 리셋)
systemctl restart keycloak
```

### Events not being sent?

```bash
# Realm 설정 확인
# Admin Console → Realm Settings → Events → Event Listeners

# 로그 확인
tail -f keycloak.log | grep EventListener

# 메트릭 확인
curl http://localhost:9090/metrics | grep keycloak_events_total
```

**더 많은 문제 해결**: [Troubleshooting Guide](docs/troubleshooting.md)

## 🔄 Version History

| Version | Date | Key Changes |
|---------|------|-------------|
| **v0.0.3** | 2025-01-06 | Manager 리팩토링, 테스트 인프라, 87 unit tests |
| **v0.0.2** | 2025-01-04 | Resilience Patterns, Prometheus Metrics |
| **v0.0.1** | 2024-12-01 | Initial Release, 6 Transports |

**상세 이력**: [Refactoring History](docs/refactoring-history.md)

## 🚧 Roadmap

### v0.0.4 (Planned)
- [ ] Google Pub/Sub Transport
- [ ] IBM MQ Transport
- [ ] Apache Pulsar Transport

### v0.1.0 (Planned)
- [ ] 성능 벤치마크
- [ ] Advanced Metrics
- [ ] Health Check API

## 🤝 Contributing

프로젝트에 기여하고 싶으시면:

1. [Architecture](docs/architecture.md) 문서 읽기
2. [Refactoring History](docs/refactoring-history.md)에서 코딩 스타일 확인
3. 새 Transport 추가 또는 버그 수정
4. PR 제출

## 📝 License

Apache License 2.0

## 🆘 Support

- **Documentation**: [docs/](docs/)
- **Issues**: [GitHub Issues](https://github.com/scriptonbasestar/sb-keycloak-exts/issues)
- **Slack**: #keycloak-extensions

---

**Quick Links**:
- [Get Started in 5 Minutes](docs/quickstart.md)
- [View Architecture](docs/architecture.md)
- [Configure for Production](docs/configuration.md)
- [Monitor with Prometheus](docs/monitoring.md)
