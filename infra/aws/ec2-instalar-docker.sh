#!/bin/bash
# Prepara una EC2 Amazon Linux 2023 para ejecutar Pedidos360 con Docker Compose.
# Uso: pegarlo en "Advanced details -> User data" al crear la instancia,
#      o ejecutarlo dentro de la instancia con:  sudo bash ec2-instalar-docker.sh
set -euo pipefail

dnf install -y docker git
systemctl enable --now docker
usermod -aG docker ec2-user

# Plugins de Docker: compose (docker compose ...) y buildx (necesario para --build)
PLUGINS=/usr/local/lib/docker/cli-plugins
mkdir -p "$PLUGINS"
curl -fsSL https://github.com/docker/compose/releases/download/v2.29.7/docker-compose-linux-x86_64 \
  -o "$PLUGINS/docker-compose"
curl -fsSL https://github.com/docker/buildx/releases/download/v0.17.1/buildx-v0.17.1.linux-amd64 \
  -o "$PLUGINS/docker-buildx"
chmod +x "$PLUGINS/docker-compose" "$PLUGINS/docker-buildx"

echo "Docker listo. Cierra la sesión SSH y vuelve a entrar para usar docker sin sudo."
