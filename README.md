# sds-server

## What is it?

SDS is a tool to ease deployment of software components across low bandwidth connections. Performing differential updates, only files which really changed are transmitted. Using port 80 and HTTP enables updates through most firewalls etc. without exotic configuration.

## How do I use it?

SDS consists of three parts: A Java command line tool called SDS, the sds-server and a Maven Upload Mojo.

## The Server

Visit https://oss.sonatype.org/content/groups/public/com/scireum/sds-server/ to download the latest release zip. Once this
is unpacked, make **sirius.sh** executable and launch the application using **./sirius.sh start**. By default the
server runs on port 9000. Create a file called **instance.conf** containing *http.port=80* to change this.

Once the server is up and running, its web interface will provide further instructions on how to use it.

