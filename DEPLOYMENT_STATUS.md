# Homelab VKS deployment status

## Done in this agent run
- Feature branch `cursor/cf-mcp-client-k8s-2671` + PR https://github.com/0pens0/cf-mcp-client-sso/pull/7
- Version **1.6.0** with k8s profile, service-binding GenAI/Postgres wiring, Dockerfile, manifests
- Fork tree on branch `fork/cf-mcp-client-k8s` (standalone `0pens0/cf-mcp-client-k8s` needs a PAT: `./scripts/publish-k8s-fork.sh`)
- Local verification: app boots with `SPRING_PROFILES_ACTIVE=k8s`, discovers mock bindings under `SERVICE_BINDING_ROOT`

## Blocked from this cloud VM
| Need | Why blocked |
|------|-------------|
| Create `0pens0/cf-mcp-client-k8s` repo | GitHub app token 403 on `createRepository` / fork |
| Reach homelab VKS | Lab is on **Tailscale**; this agent is not on the tailnet |
| kubeconfig | Not present in the cloud environment (expected on Mac) |
| `docker build` / kind | OverlayFS mount failures in the VM |
| Slack `#ai-tool-chat` | Slack MCP requires Cursor desktop authentication |

## Finish on the Mac (homelab)

```bash
# 1) Publish standalone fork (once)
cd /path/to/cf-mcp-client-sso
git checkout cursor/cf-mcp-client-k8s-2671
gh auth login   # use a PAT with repo create
./scripts/publish-k8s-fork.sh

# 2) Point at the VKS cluster that hosts tanzubotk8s
export KUBECONFIG=~/.kube/homelab-vks   # or your context
./scripts/discover-tanzubotk8s.sh

# 3) Build + push image (Harbor or your registry)
docker build -t harbor.lab.example/demo/cf-mcp-client-k8s:1.6.0 .
docker push harbor.lab.example/demo/cf-mcp-client-k8s:1.6.0

# 4) Create GenAI chat, GenAI embed, Postgres binding secrets
#    (copy names/structure from the live tanzubotk8s bindings)
#    see deploy/k8s/secrets.example.yaml

# 5) Deploy
IMAGE=harbor.lab.example/demo/cf-mcp-client-k8s:1.6.0 \
NAMESPACE=demo \
  ./scripts/deploy-k8s.sh

# 6) Verify chat + file upload, then post the route to #ai-tool-chat
```

Alternatively store a base64 kubeconfig as GitHub Actions secret `HOMELAB_KUBECONFIG` and run workflow **deploy-k8s**.
