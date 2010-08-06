/*
 * ========================================================================
 *
 * Codehaus CARGO, copyright 2004-2010 Vincent Massol.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * ========================================================================
 */
package org.codehaus.cargo.container.jboss.internal;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;

import org.codehaus.cargo.util.CargoException;
import org.codehaus.cargo.util.FileHandler;
import org.codehaus.cargo.util.log.Logger;

/**
 * Implementation of a Web server that serves one file.
 *
 * @version $Id$
 */
public class SimpleHttpFileServer implements Runnable, ISimpleHttpFileServer
{

    /**
     * Logger instance.
     */
    protected Logger logger;

    /**
     * URL for retrieving file.
     */
    protected URL url;

    /**
     * CARGO file handler.
     */
    protected FileHandler fileHandler;

    /**
     * Path of the file to serve.
     */
    protected String filePath;

    /**
     * Remote path of served file.
     */
    protected String remotePath;

    /**
     * TCP socket.
     */
    protected ServerSocket socket;

    /**
     * Call count.
     */
    protected int callCount;

    /**
     * Has stop been called?
     */
    protected boolean stopped;

    /**
     * create the simple http file server.
     */
    public SimpleHttpFileServer()
    {
        callCount = 0;
    }

    /**
     * @param logger logger to use.
     */
    public void setLogger(Logger logger)
    {
        this.logger = logger;
    }

    /**
     * @param handler file handler to use.
     * @param filePath path of the file in the handler.
     */
    public void setFile(FileHandler handler, String filePath)
    {
        if (!handler.exists(filePath) || handler.isDirectory(filePath))
        {
            throw new CargoException("File " + filePath + " does not exist or is not a file");
        }
        String baseDir = handler.getParent(filePath);
        String fileName = handler.getName(filePath);

        this.fileHandler = handler;
        this.remotePath = "/" + fileName;
    }

    /**
     * @param listenSocket socket to listen on.
     * @param remoteDeployAddress remote hostname to use in the url, if null it will be obtained
     *        from the listenSocket.
     */
    public void setListeningParameters(InetSocketAddress listenSocket, String remoteDeployAddress)
    {
        if (this.remotePath == null)
        {
            throw new CargoException("Please call setFile first!");
        }

        try
        {
            String finalRemoteDeployAddress = remoteDeployAddress;
            if (finalRemoteDeployAddress == null)
            {
                finalRemoteDeployAddress = listenSocket.getHostName();
            }
            this.url = new URL("http", finalRemoteDeployAddress, listenSocket.getPort(),
                this.remotePath);
        }
        catch (MalformedURLException e)
        {
            throw new CargoException("Could not create a url for " + listenSocket + " and file: "
                + this.remotePath, e);
        }

        try
        {
            this.socket = new ServerSocket(listenSocket.getPort(), 0, listenSocket.getAddress());
        }
        catch (IOException e)
        {
            throw new CargoException("Could not create a socket for " + listenSocket, e);
        }
    }

    /**
     *@return url this server serves.
     */
    public URL getURL()
    {
        if (this.url == null)
        {
            throw new CargoException("Please call setListeningParameters first!");
        }

        return url;
    }

    /**
     * @return the number of successful calls received.
     */
    public int getCallCount()
    {
        return this.callCount;
    }

    /**
     * starts the server.
     */
    public void start()
    {
        if (this.logger == null)
        {
            throw new CargoException("Please call setLogger first!");
        }

        if (this.socket == null)
        {
            throw new CargoException("Please call setListeningParameters first!");
        }

        this.stopped = false;
        Thread thread = new Thread(this);
        thread.setDaemon(true);
        thread.start();
    }

    /**
     * stops the server.
     */
    public void stop()
    {
        this.stopped = true;

        try
        {
            this.socket.close();
        }
        catch (IOException e)
        {
            throw new CargoException("Error stopping embedded HTTP server", e);
        }
    }

    /**
     * runs the thread.
     */
    public void run()
    {
        try
        {
            this.runAndThrow();
        }
        catch (Throwable t)
        {
            if (!this.stopped)
            {
                this.logger.warn("Error in the embedded HTTP server: " + t.toString(),
                    this.getClass().getName());
            }
        }
    }

    /**
     * runs the thread.
     * @throws Throwable if anything is thrown.
     */
    private void runAndThrow() throws Throwable
    {
        final String expectedGetRequest = "GET " + this.remotePath;

        while (true)
        {
            Socket socket = null;

            try
            {
                // wait for a connection
                socket = this.socket.accept();

                boolean error = false;
                BufferedReader in = new BufferedReader(new InputStreamReader(
                    socket.getInputStream()));

                String line = in.readLine();
                if (line == null)
                {
                    line = "";
                }
                if (!line.startsWith(expectedGetRequest))
                {
                    error = true;
                }
                while (!"".equals(line))
                {
                    line = in.readLine();
                }
                in.close();
                in = null;

                BufferedOutputStream out = new BufferedOutputStream(socket.getOutputStream());
                if (error)
                {
                    StringBuilder answer = new StringBuilder();
                    answer.append("HTTP/1.0 404 NOTFOUND");
                    answer.append("\r\n");
                    answer.append("Connection: close");
                    answer.append("\r\n");
                    answer.append("\r\n");

                    out.write(answer.toString().getBytes("US-ASCII"));
                }
                else
                {
                    StringBuilder answer = new StringBuilder();
                    answer.append("HTTP/1.0 200 OK");
                    answer.append("\r\n");
                    answer.append("Connection: close");
                    answer.append("\r\n");
                    answer.append("Content-Type: application/octet-stream");
                    answer.append("\r\n");
                    answer.append("\r\n");

                    out.write(answer.toString().getBytes("US-ASCII"));

                    InputStream file = this.fileHandler.getInputStream(this.filePath);
                    try
                    {
                        int read;
                        while ((read = file.read()) != -1)
                        {
                            out.write(read);
                        }
                    }
                    finally
                    {
                        file.close();
                        file = null;
                    }

                    this.callCount++;
                }

                out.flush();
                out.close();
                out = null;
            }
            finally
            {
                if (socket != null)
                {
                    socket.close();
                    socket = null;
                }
            }
        }
    }
}