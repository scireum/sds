#
# Made with all the love in the world
# by scireum in Remshalden, Germany
#
# Copyright by scireum GmbH
# http://www.scireum.de - info@scireum.de
#
# Generates the sds binaries for all supported platforms.
#
# Expects a go installation in /opt/go
# Also expects the tools of: https://github.com/davecheney/golang-crosscompile in the src directory of go

# Add to go path
export PATH=/opt/go/bin:$PATH

# Add crosscompiler macros
source /opt/go/src/golang-crosscompile/crosscompile.bash

# Clean up directories
cd ../go
rm ../resources/assets/binaries/*

# Build binaries
go-linux-386 build sds.go
mv sds ../resources/assets/binaries/sds-linux

go-windows-386 build sds.go
mv sds.exe ../resources/assets/binaries/sds-windows.exe

go-darwin-386 build sds.go
mv sds ../resources/assets/binaries/sds-osx

go-freebsd-386 build sds.go
mv sds ../resources/assets/binaries/sds-freebsd

# Jump back into the build directory
cd ../build