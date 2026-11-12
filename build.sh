#!/bin/sh
set -eu

cd "$(dirname "$0")"
goal=${1:-package}
if [ "$goal" != package ] && [ "$goal" != test ]; then
    printf '%s\n' 'Expected package or test' >&2
    exit 2
fi

mvn -B -Dmaven.javadoc.skip=true -DBUILD_NUMBER=MC1.8.8-packet "$goal"
