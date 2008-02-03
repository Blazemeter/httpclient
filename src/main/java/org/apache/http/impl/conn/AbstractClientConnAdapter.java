/*
 * $HeadURL$
 * $Revision$
 * $Date$
 *
 * ====================================================================
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */

package org.apache.http.impl.conn;


import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.InetAddress;
import java.net.Socket;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSession;

import org.apache.http.HttpException;
import org.apache.http.HttpRequest;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpConnectionMetrics;
import org.apache.http.conn.OperatedClientConnection;
import org.apache.http.conn.ManagedClientConnection;
import org.apache.http.conn.ClientConnectionManager;


/**
 * Abstract adapter from {@link OperatedClientConnection operated} to
 * {@link ManagedClientConnection managed} client connections.
 * Read and write methods are delegated to the wrapped connection.
 * Operations affecting the connection state have to be implemented
 * by derived classes. Operations for querying the connection state
 * are delegated to the wrapped connection if there is one, or
 * return a default value if there is none.
 * <br/>
 * This adapter tracks the checkpoints for reusable communication states,
 * as indicated by {@link #markReusable markReusable} and queried by
 * {@link #isMarkedReusable isMarkedReusable}.
 * All send and receive operations will automatically clear the mark.
 * <br/>
 * Connection release calls are delegated to the connection manager,
 * if there is one. {@link #abortConnection abortConnection} will
 * clear the reusability mark first. The connection manager is
 * expected to tolerate multiple calls to the release method.
 *
 * @author <a href="mailto:rolandw at apache.org">Roland Weber</a>
 *
 *
 * <!-- empty lines to avoid svn diff problems -->
 * @version   $Revision$ $Date$
 *
 * @since 4.0
 */
public abstract class AbstractClientConnAdapter
    implements ManagedClientConnection {

    /**
     * The connection manager, if any.
     * This attribute MUST NOT be final, so the adapter can be detached
     * from the connection manager without keeping a hard reference there.
     */
    private volatile ClientConnectionManager connManager;

    /** The wrapped connection. */
    private volatile OperatedClientConnection wrappedConnection;

    /** The reusability marker. */
    private volatile boolean markedReusable;

    private volatile boolean aborted;

    /**
     * Creates a new connection adapter.
     * The adapter is initially <i>not</i>
     * {@link #isMarkedReusable marked} as reusable.
     *
     * @param mgr       the connection manager, or <code>null</code>
     * @param conn      the connection to wrap, or <code>null</code>
     */
    protected AbstractClientConnAdapter(ClientConnectionManager mgr,
                                        OperatedClientConnection conn) {

        connManager = mgr;
        wrappedConnection = conn;
        markedReusable = false;
        aborted = false;

    } // <constructor>


    /**
     * Detaches this adapter from the wrapped connection.
     * This adapter becomes useless.
     */
    protected void detach() {
        wrappedConnection = null;
        connManager = null; // base class attribute
    }

    protected OperatedClientConnection getWrappedConnection() {
        return wrappedConnection;
    }
    
    protected ClientConnectionManager getManager() {
        return connManager;
    }
    
    /**
     * Asserts that the connection has not been aborted.
     *
     * @throws InterruptedIOException   if the connection has been aborted
     */
    protected final void assertNotAborted() throws InterruptedIOException {
        if (aborted) {
            throw new InterruptedIOException("Connection has been shut down.");
        }
    }

    /**
     * Asserts that there is a wrapped connection to delegate to.
     *
     * @throws IllegalStateException    if there is no wrapped connection
     *                                  or connection has been aborted
     */
    protected final void assertValid(
            final OperatedClientConnection wrappedConn) {
        if (wrappedConn == null) {
            throw new IllegalStateException("No wrapped connection.");
        }
    }

    // non-javadoc, see interface HttpConnection
    public boolean isOpen() {
        OperatedClientConnection conn = getWrappedConnection();
        if (conn == null)
            return false;

        return conn.isOpen();
    }


    // non-javadoc, see interface HttpConnection
    public boolean isStale() {
        OperatedClientConnection conn = getWrappedConnection();
        if (conn == null)
            return true;

        return conn.isStale();
    }


    // non-javadoc, see interface HttpConnection
    public void setSocketTimeout(int timeout) {
        OperatedClientConnection conn = getWrappedConnection();
        assertValid(conn);
        conn.setSocketTimeout(timeout);
    }


    // non-javadoc, see interface HttpConnection
    public int getSocketTimeout() {
        OperatedClientConnection conn = getWrappedConnection();
        assertValid(conn);
        return conn.getSocketTimeout();
    }


    // non-javadoc, see interface HttpConnection
    public HttpConnectionMetrics getMetrics() {
        OperatedClientConnection conn = getWrappedConnection();
        assertValid(conn);
        return conn.getMetrics();
    }


    // non-javadoc, see interface HttpClientConnection
    public void flush()
        throws IOException {

        assertNotAborted();
        OperatedClientConnection conn = getWrappedConnection();
        assertValid(conn);

        conn.flush();
    }


    // non-javadoc, see interface HttpClientConnection
    public boolean isResponseAvailable(int timeout)
        throws IOException {

        assertNotAborted();
        OperatedClientConnection conn = getWrappedConnection();
        assertValid(conn);

        return conn.isResponseAvailable(timeout);
    }


    // non-javadoc, see interface HttpClientConnection
    public void receiveResponseEntity(HttpResponse response)
        throws HttpException, IOException {

        assertNotAborted();
        OperatedClientConnection conn = getWrappedConnection();
        assertValid(conn);

        unmarkReusable();
        conn.receiveResponseEntity(response);
    }


    // non-javadoc, see interface HttpClientConnection
    public HttpResponse receiveResponseHeader()
        throws HttpException, IOException {

        assertNotAborted();
        OperatedClientConnection conn = getWrappedConnection();
        assertValid(conn);

        unmarkReusable();
        return conn.receiveResponseHeader();
    }


    // non-javadoc, see interface HttpClientConnection
    public void sendRequestEntity(HttpEntityEnclosingRequest request)
        throws HttpException, IOException {

        assertNotAborted();
        OperatedClientConnection conn = getWrappedConnection();
        assertValid(conn);

        unmarkReusable();
        conn.sendRequestEntity(request);
    }


    // non-javadoc, see interface HttpClientConnection
    public void sendRequestHeader(HttpRequest request)
        throws HttpException, IOException {

        assertNotAborted();
        OperatedClientConnection conn = getWrappedConnection();
        assertValid(conn);
        
        unmarkReusable();
        conn.sendRequestHeader(request);
    }


    // non-javadoc, see interface HttpInetConnection
    public InetAddress getLocalAddress() {
        OperatedClientConnection conn = getWrappedConnection();
        assertValid(conn);
        return conn.getLocalAddress();
    }

    // non-javadoc, see interface HttpInetConnection
    public int getLocalPort() {
        OperatedClientConnection conn = getWrappedConnection();
        assertValid(conn);
        return conn.getLocalPort();
    }


    // non-javadoc, see interface HttpInetConnection
    public InetAddress getRemoteAddress() {
        OperatedClientConnection conn = getWrappedConnection();
        assertValid(conn);
        return conn.getRemoteAddress();
    }

    // non-javadoc, see interface HttpInetConnection
    public int getRemotePort() {
        OperatedClientConnection conn = getWrappedConnection();
        assertValid(conn);
        return conn.getRemotePort();
    }

    // non-javadoc, see interface ManagedClientConnection
    public boolean isSecure() {
        OperatedClientConnection conn = getWrappedConnection();
        assertValid(conn);
        return conn.isSecure();
    }

    // non-javadoc, see interface ManagedClientConnection
    public SSLSession getSSLSession() {
        OperatedClientConnection conn = getWrappedConnection();
        assertValid(conn);
        if (!isOpen())
            return null;

        SSLSession result = null;
        Socket    sock    = conn.getSocket();
        if (sock instanceof SSLSocket) {
            result = ((SSLSocket)sock).getSession();
        }
        return result;
    }

    // non-javadoc, see interface ManagedClientConnection
    public void markReusable() {
        markedReusable = true;
    }

    // non-javadoc, see interface ManagedClientConnection
    public void unmarkReusable() {
        markedReusable = false;
    }

    // non-javadoc, see interface ManagedClientConnection
    public boolean isMarkedReusable() {
        return markedReusable;
    }

    // non-javadoc, see interface ConnectionReleaseTrigger
    public void releaseConnection() {
        if (connManager != null)
            connManager.releaseConnection(this);
    }

    // non-javadoc, see interface ConnectionReleaseTrigger
    public void abortConnection() {
        if (aborted) {
            return;
        }
        aborted = true;
        unmarkReusable();

        OperatedClientConnection conn = getWrappedConnection();
        
        if (conn != null) {
            try {
                conn.shutdown();
            } catch (IOException ignore) {
            }
        }
    }

} // class AbstractClientConnAdapter
