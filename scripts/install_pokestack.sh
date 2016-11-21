#!/usr/bin/env bash

set -eu

if (( EUID != 0 )); then
    echo "[install_pokestack] You must run this script as root." 1>&2
    exit 1
fi

vergte () {
    if [[ $1 == $2 ]]
    then
        return 0
    fi
    local IFS=.
    local i ver1=($1) ver2=($2)
    # fill empty fields in ver1 with zeros
    for ((i=${#ver1[@]}; i<${#ver2[@]}; i++))
    do
        ver1[i]=0
    done
    for ((i=0; i<${#ver1[@]}; i++))
    do
        if [[ -z ${ver2[i]} ]]
        then
            # fill empty fields in ver2 with zeros
            ver2[i]=0
        fi
        if ((10#${ver1[i]} > 10#${ver2[i]}))
        then
            return 0
        fi
        if ((10#${ver1[i]} < 10#${ver2[i]}))
        then
            return 1
        fi
    done
    return 0
}

is_nonroot_user() {
    if [ -z $1 ]; then
        return 1
    fi

    local id
    id=$(id -u "$1" 2>/dev/null)
    if [[ $? -ne 0 || -z $id ]]; then
        return 1 # command failed
    elif [ -z $id ]; then
        return 1 # user not found
    elif [ $id -eq 0 ]; then
        return 1 # user is root
    else
        return 0
    fi
}

U_USER=$(who am i | cut -d ' ' -f 1)
: ${U_USER:=ubuntu}

if ! is_nonroot_user "$U_USER"; then
  echo "Unable to obtain unprivileged user for created files."
  exit 1
fi

NODE_VERSION="$(node --version | cut -c 2-)"

if [ $? = 0 ] && vergte "$NODE_VERSION" "6.0"; then
  echo "[install_pokestack] NodeJS >=6.0 (v$NODE_VERSION) already installed, skipping"
else
  echo "[install_pokestack] Installing NodeJS >=6.x and native build tools"
  curl -sL https://deb.nodesource.com/setup_6.x | bash -
  apt-get install -y nodejs build-essential
fi

echo "[install_pokestack] Downloading latest pokestack release"
LATEST_URL=$(curl -s https://api.github.com/repos/v1ct04/benchstack/releases | grep browser_download_url | cut -d '"' -f 4 | grep pokestack | grep .tar.gz)

echo "[install_pokestack] URL: $LATEST_URL"
FILENAME="${LATEST_URL##*/}"
DIRNAME="${FILENAME%.tar.gz}"

if [[ $? != 0 || -z "$LATEST_URL" || -z "$DIRNAME" ]]; then
  echo "[install_pokestack] Failed to get release URL or filename."
  exit 1
fi

if [ -d "$DIRNAME" ]; then
  echo "[install_pokestack] Latest release already downloaded. Skipping."
else
  curl -SL "$LATEST_URL" | sudo -u "$U_USER" tar -xz
fi
cd "$DIRNAME"

echo "[install_pokestack] Installing npm dependencies as user $U_USER"
sudo -u "$U_USER" npm install

# Set up system service

echo "[install_pokestack] Installing pm2 and enabling auto-start behavior"
npm install -g pm2
pm2 startup

echo "[install_pokestack] Restarting pokestack service in pm2"
pm2 delete pokestack > /dev/null || true
if [ $# -gt 0 ]; then
  pm2 start bin/www --name="pokestack" -- "$@"
else
  pm2 start bin/www --name="pokestack"
fi
pm2 save

echo "[install_pokestack] Waiting for service initialization"
sleep 5

if pm2 status | grep pokestack | grep online > /dev/null; then
  echo "[install_pokestack] Success! Pokestack service is up and running"
else
  echo "[install_pokestack] Failed! Check pm2 logs for fixing."
  exit 1
fi
