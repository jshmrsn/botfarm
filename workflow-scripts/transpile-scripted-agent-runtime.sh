#!/bin/sh
npx tsc --target es6 "$(dirname "${BASH_SOURCE[0]}")/../simulation-server/src/main/resources/scripted-agent-runtime.ts"