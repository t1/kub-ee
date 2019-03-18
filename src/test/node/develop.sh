#!/usr/bin/env bash

cat ../java/com/github/t1/kubee/boundary/html/deployment-list-full.html \
    | sed 's|bootstrap/|http://maxcdn.bootstrapcdn.com/bootstrap/3.2.0/|' \
    | sed 's|jquery/|https://ajax.googleapis.com/ajax/libs/jquery/1.12.4/|' \
    | sed 's|babel-standalone/|https://unpkg.com/babel-standalone@6/|' \
    | sed 's|react/react.js|https://unpkg.com/react@15/dist/react.js|' \
    | sed 's|react/react-dom.js|https://unpkg.com/react-dom@15/dist/react-dom.js|' \
    > deployments.html

open http://0.0.0.0:3000/deployments.html

light-server --serve . --port 3000 --watchexp "*"
