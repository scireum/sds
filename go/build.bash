export PATH=/opt/go/bin:$PATH
source /opt/go/src/golang-crosscompile/crosscompile.bash

cd ../go
rm ../resources/assets/binaries/*

go-linux-386 build sds.go
mv sds ../resources/assets/binaries/sds-linux-386

go-linux-amd64 build sds.go
mv sds.exe ../resources/assets/binaries/sds-linux-amd64.exe

go-windows-386 build sds.go
mv sds.exe ../resources/assets/binaries/sds-windows-386.exe

go-windows-amd64 build sds.go
mv sds ../resources/assets/binaries/sds-windows-amd64

go-darwin-386 build sds.go
mv sds ../resources/assets/binaries/sds-darwin-386

go-darwin-amd64 build sds.go
mv sds ../resources/assets/binaries/sds-darwin-amd64

go-freebsd-386 build sds.go
mv sds ../resources/assets/binaries/sds-freebsd-386

go-freebsd-amd64 build sds.go
mv sds ../resources/assets/binaries/sds-freebsd-amd64

cd ../build