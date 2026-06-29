#!/usr/bin/env bash
# Bootstrap an EC2 instance for CodeStream API + worker (frontend on Vercel).
# Run as root or with sudo after cloning the repo to /opt/codestream-src (or set REPO_DIR).
set -euo pipefail

REPO_DIR="${REPO_DIR:-/opt/codestream-src}"
INSTALL_DIR="/opt/codestream"
ENV_FILE="/etc/codestream/env"
SERVICE_USER="codestream"

log() { echo "[bootstrap] $*"; }

if [[ $EUID -ne 0 ]]; then
  echo "Run with sudo." >&2
  exit 1
fi

detect_os() {
  if [[ -f /etc/os-release ]]; then
    # shellcheck source=/dev/null
    . /etc/os-release
    echo "${ID:-unknown}"
  else
    echo "unknown"
  fi
}

install_packages() {
  local os
  os="$(detect_os)"
  log "Detected OS: $os"

  case "$os" in
    amzn)
      dnf update -y
      dnf install -y java-17-amazon-corretto-devel maven docker nginx git
      systemctl enable --now docker
      ;;
    ubuntu|debian)
      apt-get update
      apt-get install -y openjdk-17-jdk maven docker.io nginx git
      systemctl enable --now docker
      ;;
    *)
      echo "Unsupported OS. Install Java 17, Maven, Docker, and nginx manually." >&2
      exit 1
      ;;
  esac
}

create_service_user() {
  if ! id "$SERVICE_USER" &>/dev/null; then
    useradd --system --create-home --home-dir "$INSTALL_DIR" --shell /sbin/nologin "$SERVICE_USER"
  fi
  usermod -aG docker "$SERVICE_USER"
}

build_app() {
  if [[ ! -d "$REPO_DIR" ]]; then
    echo "Repo not found at $REPO_DIR. Clone the project first." >&2
    exit 1
  fi

  log "Building Java services..."
  cd "$REPO_DIR"
  mvn clean install -DskipTests

  log "Building python-runner Docker image..."
  docker build -t codestream-python-runner:latest "$REPO_DIR/worker-service/docker/python-runner"

  log "Installing artifacts to $INSTALL_DIR..."
  install -d -o "$SERVICE_USER" -g "$SERVICE_USER" "$INSTALL_DIR"

  cp "$REPO_DIR/api-service/target/api-service-"*.jar "$INSTALL_DIR/api-service.jar"
  cp "$REPO_DIR/worker-service/target/worker-service-"*.jar "$INSTALL_DIR/worker-service.jar"

  if ! unzip -p "$INSTALL_DIR/api-service.jar" META-INF/MANIFEST.MF | grep -q "Main-Class:"; then
    echo "api-service.jar is not a runnable Spring Boot fat JAR. Check Maven spring-boot-maven-plugin repackage." >&2
    exit 1
  fi
  if ! unzip -p "$INSTALL_DIR/worker-service.jar" META-INF/MANIFEST.MF | grep -q "Main-Class:"; then
    echo "worker-service.jar is not a runnable Spring Boot fat JAR. Check Maven spring-boot-maven-plugin repackage." >&2
    exit 1
  fi

  chown -R "$SERVICE_USER:$SERVICE_USER" "$INSTALL_DIR"
}

install_env_and_systemd() {
  install -d -m 700 /etc/codestream
  if [[ ! -f "$ENV_FILE" ]]; then
    cp "$REPO_DIR/deploy/env.production.example" "$ENV_FILE"
    chmod 600 "$ENV_FILE"
    log "Created $ENV_FILE — edit it before starting services."
  else
    log "$ENV_FILE already exists; skipping."
  fi

  cp "$REPO_DIR/deploy/systemd/codestream-api.service" /etc/systemd/system/
  cp "$REPO_DIR/deploy/systemd/codestream-worker.service" /etc/systemd/system/
  systemctl daemon-reload
  log "Systemd units installed. Enable after configuring env:"
  log "  systemctl enable --now codestream-api codestream-worker"
}

install_packages
create_service_user
build_app
install_env_and_systemd

log "Done. Next steps:"
log "  1. Edit $ENV_FILE (DATABASE_URL, SQS URLs, CORS for Vercel domain)"
log "  2. Configure nginx for api.codestream.npsolver.io: see deploy/EC2-DEPLOY.md"
log "  3. systemctl enable --now codestream-api codestream-worker"
log "  4. Set API_SERVICE_URL on Vercel (see deploy/env.vercel.example) and redeploy"
