#!/usr/bin/env bash

set -eu

if (( EUID != 0 )); then
    echo "[install_mongod] You must run this script as root." 1>&2
    exit 1
fi

DIST="$(lsb_release -i | cut -f 2)"
RELEASE="$(lsb_release -r | cut -f 2)"
CODENAME="$(lsb_release -c | cut -f 2)"

if ! [[ "$DIST" = "Ubuntu" && "$RELEASE" =~ ^(12.04|14.04|16.04)$ ]]; then
    echo "[install_mongod] This script is only compatible with Ubuntu 12.04, 14.04 or 16.04"
    exit 1
fi

echo "[install_mongod] Importing apt-get public key"
apt-key adv --keyserver hkp://keyserver.ubuntu.com:80 --recv EA312927

echo "[install_mongod] Adding mongodb.org repository"
LISTF_CONTENT="deb http://repo.mongodb.org/apt/ubuntu $CODENAME/mongodb-org/3.2 multiverse"
echo "$LISTF_CONTENT" | tee /etc/apt/sources.list.d/mongodb-org-3.2.list

echo "[install_mongod] Updating apt-get package database..."
apt-get update > /dev/null

echo "[install_mongod] Installing latest stable mongodb-org pkg"
apt-get install -y mongodb-org

if [ "$RELEASE" = "16.04" ]; then
  echo "[install_mongod] Creating systemd service init script (Ubuntu 16.04 only)"

  SERVICE_FILE="/lib/systemd/system/mongod.service"
  echo "[install_mongod] $SERVICE_FILE:"

  tee "$SERVICE_FILE" << EOL
[Unit]
Description=High-performance, schema-free document-oriented database
After=network.target
Documentation=https://docs.mongodb.org/manual

[Service]
User=mongodb
Group=mongodb
ExecStart=/usr/bin/mongod --quiet --config /etc/mongod.conf

[Install]
WantedBy=multi-user.target
EOL
  echo "[install_mongod] End of init script"
fi

# Tee and grep in order to check if the substitution was actually successful
cat /etc/mongod.conf | sed -r 's/(\s*bindIp:).*/\1 0.0.0.0/' | tee /etc/mongod.conf | grep "bindIp: 0.0.0.0"

echo "[install_mongod] Making sure mongod service is running"

if [ "$RELEASE" = "16.04" ]; then
  systemctl daemon-reload
  systemctl enable mongod
  systemctl start mongod
  systemctl is-active mongod > /dev/null
else
  service mongod start || true
  service mongod status | grep running > /dev/null
fi

if [ $? = 0 ]; then
  echo "[install_mongod] Success! MongoDB service is installed and running."
else
  echo "[install_mongod] Failed! Check mongod service logs for fix."
  exit 1
fi
