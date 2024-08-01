#!/bin/sh 
LD_LIBRARY_PATH=`pwd`/phono_opus java -cp target/whipi-1.2.jar pe.pi.whipi.WhepCommandLine $1 $2
