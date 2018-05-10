#!/bin/bash

export DEBUG="*"
export DEBUG_LEVEL=verbose
export TIMBRE_LEVEL=':trace'               # Elide all lower logging calls
export DEVELOPMENT=true

export AUTH__METHOD=${AUTH__METHOD:-user}
export AUTH__ADMIN_USERNAME=${AUTH__ADMIN_USERNAME:-admin}
export AUTH__ADMIN_PASSWORD=${AUTH__ADMIN_PASSWORD:-ChangeMe!}
export AUTH__SECRET=${AUTH__SECRET:-$(head -c 8 /dev/urandom | hexdump -ve '1/1 "%.2x"')}

export HTTP__PORT=${HTTP__PORT:-${PORT:-3011}}
export HTTP__ADDRESS=${HTTP__ADDRESS:-"0.0.0.0"}

export DB__DATABASE=${DB__DATABASE:-lapidary}
export DB__USERNAME=${DB__USERNAME:-lapidary}
export DB__PASSWORD=${DB__PASSWORD:-lapidary}
export DB__PORT=${DB__PORT:-5433}
export DB__HOSTNAME=${DB__HOSTNAME:-127.0.0.1}
export DB__POOL_SIZE=11

export LDAP__URL="ldaps://ipa.gt0.ca"
export LDAP__BASE_DN="dc=gt0,dc=ca"

export JWT__SECRET=${JWT__SECRET:-$(head -c 8 /dev/urandom | hexdump -ve '1/1 "%.2x"')}

exec node target/private/js/server.js
