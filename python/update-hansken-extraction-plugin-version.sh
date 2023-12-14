#!/usr/bin/env bash

version=${1:?version}

SCRIPT_DIR=$( cd -- "$( dirname -- "${BASH_SOURCE[0]}" )" &> /dev/null && pwd )

mapfile -d $'\0' reqs < <(find . -name requirements.in -print0)
for i in "${!reqs[@]}"; do
  req="${reqs[$i]}"
  plugin=$(dirname "${req}")
  echo "[$((i + 1))/${#reqs[@]}] updating plugin $(basename "${plugin}")..."
  sed -i "s/hansken-extraction-plugin==.*/hansken-extraction-plugin==${version}/" "${req}"
  (cd "${plugin}"; pip-compile --no-header --quiet)
done
echo "DONE"