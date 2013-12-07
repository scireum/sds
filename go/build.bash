source /opt/go/src/golang-crosscompile/crosscompile.bash

cd ../go
go-linux-386 build sds.go
rm ../resources/assets/binaries/*
mv sds ../resources/assets/binaries/sds-linux-386

cd ../build