export PATH=/opt/go/bin:$PATH
source /opt/go/src/golang-crosscompile/crosscompile.bash

cd ../go

go-linux-386 build sds.go
rm ../resources/assets/binaries/*
mv sds ../resources/assets/binaries/sds-linux-386

go-linux-amd64 build sds.go
rm ../resources/assets/binaries/*
mv sds ../resources/assets/binaries/sds-linux-adm64

go-windows-386 build sds.go
rm ../resources/assets/binaries/*
mv sds ../resources/assets/binaries/sds-windows-386

go-windows-amd64 build sds.go
rm ../resources/assets/binaries/*
mv sds ../resources/assets/binaries/sds-windows-adm64

go-darwin-386 build sds.go
rm ../resources/assets/binaries/*
mv sds ../resources/assets/binaries/sds-darwin-386

go-darwin-amd64 build sds.go
rm ../resources/assets/binaries/*
mv sds ../resources/assets/binaries/sds-darwin-adm64

go-freebsd-386 build sds.go
rm ../resources/assets/binaries/*
mv sds ../resources/assets/binaries/sds-freebsd-386

go-freebsd-amd64 build sds.go
rm ../resources/assets/binaries/*
mv sds ../resources/assets/binaries/sds-freebsd-adm64

cd ../build