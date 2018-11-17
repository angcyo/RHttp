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

package com.androidzeitgeist.ani.discovery;

import android.content.Intent;
import android.util.Log;

import java.io.IOException;
import java.net.*;
import java.nio.charset.Charset;

/**
 * Internal class for handling the network connection of the {@link Discovery} class
 * on a background thread.
 */
class DiscoveryThread extends Thread {
    private static final String TAG = "ANI/DiscoveryThread";
    private static final int MAXIMUM_PACKET_BYTES = 102400;

    private String multicastAddress;
    private int port;
    private MulticastSocket socket;
    private DiscoveryListener listener;

    private volatile boolean running;

    /**
     * Create a new background thread that handles incoming Intents on the given
     * multicast address and port.
     * <p>
     * Do not instantiate this class yourself. Use the {@link Discovery} class
     * instead.
     *
     * @param multicastAddress
     * @param port
     * @param listener
     */
    /* package-private */ DiscoveryThread(String multicastAddress, int port, DiscoveryListener listener) {
        this.multicastAddress = multicastAddress;
        this.port = port;
        this.listener = listener;
    }

    public void run() {
        running = true;

        listener.onDiscoveryStarted();

        try {
            socket = createSocket();
            receiveIntents();
        } catch (IOException exception) {
            if (running) {
                listener.onDiscoveryError(exception);
            }
        } finally {
            closeSocket();
        }

        listener.onDiscoveryStopped();
    }

    protected MulticastSocket createSocket() throws UnknownHostException, IOException {
        InetAddress address = InetAddress.getByName(multicastAddress);

        MulticastSocket socket = new MulticastSocket(port);
        socket.joinGroup(address);

        return socket;
    }

    private void closeSocket() {
        if (socket != null) {
            socket.close();
        }
    }

    public void stopDiscovery() {
        running = false;

        closeSocket();
    }

    protected void receiveIntents() throws IOException {
        while (running) {
            DatagramPacket packet = new DatagramPacket(
                    new byte[MAXIMUM_PACKET_BYTES], MAXIMUM_PACKET_BYTES
            );

            try {
                socket.receive(packet);

                byte[] data = packet.getData();
                if (data != null) {
                    int length = packet.getLength();

                    //String intentUri2 = new String(data, 0, length, Charset.forName("gbk"));
                    String intentUri = new String(data, 0, length, Charset.forName("UTF-8"));
                    Intent intent = Intent.parseUri(intentUri, 0);

                    byte[] result = new byte[length];
                    System.arraycopy(data, 0, result, 0, length);
                    listener.onIntentDiscovered(packet.getAddress(), intent, result, length);
                }
            } catch (URISyntaxException exception) {
                Log.v(TAG, "Received UDP packet that could not be parsed as Intent");
            }
        }
    }
}
