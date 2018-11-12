package com.angcyo.http;

import android.support.annotation.NonNull;
import com.androidzeitgeist.ani.discovery.Discovery;
import com.androidzeitgeist.ani.discovery.DiscoveryException;
import com.androidzeitgeist.ani.discovery.DiscoveryListener;
import com.androidzeitgeist.ani.transmitter.Transmitter;
import com.androidzeitgeist.ani.transmitter.TransmitterException;
import org.jetbrains.annotations.NotNull;

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
            byte[] bytes = packet.getData();
            int length = packet.getLength();
            result = new byte[length];
            System.arraycopy(bytes, 0, result, 0, length);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (socket != null) {
                socket.close();
            }
        }

        return result;
    }

    /**
     * @param fill 填充到数组中的数据
     * @param size 需要生成的数组大小
     */
    public static byte[] bindData(@NonNull byte[] fill, int size) {
        size = Math.max(fill.length, size);
        byte[] data = new byte[size];
        for (int i = 0; i < size; i++) {
            data[i] = 0x00;
        }
        System.arraycopy(fill, 0, data, 0, fill.length);
        return data;
    }

    public static String hexString(@NotNull byte[] data) {
        return BytesHexStrTranslate.bytesToHexFun2(data);
    }

    /**
     * byte 十进制转换成 十六进制字符串
     */
    public static String hexString(@NotNull byte[] data, int start, int end) {
        return BytesHexStrTranslate.bytesToHexFun2(data, start, end);
    }

    /**
     * 十六进制字符串, 每个2个 添加一个空格. 美观输出
     */
    public static String formatHex(@NotNull String hex) {
        return formatHex(hex, ' ');
    }

    public static String formatHex(@NotNull String hex, char split) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i <= hex.length(); i += 2) {
            if (i > 0 && i % 2 == 0) {
                builder.append(hex.substring(i - 2, i));

                //最后面不需要添加分隔符
                if (i != hex.length()) {
                    builder.append(split);
                }
            }
        }
        return builder.toString();
    }

    public static String formatHexToInt(@NotNull String hex, char split) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i <= hex.length(); i += 2) {
            if (i > 0 && i % 2 == 0) {
                builder.append(Integer.parseInt(hex.substring(i - 2, i), 16));

                //最后面不需要添加分隔符
                if (i != hex.length()) {
                    builder.append(split);
                }
            }
        }
        return builder.toString();
    }

    /**
     * 每2位十六进制 换成 十进制数据返回
     */
    public static int[] hexStringToIntArray(@NotNull String hex) {
        hex = fixHexString(hex);

        int size = hex.length() / 2;

        int[] result = new int[size];

        int index = 0;
        for (int i = 0; i <= hex.length(); i += 2) {
            if (i > 0 && i % 2 == 0) {
                String substring = hex.substring(i - 2, i);
                result[index++] = Integer.parseInt(substring, 16);
            }
        }
        return result;
    }

    public static String[] hexStringToStringArray(@NotNull String hex) {
        hex = fixHexString(hex);

        int size = hex.length() / 2;

        String[] result = new String[size];

        int index = 0;
        for (int i = 0; i <= hex.length(); i += 2) {
            if (i > 0 && i % 2 == 0) {
                String substring = hex.substring(i - 2, i);
                result[index++] = substring;
            }
        }
        return result;
    }

    private static String fixHexString(@NotNull String hex) {
        hex = hex.replaceAll(" ", "");
        if (hex.length() % 2 != 0) {
            //补齐
            hex = "0" + hex;
        }
        return hex;
    }

    /**
     * 小字节数据.
     */
    public static String reverse(@NotNull String[] hexArray) {
        StringBuilder builder = new StringBuilder();
        for (int i = hexArray.length - 1; i >= 0; i--) {
            builder.append(hexArray[i]);
        }
        return builder.toString();
    }
}
