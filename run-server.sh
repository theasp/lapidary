#!/bin/bash

export DEBUG="*"
export DEBUG_LEVEL=verbose
export TIMBRE_LEVEL=':trace'               # Elide all lower logging calls
export DEVELOPMENT=true

export AUTH__SECRET=${AUTH__SECRET:-$(head -c 8 /dev/urandom | hexdump -ve '1/1 "%.2x"')}
export JWT__SECRET=${JWT__SECRET:-$(head -c 8 /dev/urandom | hexdump -ve '1/1 "%.2x"')}

exec node target/private/js/server.js
