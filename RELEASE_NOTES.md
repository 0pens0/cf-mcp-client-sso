# Release Notes — 1.6.0

## Summary
Adds a Kubernetes / VKS deployment path for Tanzu Platform so the chat client can run
alongside workloads such as `tanzubotk8s`, connecting to GenAI chat, GenAI embedding,
and Postgres (pgvector) for document upload / RAG.

## What changed
- **K8s profile** (`application-k8s.yaml`) with demo-mode security for sandbox testing
- **Service binding reader** for `SERVICE_BINDING_ROOT` (default `/bindings`)
- **GenAI discovery** now supports CF VCAP, Kubernetes bindings, and `GENAI_CHAT_*` /
  `GENAI_EMBEDDING_*` environment variables
- **Dockerfile** multi-stage build producing a Java 21 runtime image
- **`deploy/k8s/`** manifests (Namespace, ConfigMap, Deployment, Service, Ingress,
  ServiceBinding examples)
- Spring Boot Actuator for `/actuator/health` probes

## Why
Cloud Foundry service bindings (`VCAP_SERVICES`) do not apply on VKS. The app needed a
binding contract compatible with Tanzu Platform services on Kubernetes while preserving
the existing CF path.

## Breaking changes
None for CF deployments. The default CF SSO security configuration remains on the
`!k8s` profile. Activating `k8s` with `app.security.demo-mode=true` intentionally
permits unauthenticated API access for sandbox demos — set `demo-mode=false` and
configure OIDC before any shared/production use.

## Upgrade / deploy notes
1. Build image from the `Dockerfile`
2. Apply manifests under `deploy/k8s/` on the target VKS cluster
3. Provide GenAI chat, GenAI embedding, and Postgres credentials via binding secrets
   or env vars (see `deploy/README.md`)
4. Confirm chat responses and document upload against the ingress URL
