# cf-mcp-client-k8s

Kubernetes / VKS fork of [cf-mcp-client-sso](https://github.com/0pens0/cf-mcp-client-sso)
for Tanzu Platform. Connects a chat model, an embedding model, and Postgres (pgvector)
so the existing document-upload / RAG features work on the same cluster as `tanzubotk8s`.

## Features
- Spring Boot + Angular chat UI
- GenAI via Tanzu Platform service bindings or `GENAI_*` env vars
- Postgres + pgvector for document embeddings
- MCP client support (unchanged from upstream)
- `k8s` Spring profile with optional demo-mode auth for sandbox

## Quick deploy (VKS)

```bash
# Build
docker build -t <registry>/cf-mcp-client-k8s:1.6.0 .
docker push <registry>/cf-mcp-client-k8s:1.6.0

# Discover sibling workload
kubectl get deploy,svc,ingress -A | grep -i tanzubot

# Apply (adjust namespace/host/image to match the cluster)
kubectl apply -f deploy/k8s/00-namespace.yaml
kubectl apply -f deploy/k8s/10-configmap.yaml
# create binding secrets — see deploy/k8s/secrets.example.yaml
kubectl apply -f deploy/k8s/20-deployment.yaml
kubectl apply -f deploy/k8s/30-service.yaml
kubectl apply -f deploy/k8s/40-ingress.yaml
kubectl -n demo set image deploy/cf-mcp-client-k8s app=<registry>/cf-mcp-client-k8s:1.6.0
```

Full binding contract and troubleshooting: [deploy/README.md](deploy/README.md).

## Local run (k8s profile without cluster)

```bash
./mvnw clean package
java -jar target/cf-mcp-client-1.6.0.jar --spring.profiles.active=k8s \
  -DGENAI_CHAT_CONFIG_URL=... -DGENAI_CHAT_API_KEY=... -DGENAI_CHAT_API_BASE=... \
  -DGENAI_EMBEDDING_CONFIG_URL=... -DGENAI_EMBEDDING_API_KEY=... -DGENAI_EMBEDDING_API_BASE=... \
  -Dspring.datasource.url=jdbc:postgresql://localhost:5432/vectordb \
  -Dspring.datasource.username=... -Dspring.datasource.password=...
```

## Cloud Foundry
This fork remains buildable for CF; see upstream `manifest.yml` / SSO docs. Prefer the
`cf-mcp-client-sso` repository for CF-first workflows.

## Version
**1.6.0** — see [RELEASE_NOTES.md](RELEASE_NOTES.md).
