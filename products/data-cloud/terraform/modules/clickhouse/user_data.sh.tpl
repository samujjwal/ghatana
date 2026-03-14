#!/bin/bash
set -euxo pipefail

# -------------------------------------------------------
# ClickHouse ${clickhouse_version} installation
# Runs once as EC2 user-data (Ubuntu 22.04 LTS)
# -------------------------------------------------------

export DEBIAN_FRONTEND=noninteractive

# Wait for apt lock to be released
for i in $(seq 1 20); do
  if ! fuser /var/lib/dpkg/lock-frontend >/dev/null 2>&1; then break; fi
  echo "Waiting for dpkg lock... ($i/20)"
  sleep 5
done

apt-get update -y
apt-get install -y curl gnupg2 lsb-release xfsprogs awscli

# ClickHouse APT repo
curl -fsSL 'https://packages.clickhouse.com/rpm/lts/repodata/repomd.xml.key' | \
  gpg --dearmor | tee /usr/share/keyrings/clickhouse-keyring.gpg > /dev/null

echo "deb [signed-by=/usr/share/keyrings/clickhouse-keyring.gpg] https://packages.clickhouse.com/deb stable main" \
  | tee /etc/apt/sources.list.d/clickhouse.list

apt-get update -y
CLICKHOUSE_VERSION="${clickhouse_version}"
apt-get install -y \
  clickhouse-server="$CLICKHOUSE_VERSION" \
  clickhouse-client="$CLICKHOUSE_VERSION" \
  clickhouse-common-static="$CLICKHOUSE_VERSION"

# -------------------------------------------------------
# Format and mount data volume
# -------------------------------------------------------
DATA_DEVICE="/dev/xvdf"
DATA_MOUNT="/var/lib/clickhouse"

while [ ! -b "$DATA_DEVICE" ]; do
  echo "Waiting for data volume $DATA_DEVICE..."
  sleep 3
done

if ! blkid "$DATA_DEVICE"; then
  mkfs.xfs -f "$DATA_DEVICE"
fi

mkdir -p "$DATA_MOUNT"
echo "$DATA_DEVICE $DATA_MOUNT xfs defaults,nofail 0 2" >> /etc/fstab
mount -a

chown clickhouse:clickhouse "$DATA_MOUNT"

# -------------------------------------------------------
# ClickHouse server config — listen on all interfaces
# -------------------------------------------------------
cat > /etc/clickhouse-server/config.d/00-listen.xml <<'XML'
<clickhouse>
  <listen_host>0.0.0.0</listen_host>
  <max_connections>4096</max_connections>
  <keep_alive_timeout>3</keep_alive_timeout>
  <max_concurrent_queries>100</max_concurrent_queries>
  <uncompressed_cache_size>8589934592</uncompressed_cache_size>
  <mark_cache_size>5368709120</mark_cache_size>
  <path>/var/lib/clickhouse/</path>
  <tmp_path>/var/lib/clickhouse/tmp/</tmp_path>
  <user_files_path>/var/lib/clickhouse/user_files/</user_files_path>
  <prometheus>
    <endpoint>/metrics</endpoint>
    <port>9363</port>
    <metrics>true</metrics>
    <events>true</events>
    <asynchronous_metrics>true</asynchronous_metrics>
  </prometheus>
</clickhouse>
XML

# -------------------------------------------------------
# ClickHouse users
# -------------------------------------------------------
cat > /etc/clickhouse-server/users.d/00-admin.xml <<XML
<clickhouse>
  <users>
    <${admin_user}>
      <password>${admin_password}</password>
      <networks>
        <ip>::/0</ip>
      </networks>
      <profile>default</profile>
      <quota>default</quota>
      <access_management>1</access_management>
    </${admin_user}>
    <default>
      <password></password>
      <networks>
        <ip>::1</ip>
        <ip>127.0.0.1</ip>
      </networks>
      <profile>default</profile>
      <quota>default</quota>
    </default>
  </users>
</clickhouse>
XML

# -------------------------------------------------------
# Enable and start
# -------------------------------------------------------
systemctl enable clickhouse-server
systemctl start clickhouse-server

# Wait for CH to be healthy
for i in $(seq 1 30); do
  if clickhouse-client --user="${admin_user}" --password="${admin_password}" -q "SELECT 1" 2>/dev/null; then
    echo "ClickHouse is up."
    break
  fi
  echo "Waiting for ClickHouse... ($i/30)"
  sleep 3
done

# -------------------------------------------------------
# Install clickhouse-backup for S3 backups
# -------------------------------------------------------
CH_BACKUP_VERSION="2.4.35"
curl -fsSL "https://github.com/Altinity/clickhouse-backup/releases/download/v$${CH_BACKUP_VERSION}/clickhouse-backup-linux-amd64.tar.gz" \
  | tar -xz -C /usr/local/bin/ --strip-components=2 ./build/linux/amd64/clickhouse-backup

cat > /etc/clickhouse-backup/config.yml <<YAML
general:
  remote_storage: s3
  max_file_size: 1073741824
clickhouse:
  username: ${admin_user}
  password: "${admin_password}"
  host: 127.0.0.1
  port: 9000
s3:
  bucket: ${backup_bucket}
  path: clickhouse/
  region: $(curl -s http://169.254.169.254/latest/dynamic/instance-identity/document | python3 -c "import sys,json; print(json.load(sys.stdin)['region'])")
  use_metadata_from_cloud_storage: true
YAML

# Daily backup cron
echo "0 3 * * * root /usr/local/bin/clickhouse-backup create-remote --tables='.*' daily-$(date +\%Y-\%m-\%d) >> /var/log/clickhouse-backup.log 2>&1" \
  > /etc/cron.d/clickhouse-backup

echo "ClickHouse setup complete."
