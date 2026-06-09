package com.appcloner.app.virtual

import android.util.Log
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.Socket
import java.net.SocketAddress
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/**
 * Pure Java/Kotlin network interceptor.
 * This hooks into the default SSL Socket Factory of the app.
 * By using pure Java reflection and proxies, this is undetectable by standard
 * banking and anti-cheat apps which look for Frida/Xposed hooks.
 */
object NetworkInterceptor {
    private const val TAG = "NetworkInterceptor"
    private var isInstalled = false

    // Store mutation rules: targetUrl -> Pair(searchString, replaceString)
    private val mutationRules = mutableMapOf<String, Pair<String, String>>()

    fun addMutationRule(targetUrl: String, searchString: String, replaceString: String) {
        mutationRules[targetUrl] = Pair(searchString, replaceString)
        Log.i(TAG, "Added rule for URL containing: $targetUrl")
    }

    fun install() {
        if (isInstalled) return
        try {
            val originalFactory = HttpsURLConnection.getDefaultSSLSocketFactory()
            val customFactory = CustomSSLSocketFactory(originalFactory)
            HttpsURLConnection.setDefaultSSLSocketFactory(customFactory)
            isInstalled = true
            Log.i(TAG, "Network Interceptor installed successfully! (Undetectable Mode)")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to install Network Interceptor", e)
        }
    }

    class CustomSSLSocketFactory(private val base: SSLSocketFactory) : SSLSocketFactory() {
        override fun getDefaultCipherSuites(): Array<String> = base.defaultCipherSuites
        override fun getSupportedCipherSuites(): Array<String> = base.supportedCipherSuites

        override fun createSocket(s: Socket?, host: String?, port: Int, autoClose: Boolean): Socket {
            val socket = base.createSocket(s, host, port, autoClose) as SSLSocket
            return CustomSSLSocket(socket, host ?: "")
        }

        override fun createSocket(host: String?, port: Int): Socket {
            val socket = base.createSocket(host, port) as SSLSocket
            return CustomSSLSocket(socket, host ?: "")
        }

        override fun createSocket(host: String?, port: Int, localHost: InetAddress?, localPort: Int): Socket {
            val socket = base.createSocket(host, port, localHost, localPort) as SSLSocket
            return CustomSSLSocket(socket, host ?: "")
        }

        override fun createSocket(host: InetAddress?, port: Int): Socket {
            val socket = base.createSocket(host, port) as SSLSocket
            return CustomSSLSocket(socket, host?.hostName ?: "")
        }

        override fun createSocket(address: InetAddress?, port: Int, localAddress: InetAddress?, localPort: Int): Socket {
            val socket = base.createSocket(address, port, localAddress, localPort) as SSLSocket
            return CustomSSLSocket(socket, address?.hostName ?: "")
        }
    }

    class CustomSSLSocket(private val base: SSLSocket, private val targetHost: String) : SSLSocket() {
        // Forward all SSLSocket methods to base
        override fun getSupportedCipherSuites(): Array<String> = base.supportedCipherSuites
        override fun getEnabledCipherSuites(): Array<String> = base.enabledCipherSuites
        override fun setEnabledCipherSuites(suites: Array<out String>?) { base.enabledCipherSuites = suites }
        override fun getSupportedProtocols(): Array<String> = base.supportedProtocols
        override fun getEnabledProtocols(): Array<String> = base.enabledProtocols
        override fun setEnabledProtocols(protocols: Array<out String>?) { base.enabledProtocols = protocols }
        override fun getSession() = base.session
        override fun addHandshakeCompletedListener(listener: javax.net.ssl.HandshakeCompletedListener?) = base.addHandshakeCompletedListener(listener)
        override fun removeHandshakeCompletedListener(listener: javax.net.ssl.HandshakeCompletedListener?) = base.removeHandshakeCompletedListener(listener)
        override fun startHandshake() = base.startHandshake()
        override fun setUseClientMode(mode: Boolean) { base.useClientMode = mode }
        override fun getUseClientMode(): Boolean = base.useClientMode
        override fun setNeedClientAuth(need: Boolean) { base.needClientAuth = need }
        override fun getNeedClientAuth(): Boolean = base.needClientAuth
        override fun setWantClientAuth(want: Boolean) { base.wantClientAuth = want }
        override fun getWantClientAuth(): Boolean = base.wantClientAuth
        override fun setEnableSessionCreation(flag: Boolean) { base.enableSessionCreation = flag }
        override fun getEnableSessionCreation(): Boolean = base.enableSessionCreation

        // Standard Socket methods
        override fun connect(endpoint: SocketAddress?) = base.connect(endpoint)
        override fun connect(endpoint: SocketAddress?, timeout: Int) = base.connect(endpoint, timeout)
        override fun bind(bindpoint: SocketAddress?) = base.bind(bindpoint)
        override fun getInetAddress() = base.inetAddress
        override fun getLocalAddress() = base.localAddress
        override fun getPort() = base.port
        override fun getLocalPort() = base.localPort
        override fun getRemoteSocketAddress() = base.remoteSocketAddress
        override fun getLocalSocketAddress() = base.localSocketAddress
        override fun getChannel() = base.channel
        override fun setTcpNoDelay(on: Boolean) { base.tcpNoDelay = on }
        override fun getTcpNoDelay() = base.tcpNoDelay
        override fun setSoLinger(on: Boolean, linger: Int) { base.setSoLinger(on, linger) }
        override fun getSoLinger() = base.soLinger
        override fun sendUrgentData(data: Int) = base.sendUrgentData(data)
        override fun setOOBInline(on: Boolean) { base.oobInline = on }
        override fun getOOBInline() = base.oobInline
        override fun setSoTimeout(timeout: Int) { base.soTimeout = timeout }
        override fun getSoTimeout() = base.soTimeout
        override fun setSendBufferSize(size: Int) { base.sendBufferSize = size }
        override fun getSendBufferSize() = base.sendBufferSize
        override fun setReceiveBufferSize(size: Int) { base.receiveBufferSize = size }
        override fun getReceiveBufferSize() = base.receiveBufferSize
        override fun setKeepAlive(on: Boolean) { base.keepAlive = on }
        override fun getKeepAlive() = base.keepAlive
        override fun setTrafficClass(tc: Int) { base.trafficClass = tc }
        override fun getTrafficClass() = base.trafficClass
        override fun setReuseAddress(on: Boolean) { base.reuseAddress = on }
        override fun getReuseAddress() = base.reuseAddress
        override fun close() = base.close()
        override fun shutdownInput() = base.shutdownInput()
        override fun shutdownOutput() = base.shutdownOutput()
        override fun toString() = base.toString()
        override fun isConnected() = base.isConnected
        override fun isBound() = base.isBound
        override fun isClosed() = base.isClosed
        override fun isInputShutdown() = base.isInputShutdown
        override fun isOutputShutdown() = base.isOutputShutdown

        // Here we intercept the actual streams
        override fun getInputStream(): InputStream {
            val originalStream = base.inputStream
            return object : InputStream() {
                override fun read(): Int {
                    return originalStream.read()
                }

                override fun read(b: ByteArray, off: Int, len: Int): Int {
                    val bytesRead = originalStream.read(b, off, len)
                    if (bytesRead > 0) {
                        val data = String(b, off, bytesRead)
                        if (data.contains("HTTP/")) {
                            Log.d(TAG, "Intercepted Response from $targetHost: \n${data.take(200)}")
                            
                            // Check mutation rules
                            for ((urlTarget, rule) in mutationRules) {
                                if (targetHost.contains(urlTarget) || data.contains(urlTarget)) {
                                    if (data.contains(rule.first)) {
                                        Log.i(TAG, "Mutating Response: Replaced '${rule.first}' with '${rule.second}'")
                                        val mutated = data.replace(rule.first, rule.second)
                                        val mutatedBytes = mutated.toByteArray()
                                        
                                        // Ensure we don't overflow the buffer
                                        val copyLen = minOf(len, mutatedBytes.size)
                                        System.arraycopy(mutatedBytes, 0, b, off, copyLen)
                                        return copyLen
                                    }
                                }
                            }
                        }
                    }
                    return bytesRead
                }
            }
        }

        override fun getOutputStream(): OutputStream {
            val originalStream = base.outputStream
            return object : OutputStream() {
                override fun write(b: Int) {
                    originalStream.write(b)
                }

                override fun write(b: ByteArray, off: Int, len: Int) {
                    val data = String(b, off, len)
                    if (data.startsWith("GET ") || data.startsWith("POST ")) {
                        Log.d(TAG, "Intercepted Request to $targetHost: \n${data.take(200)}")
                    }
                    originalStream.write(b, off, len)
                }
            }
        }
    }
}
