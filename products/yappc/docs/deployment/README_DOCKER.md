# YAPPC Docker Deployment Guide

Complete guide for deploying YAPPC (27 SDLC agents + Ollama LLM) using Docker Compose.

## 🚀 Quick Start

```bash
# 1. Navigate to YAPPC directory
cd products/yappc

# 2. Start all services
docker-compose -f docker-compose.yappc.yml up -d

# 3. Wait for initialization (1-2 minutes)
docker-compose -f docker-compose.yappc.yml logs -f ollama-loader

# 4. Verify services
docker-compose -f docker-compose.yappc.yml ps

# 5. Access services
# - YAPPC API: http://localhost:8080
# - Ollama: http://localhost:11434
# - Prometheus: http://localhost:9091
# - Grafana: http://localhost:3000 (admin/yappc_admin)
```

## 📦 Architecture

```
┌─────────────────────────────────────────────────────────┐
│                 YAPPC Docker Stack                       │
├─────────────────────────────────────────────────────────┤
│                                                           │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  │
│  │ YAPPC Agents │  │   Ollama     │  │  Prometheus  │  │
│  │  (27 agents) │←→│  (llama3.2)  │  │  (metrics)   │  │
│  │   Port 8080  │  │  Port 11434  │  │  Port 9091   │  │
│  └──────┬───────┘  └──────────────┘  └──────┬───────┘  │
│         │                                     │          │
│         └─────────────────┬──────────────────┘          │
│                           │                              │
│                    ┌──────▼───────┐                     │
│                    │    Grafana    │                     │
│                    │ (dashboards)  │                     │
│                    │   Port 3000   │                     │
│                    └───────────────┘                     │
│                                                           │
└─────────────────────────────────────────────────────────┘
```

## 🛠️ Prerequisites

- Docker Engine 24.0+ 
- Docker Compose v2.0+
- 8GB+ RAM (recommended: 16GB)
- 20GB+ disk space

## 📋 Services Overview

### 1. YAPPC Agents
- **Container**: `yappc-agents`
- **Port**: 8080 (API), 9090 (metrics)
- **Resources**: 2 CPU, 3GB RAM
- **Agents**: 27 (Architecture: 7, Implementation: 7, Testing: 6, Operations: 7)

### 2. Ollama LLM
- **Container**: `yappc-ollama`
- **Port**: 11434
- **Model**: llama3.2 (3.2B parameters)
- **Resources**: 4 CPU, 8GB RAM

### 3. Prometheus
- **Container**: `yappc-prometheus`
- **Port**: 9091
- **Retention**: 30 days
- **Scrape Interval**: 15s

### 4. Grafana
- **Container**: `yappc-grafana`
- **Port**: 3000
- **Default Credentials**: admin / yappc_admin

## 🔧 Configuration

### Environment Variables

Edit `docker-compose.yappc.yml` to customize:

```yaml
environment:
  # Ollama Configuration
  - OLLAMA_BASE_URL=http://ollama:11434
  - OLLAMA_MODEL=llama3.2  # or codellama, llama3
  - OLLAMA_TEMPERATURE=0.7 # 0.0-1.0
  - OLLAMA_MAX_TOKENS=4000 # per request
  
  # Agent Configuration
  - YAPPC_AGENT_COUNT=27
  - YAPPC_MODE=production # or development
  
  # Performance
  - JAVA_OPTS=-Xmx2G -Xms1G # JVM heap size
```

### Resource Limits

Adjust resources based on your hardware:

```yaml
deploy:
  resources:
    limits:
      cpus: '4'        # Increase for better performance
      memory: 8G       # Increase for larger models
    reservations:
      cpus: '2'
      memory: 4G
```

## 📊 Monitoring

### Prometheus Metrics

Access metrics at http://localhost:9091

Key metrics:
- `yappc_agent_requests_total` - Total agent invocations
- `yappc_agent_latency_seconds` - Agent response times
- `yappc_llm_tokens_total` - Token consumption
- `yappc_errors_total` - Error count

### Grafana Dashboards

1. Open http://localhost:3000
2. Login: admin / yappc_admin
3. Navigate to Dashboards → YAPPC Overview

Default dashboards:
- **YAPPC Agent Performance**: Request rates, latencies, errors
- **Ollama LLM Metrics**: Token usage, model performance
- **System Resources**: CPU, memory, disk usage

## 🧪 Testing

### Health Checks

```bash
# Check all services
docker-compose -f docker-compose.yappc.yml ps

# Test YAPPC API
curl http://localhost:8080/health

# Test Ollama
curl http://localhost:11434/api/tags

# Test Prometheus
curl http://localhost:9091/-/healthy

# Test Grafana
curl http://localhost:3000/api/health
```

### Sample Request

```bash
# Extract requirements (architecture.intake agent)
curl -X POST http://localhost:8080/api/agents/architecture.intake \
  -H "Content-Type: application/json" \
  -d '{
    "input": "Build a REST API for todo management with authentication"
  }'
```

## 🚨 Troubleshooting

### Ollama Model Not Loading

```bash
# Check loader logs
docker-compose -f docker-compose.yappc.yml logs ollama-loader

# Manually pull model
docker exec yappc-ollama ollama pull llama3.2

# Verify model loaded
docker exec yappc-ollama ollama list
```

### YAPPC Agents Not Starting

```bash
# Check logs
docker-compose -f docker-compose.yappc.yml logs yappc-agents

# Common issues:
# - Ollama not ready: Wait for ollama-loader to complete
# - Out of memory: Increase Docker memory limit
# - Port conflict: Change port mapping in compose file
```

### High Memory Usage

```bash
# Monitor resource usage
docker stats

# Reduce Ollama memory
# Edit docker-compose.yappc.yml:
deploy:
  resources:
    limits:
      memory: 4G  # Reduce from 8G

# Or use smaller model
environment:
  - OLLAMA_MODEL=llama3.2:1b  # 1B parameter model
```

## 🔄 Maintenance

### View Logs

```bash
# All services
docker-compose -f docker-compose.yappc.yml logs -f

# Specific service
docker-compose -f docker-compose.yappc.yml logs -f yappc-agents
docker-compose -f docker-compose.yappc.yml logs -f ollama
```

### Restart Services

```bash
# Restart all
docker-compose -f docker-compose.yappc.yml restart

# Restart specific service
docker-compose -f docker-compose.yappc.yml restart yappc-agents
```

### Update Services

```bash
# Pull latest images
docker-compose -f docker-compose.yappc.yml pull

# Recreate containers
docker-compose -f docker-compose.yappc.yml up -d --force-recreate
```

### Backup Data

```bash
# Backup volumes
docker run --rm -v yappc_ollama-data:/data -v $(pwd):/backup \
  alpine tar czf /backup/ollama-backup.tar.gz -C /data .

docker run --rm -v yappc_prometheus-data:/data -v $(pwd):/backup \
  alpine tar czf /backup/prometheus-backup.tar.gz -C /data .

docker run --rm -v yappc_grafana-data:/data -v $(pwd):/backup \
  alpine tar czf /backup/grafana-backup.tar.gz -C /data .
```

## 🛑 Shutdown

```bash
# Stop all services
docker-compose -f docker-compose.yappc.yml down

# Stop and remove volumes (WARNING: deletes data)
docker-compose -f docker-compose.yappc.yml down -v
```

## 📈 Performance Tuning

### For Production

1. **GPU Acceleration** (if available):
   ```yaml
   ollama:
     deploy:
       resources:
         reservations:
           devices:
             - driver: nvidia
               count: 1
               capabilities: [gpu]
   ```

2. **Increase Resources**:
   ```yaml
   yappc-agents:
     deploy:
       resources:
         limits:
           cpus: '4'
           memory: 4G
   ```

3. **Use SSD for Volumes**:
   ```yaml
   volumes:
     ollama-data:
       driver: local
       driver_opts:
         type: none
         o: bind
         device: /mnt/ssd/ollama-data
   ```

## 🔒 Security

### Production Hardening

1. **Change Default Passwords**:
   ```yaml
   grafana:
     environment:
       - GF_SECURITY_ADMIN_PASSWORD=<strong-password>
   ```

2. **Enable TLS**:
   - Add nginx reverse proxy
   - Configure SSL certificates
   - Restrict internal network access

3. **Firewall Rules**:
   ```bash
   # Only expose necessary ports
   # Close 11434 (Ollama) to external access
   # Use nginx for HTTPS on 443
   ```

## 📚 Additional Resources

- [Ollama Documentation](https://ollama.ai/docs)
- [Prometheus Configuration](https://prometheus.io/docs/prometheus/latest/configuration/configuration/)
- [Grafana Dashboards](https://grafana.com/docs/grafana/latest/dashboards/)
- [Docker Compose Reference](https://docs.docker.com/compose/compose-file/)

## ✅ Deployment Checklist

- [ ] Prerequisites installed (Docker, Compose, resources)
- [ ] Configuration customized (ports, resources, env vars)
- [ ] Services started (`docker-compose up -d`)
- [ ] Health checks passing (all services healthy)
- [ ] Ollama model loaded (llama3.2 available)
- [ ] API responding (curl test successful)
- [ ] Metrics collecting (Prometheus scraping)
- [ ] Dashboards configured (Grafana accessible)
- [ ] Backups configured (volume backup strategy)
- [ ] Monitoring alerts set up (optional)
- [ ] Security hardened (passwords, firewall)
- [ ] Documentation updated (team access guide)

---

**Status**: ✅ Production-Ready  
**Support**: See [DEPLOYMENT_GUIDE.md](./DEPLOYMENT_GUIDE.md) for advanced configuration
