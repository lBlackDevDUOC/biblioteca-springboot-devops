#!/usr/bin/env bash
# ============================================================
# PREPARACIÓN DE EC2 - AMAZON LINUX / AWS LEARNER LAB
# ============================================================
# Instala únicamente lo que necesita el HOST:
#   - Docker
#   - Docker Compose
#   - Git y curl para diagnóstico
#
# Java, Maven, PostgreSQL y Nginx vivirán dentro de contenedores.
# ============================================================
set -Eeuo pipefail

BASE=/opt/biblioteca
READY="$BASE/.bootstrap-ready"
FAILED="$BASE/.bootstrap-failed"

on_error() {
  rc=$?
  echo "ERROR preparando EC2 (código $rc)."
  sudo mkdir -p "$BASE"
  sudo touch "$FAILED"
  sudo rm -f "$READY"
  exit "$rc"
}
trap on_error ERR

# Actualizar metadatos e instalar Docker y utilidades.
sudo yum makecache -y
# curl ya viene como curl-minimal en Amazon Linux 2023 (instalar "curl" choca con ese paquete)
sudo yum install -y docker git

# Iniciar Docker y dejarlo habilitado para reinicios de la EC2.
sudo systemctl enable docker
sudo systemctl start docker

# Permitir que ec2-user use Docker en futuras sesiones.
sudo usermod -aG docker ec2-user || true

# Esperar a que Docker realmente responda.
for i in {1..30}; do
  if sudo docker info >/dev/null 2>&1; then
    break
  fi
  echo "Esperando Docker... intento $i/30"
  sleep 2
done
sudo docker info >/dev/null

# ------------------------------------------------------------
# DOCKER COMPOSE
# ------------------------------------------------------------
# Primero comprobamos si el plugin ya viene con la instalación.
if ! sudo docker compose version >/dev/null 2>&1; then

  # Intentar instalar el paquete disponible en algunos Amazon Linux.
  sudo yum install -y docker-compose-plugin || true
fi

# Si todavía no existe, instalar el plugin oficial como fallback.
if ! sudo docker compose version >/dev/null 2>&1; then
  sudo mkdir -p /usr/local/lib/docker/cli-plugins
  sudo curl -fL \
    "https://github.com/docker/compose/releases/latest/download/docker-compose-linux-x86_64" \
    -o /usr/local/lib/docker/cli-plugins/docker-compose
  sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
fi

# ------------------------------------------------------------
# DOCKER BUILDX
# ------------------------------------------------------------
# docker compose build necesita buildx 0.17 o superior y el paquete
# docker de Amazon Linux trae una versión más antigua.
BUILDX_ACTUAL=$(sudo docker buildx version 2>/dev/null | grep -oE '[0-9]+\.[0-9]+\.[0-9]+' | head -1 || true)
if [ -z "$BUILDX_ACTUAL" ] || [ "$(printf '%s\n' 0.17.0 "$BUILDX_ACTUAL" | sort -V | head -1)" != "0.17.0" ]; then
  sudo mkdir -p /usr/local/lib/docker/cli-plugins
  sudo curl -fL \
    "https://github.com/docker/buildx/releases/download/v0.19.3/buildx-v0.19.3.linux-amd64" \
    -o /usr/local/lib/docker/cli-plugins/docker-buildx
  sudo chmod +x /usr/local/lib/docker/cli-plugins/docker-buildx
fi

# ------------------------------------------------------------
# SWAP DE 2 GB
# ------------------------------------------------------------
# Memoria de respaldo para compilar los 5 servicios Java en la EC2.
if ! sudo swapon --show | grep -q /swapfile; then
  sudo dd if=/dev/zero of=/swapfile bs=1M count=2048 status=none
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile >/dev/null
  sudo swapon /swapfile
  echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi

# Comprobaciones finales.
sudo docker --version
sudo docker compose version
sudo docker buildx version

# Directorio donde GitHub Actions copiará el repositorio.
sudo mkdir -p "$BASE/current"
sudo chown -R ec2-user:ec2-user "$BASE"

# Marcar la instancia como preparada.
touch "$READY"
rm -f "$FAILED"

echo "EC2 preparada correctamente para Docker Compose."
