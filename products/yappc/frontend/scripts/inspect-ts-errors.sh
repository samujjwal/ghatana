#!/usr/bin/env bash
set -euo pipefail
mkdir -p tmp

TS_PROJECT=libs/page-builder/tsconfig.json
OUT_RAW=tmp/ts-full-output.txt
OUT_BY_FILE=tmp/ts-errors-by-file.txt
OUT_UNIQUE=tmp/ts-unique-errors.txt

echo "Running tsc for ${TS_PROJECT} (output -> ${OUT_RAW})..."
# Run tsc and capture output (non-zero exit OK)
npx -y typescript --project "${TS_PROJECT}" --noEmit --pretty false 2> "${OUT_RAW}" || true

if [ ! -s "${OUT_RAW}" ]; then
  echo "No TypeScript output was produced or tsc is not installed. Check project setup."
  exit 0
fi

# Extract lines with "error TS" and normalize; produce counts per file
grep -E "error TS" "${OUT_RAW}" || true | sed -E 's/^(.*):([0-9]+):([0-9]+) - error TS([0-9]+): (.*)$/\1|TS\4|\5/' \
  | awk -F'|' '{print $1}' | sort | uniq -c | sort -rn > "${OUT_BY_FILE}"

echo "Top error files (saved to ${OUT_BY_FILE}):"
head -n 50 "${OUT_BY_FILE}" || true

# Top unique error messages
grep -E "error TS" "${OUT_RAW}" || true | sed -E 's/.*error TS[0-9]+: //' | sort | uniq -c | sort -rn | head -n 50 > "${OUT_UNIQUE}"

echo "Top unique error messages (saved to ${OUT_UNIQUE}):"
cat "${OUT_UNIQUE}"

echo "Raw tsc output saved to ${OUT_RAW}"
echo "Per-file counts saved to ${OUT_BY_FILE}"

