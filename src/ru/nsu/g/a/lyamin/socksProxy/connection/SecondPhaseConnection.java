package ru.nsu.g.a.lyamin.socksProxy.connection;

import ru.nsu.g.a.lyamin.socksProxy.ConnectionSelector;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

public class SecondPhaseConnection extends PhaseConnection
{
    private boolean answerIsReady = false;
    private ByteBuffer buffer = ByteBuffer.wrap(new byte[263]);

    private byte[] answer = new byte[10];

    private byte[] addressBytes;

    private boolean ipResolved;
    private boolean isIp;

    private int port;

    private byte error = 0x00;

    private InetAddress ip;

    public SecondPhaseConnection(ConnectionSelector connectionSelector, SocketChannel channel)
    {
        super(connectionSelector, channel);
    }

    @Override
    public void perform(SelectionKey key) throws IOException
    {
        if(key.isValid() && key.isReadable())
        {
            /*int readed = */channel.read(buffer);
            if(buffer.position() >= 5)
            {
                if(buffer.get(0) != 0x05)
                {
                    error = 0x07;
                    createAnswer();
                }

                else if(buffer.get(1) != 0x01 || buffer.get(2) != 0x00)
                {
                    error = 0x07;
                    createAnswer();
                }

                else
                {
                    byte addressType = buffer.get(3);

                    if(addressType == 0x04)
                    {
                        error = 0x08;
                        createAnswer();
                    }

                    else if (addressType == 0x01)
                    {
                        if(buffer.position() > 10)
                        {
                            error = 0x07;
                            createAnswer();
                        }
                        else if( buffer.position() == 10)
                        {
                            isIp = true;
                            addressBytes = new byte[4];
                            System.arraycopy(buffer.array(), 4, addressBytes, 0, addressBytes.length);
                            ip = parseIpFromBytes(addressBytes);
                        }
                    }
                    else if(addressType == 0x03)
                    {
                        int length = buffer.get(4);
                        if(buffer.position() + 1 > 4 + 1 + length + 2)
                        {
                            error = 0x07;
                            createAnswer();
                            //terminate(key);
                        }
                        else if(buffer.position() + 1 == 4 + 1 + length + 2)
                        {
                            isIp = false;
                            addressBytes = new byte[length];
                            System.arraycopy(buffer.array(), 5, addressBytes, 0, length);
                            DNSConnection dnsConnection = DNSConnection.getInstance();
                            dnsConnection.resolveAddress(addressBytes, this);
                        }
                    }
                    else
                    {
                        error = 0x07;
                        createAnswer();
                    }

                }

                byte[] portBytes = new byte[2];
                System.arraycopy(buffer.array(), buffer.position() - 2, portBytes, 0, 2);

                port = bytesToPort(portBytes);

                if(isIp)
                {
                    SocketChannel socketChannel = SocketChannel.open();
                    PendingConnection pendingConnection = new PendingConnection(connectionSelector, socketChannel, this, new InetSocketAddress(ip, port));
                }
                else
                {
                    DNSConnection dnsConnection = DNSConnection.getInstance();
                    dnsConnection.resolveAddress(addressBytes, this);
                }
            }
        }

        if(key.isValid() && key.isWritable())
        {

            channel.write(ByteBuffer.wrap(answer));

            if(hasError()) {terminate(key);}

            SocketChannel connectionChannel = SocketChannel.open(new InetSocketAddress(ip, port));

//            if(connectionChannel.isConnected())
//            {
//                System.out.println("CHI DA");
//            }

            connectionChannel.configureBlocking(false);

            //System.out.println(ip.getHostName() + ":" + port);
            //connectionChannel.connect(new InetSocketAddress(ip, port));


            //System.out.println("IS CONNECTED: " + connectionChannel.isConnected());

            //connectionChannel.bind(new InetSocketAddress(ip, port));

            ConnectionBuffer firstBuffer = new ConnectionBuffer();
            ConnectionBuffer secondBuffer = new ConnectionBuffer();

            DirectConnection directConnection1 = new DirectConnection(connectionSelector, channel, firstBuffer, secondBuffer);
            DirectConnection directConnection2 = new DirectConnection(connectionSelector, connectionChannel, secondBuffer, firstBuffer);

            connectionSelector.registerConnection(channel, directConnection1, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            connectionSelector.registerConnection(connectionChannel, directConnection2, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

//
//            SocketChannel channel2 = SocketChannel.open();
//            channel2.configureBlocking(false);
//
//            channel2.bind(new InetSocketAddress(ip, port));
//
//            DirectConnection directConnection2 = new DirectConnection(connectionSelector, channel2);

            //SecondPhaseConnection secondPhaseConnection = new SecondPhaseConnection(connectionSelector, channel);

            //connectionSelector.addConnection(channel, secondPhaseConnection);
        }
    }

    public void setIsConnected(boolean flag) throws ClosedChannelException
    {
        if(!flag)
        {
            error = 0x04;
        }

        createAnswer();
    }

    private boolean hasError()
    {
        return error != (byte) 0x00;
    }

    private void createAnswer() throws ClosedChannelException
    {

        connectionSelector.registerConnection(channel, this, SelectionKey.OP_WRITE);

        answerIsReady = true;
        InetSocketAddress remoteAddress = null;
        try
        {
            remoteAddress = (InetSocketAddress) channel.getRemoteAddress();
        }
        catch (IOException ignored)
        {
            error = 0x01;
        }

        answer[0] = 0x05;
        answer[1] = error;
        answer[2] = (byte) 0x00;
        if(hasError())
        {
            answerIsReady = true;
            return;
        }

        answer[3] = (byte) 0x01;

        InetAddress localIP = remoteAddress.getAddress();
        int localPort = remoteAddress.getPort();

        byte[] ipBytes = inetAddressToBytes(localIP);



        answer[4] = ipBytes[0];
        answer[5] = ipBytes[1];
        answer[6] = ipBytes[2];
        answer[7] = ipBytes[3];

        System.out.println(localPort);

        byte[] portBytes = portToBytes(localPort);

        System.out.println(Arrays.toString(portBytes));

        int getNewLocalProPort = bytesToPort(portBytes);

        System.out.println(getNewLocalProPort);

        answer[8] = portBytes[0];
        answer[9] = portBytes[1];

        System.out.println(Arrays.toString(answer));

    }

    private InetAddress parseIpFromBytes(byte[] ipBytes)
    {
        InetAddress ipResult = null;
        try
        {
            ipResult = InetAddress.getByAddress(ipBytes);
        }
        catch (UnknownHostException e)
        {
            e.printStackTrace();
        }

        return ipResult;
    }

    private byte[] inetAddressToBytes(InetAddress ip)
    {
        return ip.getAddress();
    }

    private byte[] portToBytes(int port)
    {
        byte[] tmp = new byte[4];

        ByteBuffer.wrap(tmp).order(ByteOrder.BIG_ENDIAN).putInt(port); //.array();

        byte[] result = new byte[2];

        System.arraycopy(tmp, 2, result, 0, 2);
//        result[1] = (byte)(port % 256);
//        result[0] = (byte)(port / 256);
//
//        //ByteBuffer.wrap(result).putInt(port);
//
        return result;
    }

    private int bytesToPort(byte[] portBytes)
    {
        byte[] newPortBytes = new byte[4];
        System.arraycopy(portBytes, 0, newPortBytes, 2, portBytes.length);

        System.out.println(Arrays.toString(newPortBytes));
        return ByteBuffer.wrap(newPortBytes).order(ByteOrder.BIG_ENDIAN).getInt();
//        return (portBytes[0] + portBytes[1] * 256);
    }

    public void setIp(InetAddress _ip)
    {
        ip = _ip;
    }

    public void setIpResolved(boolean _ipResolved) throws IOException
    {

        ipResolved = _ipResolved;

        if(!ipResolved)
        {
            error = 0x04;
            createAnswer();
            return;
        }

        SocketChannel socketChannel = SocketChannel.open();
        PendingConnection pendingConnection = new PendingConnection(connectionSelector, socketChannel, this, new InetSocketAddress(ip, port));
    }

    public byte[] getAddressBytes()
    {
        return addressBytes;
    }

    public int getPort()
    {
        return port;
    }
}
