#!/usr/bin/env bash
set -euo pipefail

base_dir="/opt/casamento"
backup_dir="$base_dir/backups"
compose_file="$base_dir/docker-compose.prod.yml"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"

mkdir -p "$backup_dir"
set -a
source "$base_dir/.env"
set +a

backup_file="$backup_dir/casamento-${timestamp}.sql.gz"
docker compose -f "$compose_file" exec -T db pg_dump -U "$DB_USERNAME" -d "${DB_NAME:-casamento}" | gzip > "$backup_file"
test -s "$backup_file"

if [[ -n "${BACKUP_RCLONE_DEST:-}" ]]; then
	command -v rclone >/dev/null || { echo "rclone nao encontrado"; exit 69; }
	rclone copy "$backup_file" "$BACKUP_RCLONE_DEST"
fi

find "$backup_dir" -type f -name 'casamento-*.sql.gz' -mtime +7 -delete
echo "Backup criado: $backup_file"