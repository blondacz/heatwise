Implementation
--

Done in **Scala** and running within **Docker** and orchestrated by **Kubernetes**.
Decisions are logged to **Kafka** Broker.

### Using following libraries:
- **Apache Pekko** (open sourced Akka) - for streaming and web/http 
- **Apache Kafka** - broker for storing decisions and latest state for recovery
- **circe** - for JSON serialization
- **sttp** - for HTTP client
- **pureconfig** - for configuration loading

### Deployment
- **Docker** - one application image and one kafka image
- **Kubernetes** - to maintain state and to orchestrate
- **Helm** - to deploy