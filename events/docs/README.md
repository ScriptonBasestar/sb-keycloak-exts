# Events Module Documentation

Keycloak Event Listener 확장 모듈 - 사용자/관리자 이벤트를 다양한 메시징 시스템으로 전송합니다.

## 📚 Documentation Structure

### Getting Started
- **[Architecture Overview](architecture.md)** - 시스템 구조 및 설계 원칙
- **[Quick Start Guide](quickstart.md)** - 5분 안에 시작하기

### Configuration
- **[Configuration Guide](configuration.md)** - 모든 설정 옵션 상세 설명
- **[Resilience Patterns](resilience.md)** - Circuit Breaker, Retry, DLQ, Batch 처리

### Operations
- **[Monitoring Guide](monitoring.md)** - Prometheus 메트릭 및 Grafana 대시보드
- **[Troubleshooting](troubleshooting.md)** - 일반적인 문제 해결

### Development
- **[Development Guide](development.md)** - 새로운 Transport 추가 및 테스트 작성
- **[Refactoring History](refactoring-history.md)** - 주요 리팩토링 기록

## 🚀 Supported Transports

| Transport | Status | Use Case |
|-----------|--------|----------|
| **Kafka** | ✅ Production Ready | 대용량 이벤트 스트리밍 |
| **RabbitMQ** | ✅ Production Ready | AMQP 기반 메시징 |
| **NATS** | ✅ Production Ready | 경량 메시징, JetStream |
| **Redis Streams** | ✅ Production Ready | 간단한 이벤트 큐 |
| **Azure Service Bus** | ✅ Production Ready | Azure 클라우드 통합 |
| **AWS SQS/SNS** | ✅ Production Ready | AWS 클라우드 통합 |

## 🏗️ Architecture Overview

```
Keycloak Event
    ↓
EventListenerProvider (SPI)
    ↓
Circuit Breaker → Retry Policy → Transport Connection Manager
    ↓                    ↓
    DLQ              Message Broker
```

**공통 기능**:
- ✅ Circuit Breaker (장애 전파 방지)
- ✅ Retry Policy (자동 재시도)
- ✅ Dead Letter Queue (실패 이벤트 보관)
- ✅ Batch Processing (처리량 최적화)
- ✅ Prometheus Metrics (모니터링)

## 📖 Quick Links

### For Operators
1. [설치 방법](../event-listener-kafka/README.md#installation)
2. [기본 설정](configuration.md#basic-setup)
3. [모니터링 설정](monitoring.md#prometheus-setup)

### For Developers
1. [아키텍처 이해](architecture.md)
2. [새 Transport 추가](development.md#adding-new-transport)
3. [테스트 작성](development.md#writing-tests)

## 🆘 Need Help?

- **Configuration Issues**: [Configuration Guide](configuration.md)
- **Performance Problems**: [Monitoring Guide](monitoring.md)
- **Error Messages**: [Troubleshooting](troubleshooting.md)
- **Development Questions**: [Development Guide](development.md)

## 📝 Version History

| Version | Date | Key Changes |
|---------|------|-------------|
| v0.0.3 | 2025-01-06 | Manager 리팩토링, 테스트 유틸리티 추가 |
| v0.0.2 | 2025-01-04 | Resilience Patterns 구현 |
| v0.0.1 | 2024-12-01 | 초기 릴리스 |

---

**Last Updated**: 2025-01-07
**Maintainers**: Keycloak Extensions Team
