#!/bin/sh 
LD_LIBRARY_PATH=`pwd`/src/main/resources/phono_opus java -cp target/whipi-1.2.jar pe.pi.whipi.WhepCommandLine $1 $2
