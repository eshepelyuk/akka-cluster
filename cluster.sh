#!/bin/bash

docker run --network casino_dev2 --rm bwits/docker-httpie GET 10.0.0.3:8778/jolokia/read/akka:type=Cluster/ClusterStatus | jq '.value | fromjson'
