#!/usr/bin/env bash

cd "$(dirname "$0")"

docker build --tag=kubee-artifactory .
