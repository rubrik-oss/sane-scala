#!/usr/bin/env bash

grep 'version :=' build.sbt | cut -d \" -f2