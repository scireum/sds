# sds-server

## What is it?

SDS is a tool to ease deployment of software components across low bandwidth connections. Performing differential updates, only files which really changed are transmitted. Using port 80 and HTTP enables updates through most firewalls etc. without exotic configuration.

## How do I use it?

SDS consists of two parts: A command line tool calles sds and the sds-server. To use it you need to obtain the appropriate binary for your platform (see left). Having a bash at hands, this can easily be done using **wget -O /usr/local/bin/sds <BINARY-URL>**. Having downloaded the binary, you need to make it executable by calling **chmod +x /usr/local/bin/sds**. You can call sds usage to get a complete list of all commands and environment variables which can be set. (Like *SDS_SERVER*, so that you don't need to specify the -server parameter for each call.)

Now you can start using sds to install your own sds-server (or other artifacts, like S3 Ninja). Calling **sds -server http://sds.scireum.net remote** you'll get a list of all public artifacts on our main distribution server. In a **new and empty** directory, you can call **sds -server http://sds.scireum.net pull sds-server**.

    Never call this in a directory filled with data, as it will delete everything which does not belong to the sds distribution!

Like any SIRIUS system you can now start the server by calling **./sirius.sh start** in the install directory (you might need to make it executable: **chmod +x sirius.sh**).

By default the system operates on port **9000**. You can tweak the configuration by creating a file called instance.conf. Here you can place custom settings like **http.port=80**. All available settings can be found in *app/application.conf*, *app/component-web.conf* and *app/component-kernel.conf*. 
