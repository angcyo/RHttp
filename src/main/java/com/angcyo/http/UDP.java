package com.angcyo.http;

import android.support.annotation.NonNull;
import com.androidzeitgeist.ani.discovery.Discovery;
import com.androidzeitgeist.ani.discovery.DiscoveryException;
import com.androidzeitgeist.ani.discovery.DiscoveryListener;
import com.androidzeitgeist.ani.transmitter.Transmitter;
import com.androidzeitgeist.ani.transmitter.TransmitterException;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;

/**
 * Email:angcyo@126.com
 *
 * @author angcyo
 * @date 2018/11/12
 */
public class UDP {

    /**
     * 循环接收UDP的数据
     */
    public static Discovery receive(int port, @NonNull DiscoveryListener listener) throws DiscoveryException {
        Discovery discovery = new Discovery("255.255.255.255", port);
        discovery.enable(listener);
        return discovery;
    }

    /**
     * 使用UDP发送数据
     */
    public static void send(String address, int port, @NonNull byte[] data) throws TransmitterException {
        new Transmitter(address, port).transmit(data);
    }

    /**
     * 请在子线程中执行
     */
    public static byte[] sendAndReceive(String address, int port, @NonNull byte[] data) {
        MulticastSocket socket = null;
        byte[] result = null;

        try {
            socket = new MulticastSocket();

            //发送数据
            DatagramPacket packet = new DatagramPacket(
                    data,
                    data.length,
                    InetAddress.getByName(address),
                    port
            );
            socket.send(packet);

            Thread.sleep(200);

            //接收数据
            packet = new DatagramPacket(new byte[102400], 102400);
            socket.receive(packet);
            result = packet.getData();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (socket != null) {
                socket.close();
            }
        }

        return result;
    }
}
