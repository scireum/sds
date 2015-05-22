# sds-server

If you have questions or are just curious, please feel welcome to join the chat room:
[![Join the chat at https://gitter.im/scireum/sirius-kernel](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/scireum/OpenSource?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

## What is it?

SDS is a tool to ease deployment of software components across low bandwidth connections. Performing differential updates, only files which really changed are transmitted. Using port 80 and HTTP enables updates through most firewalls etc. without exotic configuration.

## How do I use it?

Visit https://oss.sonatype.org/content/groups/public/com/scireum/sds-server/ to download the latest release zip. Once this
is unpacked, make **sirius.sh** executable and launch the application using **./sirius.sh start**. By default the
server runs on port 9000. Create a file called **instance.conf** containing *http.port=80* to change this.

Once the server is up and running, its web interface will provide further instructions on how to use it.

