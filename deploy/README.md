# Deploy cf-mcp-client-k8s to a VKS / Tanzu Platform Kubernetes cluster
#
# Prerequisites
# - kubectl context pointed at the same cluster as tanzubotk8s / tanzubotdemo
# - Container image built and pushed to a registry the cluster can pull
# - GenAI chat, GenAI embedding, and Postgres (pgvector) credentials available
#   as secrets or Tanzu service claims in the target namespace
#
# Quick start
#
# 1. Discover where tanzubotk8s lives:
#      kubectl get deploy,svc,ingress -A | grep -i tanzubot
#
# 2. Align namespace/host in the manifests with that workload, then:
#      kubectl apply -f deploy/k8s/00-namespace.yaml
#      kubectl apply -f deploy/k8s/10-configmap.yaml
#
# 3. Create binding secrets (see secrets.example.yaml) or use ServiceBindings:
#      kubectl apply -f deploy/k8s/50-servicebindings.yaml
#
# 4. Build and push the image, then set the Deployment image:
#      docker build -t <registry>/cf-mcp-client-k8s:1.6.0 .
#      docker push <registry>/cf-mcp-client-k8s:1.6.0
#      kubectl -n demo set image deploy/cf-mcp-client-k8s app=<registry>/cf-mcp-client-k8s:1.6.0
#
# 5. Apply workload + route:
#      kubectl apply -f deploy/k8s/20-deployment.yaml
#      kubectl apply -f deploy/k8s/30-service.yaml
#      kubectl apply -f deploy/k8s/40-ingress.yaml
#
# 6. Verify:
#      kubectl -n demo rollout status deploy/cf-mcp-client-k8s
#      curl -sS https://<ingress-host>/actuator/health
#      # Chat UI + document upload should work with demo-mode auth
#
# Binding contract (mounted under /bindings/<name>/)
# - GenAI: type, config_url, api_key, api_base (optional model / model_capabilities)
# - Postgres: type=postgresql, uri or host/port/database, username, password
#
# Environment fallbacks (Secret cf-mcp-client-k8s-env):
# - GENAI_CHAT_CONFIG_URL, GENAI_CHAT_API_KEY, GENAI_CHAT_API_BASE
# - GENAI_EMBEDDING_CONFIG_URL, GENAI_EMBEDDING_API_KEY, GENAI_EMBEDDING_API_BASE
# - SPRING_DATASOURCE_URL, SPRING_DATASOURCE_USERNAME, SPRING_DATASOURCE_PASSWORD
