#!/bin/bash

rm src/main/resources/executor-1.1-SNAPSHOT.jar

mvn org.apache.maven.plugins:maven-dependency-plugin:get -Dtransitive=false -DrepoUrl=https://oss.sonatype.org/content/repositories/snapshots -Dartifact=io.cresco:executor:1.1-SNAPSHOT

cp ~/.m2/repository/io/cresco/executor/1.1-SNAPSHOT/executor-1.1-SNAPSHOT.jar src/main/resources/
