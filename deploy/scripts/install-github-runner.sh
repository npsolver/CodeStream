#!/usr/bin/env bash
# Install a GitHub Actions self-hosted runner on EC2 for CodeStream deploys.
# Run on the EC2 instance as root (sudo).
#
# Prerequisites:
#   1. CodeStream cloned at /opt/codestream-src and bootstrapped.
#   2. A one-time registration token from GitHub:
#      Repo → Settings → Actions → Runners → New self-hosted runner
#
# Usage:
#   sudo GITHUB_REPO=npsolver/CodeStream RUNNER_TOKEN=XXXX ./deploy/scripts/install-github-runner.sh
set -euo pipefail

GITHUB_REPO="${GITHUB_REPO:?Set GITHUB_REPO (e.g. npsolver/CodeStream)}"
RUNNER_TOKEN="${RUNNER_TOKEN:?Set RUNNER_TOKEN from GitHub → Settings → Actions → Runners}"
RUNNER_USER="${RUNNER_USER:-github-runner}"
RUNNER_NAME="${RUNNER_NAME:-codestream-ec2}"
RUNNER_LABELS="${RUNNER_LABELS:-codestream,ec2}"
RUNNER_DIR="/opt/actions-runner"
RUNNER_VERSION="${RUNNER_VERSION:-2.323.0}"

log() { echo "[install-runner] $*"; }

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

detect_arch() {
  case "$(uname -m)" in
    x86_64) echo "x64" ;;
    aarch64|arm64) echo "arm64" ;;
    *)
      echo "Unsupported architecture: $(uname -m)" >&2
      exit 1
      ;;
  esac
}

install_runner_deps() {
  local os
  os="$(detect_os)"
  log "Installing runner dependencies (OS: $os)"

  case "$os" in
    amzn)
      if command -v dnf &>/dev/null; then
        dnf install -y libicu
      else
        yum install -y libicu
      fi
      ;;
    ubuntu|debian)
      apt-get update
      apt-get install -y libicu70 libicu-dev || apt-get install -y libicu66 libicu-dev
      ;;
    *)
      echo "Install libicu manually, then re-run this script." >&2
      exit 1
      ;;
  esac
}

ARCH="$(detect_arch)"
install_runner_deps
TARBALL="actions-runner-linux-${ARCH}-${RUNNER_VERSION}.tar.gz"
DOWNLOAD_URL="https://github.com/actions/runner/releases/download/v${RUNNER_VERSION}/${TARBALL}"

log "Creating runner user: $RUNNER_USER"
if ! id "$RUNNER_USER" &>/dev/null; then
  useradd --system --create-home --shell /bin/bash "$RUNNER_USER"
fi

log "Granting deploy sudo to $RUNNER_USER"
cat > /etc/sudoers.d/github-runner-deploy <<EOF
# Allow the GitHub Actions runner to redeploy CodeStream.
${RUNNER_USER} ALL=(ALL) NOPASSWD: /usr/bin/git
${RUNNER_USER} ALL=(ALL) NOPASSWD: /opt/codestream-src/deploy/scripts/bootstrap-ec2.sh
${RUNNER_USER} ALL=(ALL) NOPASSWD: /usr/bin/systemctl
EOF
chmod 440 /etc/sudoers.d/github-runner-deploy

log "Downloading runner v${RUNNER_VERSION} (${ARCH})"
mkdir -p "$RUNNER_DIR"
cd "$RUNNER_DIR"
curl -fsSL -o "$TARBALL" "$DOWNLOAD_URL"
tar xzf "$TARBALL"
rm -f "$TARBALL"
chown -R "$RUNNER_USER:$RUNNER_USER" "$RUNNER_DIR"

log "Configuring runner (labels: $RUNNER_LABELS)"
sudo -u "$RUNNER_USER" ./config.sh \
  --url "https://github.com/${GITHUB_REPO}" \
  --token "$RUNNER_TOKEN" \
  --name "$RUNNER_NAME" \
  --labels "$RUNNER_LABELS" \
  --unattended \
  --replace

log "Installing and starting systemd service"
./svc.sh install "$RUNNER_USER"
./svc.sh start

log "Done. Verify in GitHub → Settings → Actions → Runners (status: Idle)."
log "The deploy job uses runs-on: [self-hosted, codestream]."
