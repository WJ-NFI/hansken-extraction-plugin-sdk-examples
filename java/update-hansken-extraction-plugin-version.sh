#!/usr/bin/env bash

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

mapfile -d $'\0' poms < <(find . -name pom.xml -print0)
for i in "${!poms[@]}"; do
  pom="${poms[$i]}"
  plugin=$(dirname "${pom}")
  echo "[$((i + 1))/${#poms[@]}] updating plugin $(basename "${plugin}")..."
  mvn --file "${pom}" --quiet versions:update-parent
done
echo "DONE"