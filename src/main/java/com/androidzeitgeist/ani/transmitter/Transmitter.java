/*
 * Copyright (C) 2013 Sebastian Kaspari
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.androidzeitgeist.ani.transmitter;

import android.content.Intent;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.androidzeitgeist.ani.discovery.Discovery;
import com.androidzeitgeist.ani.internal.AndroidNetworkIntents;

import java.io.IOException;
import java.net.*;

/**
 * Transmitter class for sending {@link Intent}s through network.
 */
public class Transmitter {
    private String multicastAddress;
    private int port;

    /**
     * Creates a new {@link Transmitter} instance that will sent {@link Intent}s to
     * the default multicast address and port.
     */
    public Transmitter() {
        this(
                AndroidNetworkIntents.DEFAULT_MULTICAST_ADDRESS,
                AndroidNetworkIntents.DEFAULT_PORT
        );
    }

    /**
     * Creates a new {@link Transmitter} instance that will sent {@link Intent}s to
     * the default multicast address and the given port port.
     *
     * @param port The destination network port.
     */
    public Transmitter(int port) {
        this(
                AndroidNetworkIntents.DEFAULT_MULTICAST_ADDRESS,
                port
        );
    }

    /**
     * Creates a new {@link Transmitter} instance that will sent {@link Intent}s to
     * the given multicast address and port.
     *
     * @param multicastAddress The destination multicast address, e.g. 225.4.5.6.
     * @param port             The destination network port.
     */
    public Transmitter(String multicastAddress, int port) {
        this.multicastAddress = multicastAddress;
        this.port = port;
    }

    /**
     * Sends an {@link Intent} through the network to any listening {@link Discovery}
     * instance.
     *
     * @param intent The intent to send.
     * @throws TransmitterException if intent could not be transmitted.
     */
    public void transmit(@NonNull Intent intent) throws TransmitterException {
        MulticastSocket socket = null;

        try {
            socket = createSocket();
            transmit(socket, intent, null);
        } catch (UnknownHostException exception) {
            throw new TransmitterException("Unknown host", exception);
        } catch (SocketException exception) {
            throw new TransmitterException("Can't create DatagramSocket", exception);
        } catch (IOException exception) {
            throw new TransmitterException("IOException during sending intent", exception);
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    public void transmit(@NonNull byte[] data) throws TransmitterException {
        MulticastSocket socket = null;

        try {
            socket = createSocket();
            transmit(socket, null, data);
        } catch (UnknownHostException exception) {
            throw new TransmitterException("Unknown host", exception);
        } catch (SocketException exception) {
            throw new TransmitterException("Can't create DatagramSocket", exception);
        } catch (IOException exception) {
            throw new TransmitterException("IOException during sending intent", exception);
        } finally {
            if (socket != null) {
                socket.close();
            }
        }
    }

    protected MulticastSocket createSocket() throws IOException {
        return new MulticastSocket();
    }

    /**
     * Actual (private) implementation that serializes the {@link Intent} and sends
     * it as {@link DatagramPacket}. Used to separate the implementation from the
     * error handling code.
     *
     * @param data 如果data不为空, 优先使用.会忽略 intent 参数的值
     */
    private void transmit(@NonNull MulticastSocket socket, @Nullable Intent intent, @Nullable byte[] data) throws IOException {
        if (data == null) {
            if (intent != null) {
                data = intent.toUri(0).getBytes();
            }
        }

        if (data != null) {
            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    InetAddress.getByName(multicastAddress),
                    port
            );

            socket.send(packet);
        }
    }
}
