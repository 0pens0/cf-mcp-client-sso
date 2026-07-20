# Homelab VKS deployment status

## Done
- Feature branch `cursor/cf-mcp-client-k8s-2671` + PR https://github.com/0pens0/cf-mcp-client-sso/pull/7
- Version **1.6.0** with k8s profile, service-binding GenAI/Postgres wiring, Dockerfile, manifests
- Docker image built locally: `cf-mcp-client-k8s:1.6.0` (also `/opt/cursor/artifacts/cf-mcp-client-k8s-1.6.0.tar`)
- Agent joined Tailscale as `cursor` (`100.78.140.30`) with SOCKS5 on `127.0.0.1:1055`
- SSH to `oren-macbook-pro` reaches auth but **Permission denied** (no authorized key)

## Blocked now
| Need | Status |
|------|--------|
| Mac SSH access | Add agent pubkey below to `~/.ssh/authorized_keys` on Mac |
| Harbor `harbor.kuhn-labs.com` (192.168.82.200) | Not in advertised Tailscale subnet routes (sparks only advertises `10.0.x/24`) |
| kubeconfig for Tanzu Platform space | Expected on Mac once SSH works |

### Agent SSH public key (add on Mac)

```
ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGRZm+P+4ykSvOLA8KA5+clsUtQs+riGrnKORhNw6iKS ubuntu@cursor
```

```bash
# on oren-macbook-pro
mkdir -p ~/.ssh && chmod 700 ~/.ssh
echo 'ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIGRZm+P+4ykSvOLA8KA5+clsUtQs+riGrnKORhNw6iKS ubuntu@cursor' >> ~/.ssh/authorized_keys
chmod 600 ~/.ssh/authorized_keys
```

Also ensure Harbor subnet is advertised on Tailscale (e.g. include `192.168.82.0/24` on sparks / the node that can reach Harbor), **or** provide Harbor URL that is on `10.0.x/24`.

Once SSH works the agent will:
1. Pull kubeconfig / discover `tanzubotk8s` namespace + Tanzu Platform space services
2. `docker login` + push `cf-mcp-client-k8s:1.6.0` to Harbor
3. Claim/bind GenAI chat, GenAI embed, Postgres and deploy the app
