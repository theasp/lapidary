#!/bin/bash

export DEBUG="*"
export DEBUG_LEVEL=verbose
export TIMBRE_LEVEL=':debug'               # Elide all lower logging calls

export ADMIN_USERNAME=${ADMIN_USERNAME:-admin}
export ADMIN_PASSWORD=${ADMIN_PASSWORD:-ChangeMe!}

export DEVELOPMENT=${DEVELOPMENT:-true}

export HTTP_PORT=${HTTP_PORT:-${PORT:-3011}}
export HTTP_ADDRESS=${HTTP_ADDRESS:-"0.0.0.0"}

export DB_NAME=${DB_NAME:-lapidary}
export DB_USER=${DB_USER:-lapidary}
export DB_PASSWORD=${DB_PASSWORD:-lapidary}
export DB_PORT=${DB_PORT:-5432}
export DB_HOST=${DB_HOST:-127.0.0.1}
export DB_URI=${DB_URI:-postgresql://${DB_USER}:${DB_PASSWORD}@${DB_HOST}:${DB_PORT}/${DB_NAME}}

export WEB_PROTO=${WEB_PROTO:-http}
export WEB_FQDN=${WEB_FQDN:-lapidary}
export WEB_ROOT=${WEB_ROOT:-""}

export COOKIE_SECRET=${COOKIE_SECRET:-$(head -c 8 /dev/urandom | hexdump -ve '1/1 "%.2x"')}
export JWT_SECRET=${JWT_SECRET:-$(head -c 8 /dev/urandom | hexdump -ve '1/1 "%.2x"')}

exec node target/private/js/server.js
