#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 1 ]]; then
    echo "Uso: $0 /opt/casamento/releases/casamento-backend-<versao>.tar.gz"
    exit 64
fi

archive="$1"
base_dir="/opt/casamento"
compose_file="$base_dir/docker-compose.prod.yml"

[[ -f "$archive" ]] || { echo "Arquivo nao encontrado: $archive"; exit 66; }
[[ -f "$compose_file" ]] || { echo "Compose nao encontrado: $compose_file"; exit 66; }
[[ -f "$base_dir/.env" ]] || { echo "Arquivo .env nao encontrado: $base_dir/.env"; exit 66; }

if [[ -f "$archive.sha256" ]]; then
    (cd "$(dirname "$archive")" && sha256sum -c "$(basename "$archive").sha256")
fi

previous_image="$(docker compose --env-file "$base_dir/.env" -f "$compose_file" images -q app || true)"
docker load < <(gzip -dc "$archive")

cd "$base_dir"
docker compose --env-file .env -f "$compose_file" up -d --no-deps app

for attempt in {1..30}; do
    if curl --fail --silent --show-error http://127.0.0.1:8080/q/health/live >/dev/null; then
        echo "Backend saudavel."
        exit 0
    fi
    printf 'Aguardando health check (%s/30)...\n' "$attempt"
    sleep 2
done

echo "O novo backend nao ficou saudavel. Logs:"
docker compose --env-file .env -f "$compose_file" logs --tail=100 app
echo "Imagem anterior: ${previous_image:-indisponivel}"
exit 1