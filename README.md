# video-processor-worker

Microsserviço responsável pelo processamento assíncrono de vídeos. Consome mensagens de uma fila SQS, extrai frames do vídeo utilizando FFmpeg, armazena os resultados no S3 e publica um evento de conclusão no SNS.

## Visão Geral

```
SQS (poll a cada 5s)
    ↓
Download do vídeo (S3)
    ↓
Extração de frames (FFmpeg/JavaCV)
    ├─ Frames em intervalos regulares
    ├─ Primeiro frame como JPEG
    └─ ZIP com todos os frames
    ↓
Upload dos resultados (S3)
    ↓
Publicação do evento de conclusão (SNS)
    ↓
Deleção da mensagem da fila (SQS)
```

Em caso de erro, publica um evento com `status: FAILED` e **não deleta** a mensagem da fila (para reprocessamento).

## Stack

- **Linguagem:** Kotlin 2.2.21
- **Framework:** Spring Boot 4.0.2
- **Java:** 21
- **AWS SDK:** v2 (S3, SQS, SNS)
- **Processamento de vídeo:** JavaCV 1.5.10 + FFmpeg
- **Observabilidade:** OpenTelemetry + Prometheus
- **Build:** Gradle (Kotlin DSL)

## Estrutura do Projeto

```
src/main/kotlin/br/com/felixgilioli/videoprocessorworker/
├── config/
│   ├── S3Config.kt
│   ├── SqsConfig.kt
│   ├── SnsConfig.kt
│   └── properties/
│       ├── S3Properties.kt
│       ├── SqsProperties.kt
│       └── SnsProperties.kt
├── consumer/
│   └── VideoProcessingConsumer.kt   # Poll SQS + orquestração do processamento
├── service/
│   ├── StorageService.kt            # Operações S3 (download, upload, presigned URL)
│   ├── NotificationService.kt       # Publicação SNS com propagação de trace
│   └── FrameExtractorService.kt     # Extração de frames com FFmpeg
├── dto/
│   ├── VideoProcessingMessage.kt    # Mensagem recebida da fila
│   └── VideoCompletedEvent.kt       # Evento publicado ao SNS
└── VideoProcessorWorkerApplication.kt
```

## Mensagens

### Entrada (SQS) — `VideoProcessingMessage`

```json
{
  "videoId": "uuid",
  "userId": "string",
  "videoUrl": "string"
}
```

### Saída (SNS) — `VideoCompletedEvent`

```json
{
  "videoId": "uuid",
  "userId": "string",
  "status": "READY | FAILED",
  "zipUrl": "presigned-url | null",
  "firstFrameUrl": "presigned-url | null"
}
```

## Configuração

As configurações são definidas em `src/main/resources/application.yaml`:

```yaml
server:
  port: 8082

storage:
  endpoint: http://localhost:9000
  bucket: videos

sqs:
  endpoint: http://localhost:4566
  video-processing-queue: video-processing-queue

sns:
  endpoint: http://localhost:4566
  video-completed-topic: video-completed-topic
```

## Infraestrutura Local

Para rodar localmente, os seguintes serviços são necessários:

| Serviço | Porta | Finalidade |
|---------|-------|-----------|
| MinIO | 9000 | Armazenamento S3-compatível |
| LocalStack | 4566 | Emulação de SQS e SNS |
| OpenTelemetry Collector | 4318 | Coleta de traces |

**Credenciais padrão:**
- MinIO: `minioadmin:minioadmin`
- AWS (LocalStack): `test:test`

## Como Executar

### Localmente

```bash
./gradlew bootRun
```

### Docker

```bash
# Build da imagem
docker-compose -f docker-compose.build.yml build

# Executar diretamente
docker run -p 8082:8082 felixgilioli/video-processor-worker
```

## Testes

```bash
./gradlew test
```

Cobertura de código gerada via JaCoCo em `build/reports/jacoco/test/html/`.

```bash
# Verificar cobertura
./gradlew jacocoTestReport
```

### Estrutura de Testes

| Classe | Testes | O que cobre |
|--------|--------|-------------|
| `StorageServiceTest` | 4 | Download, upload e geração de presigned URLs |
| `NotificationServiceTest` | 3 | Publicação SNS e propagação de erros |
| `VideoProcessingConsumerTest` | 5 | Fluxo completo, tratamento de erros e tracing |

## Observabilidade

- **Tracing distribuído:** Propagação do header W3C `traceparent` de SQS até SNS
- **Métricas:** Prometheus em `/actuator/prometheus`
- **Health check:** `/actuator/health`
- **Atributos de span:** `videoId` é adicionado a cada span de processamento

## CI/CD

O pipeline `.github/workflows/build.yaml` executa dois jobs:

1. **Build & Analyze:** Compila o projeto, executa análise SonarQube e faz build da imagem Docker
2. **Push:** Publica a imagem no Docker Hub com as tags `latest` e `v{run_number}`

**Secrets necessários:** `SONAR_TOKEN`, `DOCKERHUB_USERNAME`, `DOCKERHUB_TOKEN`

## Qualidade de Código

- **SonarQube:** Análise estática integrada ao CI
- **JaCoCo:** Relatórios de cobertura em HTML e XML
- **Projeto SonarQube:** `felixgilioli_video-processor-worker`
