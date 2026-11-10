#!/bin/sh
set -eu

cd "$(dirname "$0")"
goal=${1:-package}
if [ "$goal" != package ] && [ "$goal" != test ]; then
    printf '%s\n' 'Expected package or test' >&2
    exit 2
fi

artifact=${TINYPX_PROTOCOLLIB_JAR:-/opt/tinypx/ProtocolLib-5.0.0.jar}
temporary=
if [ ! -f "$artifact" ]; then
    temporary=$(mktemp "${TMPDIR:-/tmp}/tinypx-protocollib.XXXXXXXX.jar")
    trap 'rm -f "$temporary"' EXIT HUP INT TERM
    curl --fail --location --silent --show-error --retry 2 --connect-timeout 15 --max-time 180 \
        --output "$temporary" https://github.com/dmulloy2/ProtocolLib/releases/download/5.0.0/ProtocolLib.jar
    artifact=$temporary
fi
if ! printf '%s  %s\n' '41d7d8e99e21eebd343c04c07e6b5afff79cad28240afe1dbad0cce52402d2a6' "$artifact" | sha256sum --check --status; then
    printf '%s\n' 'ProtocolLib 5.0.0 checksum mismatch' >&2
    exit 1
fi
mvn -B -N org.apache.maven.plugins:maven-install-plugin:3.1.4:install-file \
    "-Dfile=$artifact" -DgroupId=com.comphenix.protocol -DartifactId=ProtocolLib \
    -Dversion=5.0.0 -Dpackaging=jar -DgeneratePom=true
mvn -B -Pmc18 -Dmaven.javadoc.skip=true -DBUILD_NUMBER=MC1.8.8-packet "$goal"
