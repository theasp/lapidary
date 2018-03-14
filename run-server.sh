#!/bin/bash

export DEBUG="*"
export DEBUG_LEVEL=verbose
export TIMBRE_LEVEL=':debug'               # Elide all lower logging calls

export DEVELOPMENT=${DEVELOPMENT:-true}
export HTTP_PORT=${HTTP_PORT:-${PORT:-3011}}
export HTTP_ADDRESS=${HTTP_ADDRESS:-"0.0.0.0"}
export DB_NAME=${DB_NAME:-lapidary}
export DB_USER=${DB_USER:-lapidary}
export DB_PASSWORD=${DB_PASSWORD:-lapidary}
export DB_PORT=${DB_PORT:-5432}
export DB_HOST=${DB_HOST:-127.0.0.1}
export WEB_PROTO=${WEB_PROTO:-https}
export WEB_FQDN=${WEB_FQDN:-lapidary}
export WEB_ROOT=${WEB_ROOT:-""}
export COOKIE_SECRET=${COOKIE_SECRET:-$(head -c 8 /dev/urandom | hexdump -ve '1/1 "%.2x"')}

set -ex

# cat > postgrator.js <<EOF
# var postgrator = require('postgrator');

# postgrator.setConfig({
#                       migrationDirectory: "resources/postgrator",
#                       schemaTable: 'postgrator',
#                       driver: "pg",
#                       host: "${DB_HOST}",
#                       port: ${DB_PORT},
#                       database: "${DB_NAME}",
#                       username: "${DB_USER}",
#                       password: "${DB_PASSWORD}"
#                     });

# // migrate to version specified, or supply 'max' to go all the way up
# postgrator.migrate('max', function (err, migrations) {
#                      postgrator.endConnection(function () {
#                                                 // connection is closed, or will close in the case of SQL Server
#                                               });
#                      if (err) {
#                           console.log(err);
#                           process.exit(1);
#                         } else {
#                           console.log(migrations)
#                         }

#                    });
# EOF

# node postgrator.js

exec node target/private/js/server.js
