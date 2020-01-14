package ru.nsu.g.a.lyamin.socksProxy.connection;

import ru.nsu.g.a.lyamin.socksProxy.ConnectionSelector;

import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

public class SecondPhaseConnection extends PhaseConnection
{
    //private boolean answerIsReady = false;
    private ByteBuffer buffer = ByteBuffer.wrap(new byte[263]);

    private byte[] answer = new byte[10];

    private byte[] addressBytes;

    private boolean isResolved = false;

    private int port;

    private byte error = 0x00;

    private InetAddress ip;

    private PendingConnection pendingConnection;

    private int answerWrittenAll = 0;

    public SecondPhaseConnection(ConnectionSelector connectionSelector, SocketChannel channel)
    {
        super(connectionSelector, channel);
        System.out.println("SECOND PHASE CTOR");
    }

    @Override
    public void perform(SelectionKey key) throws IOException
    {
        if(key.isValid() && key.isReadable())
        {
            int read;
            try
            {
                read = channel.read(buffer);
            }
            catch(IOException ignored)
            {
                terminate(key);
                return;
            }

            if(read == -1)
            {
                terminate(key);
                return;
            }

            if(!checkReaden()) return;

            byte[] secondPhaseRequest = new byte[buffer.position()];
            System.arraycopy(buffer.array(), 0, secondPhaseRequest, 0, secondPhaseRequest.length);
            System.out.println("SECOND PHASE REQUEST: " + Arrays.toString(secondPhaseRequest));

            if(buffer.get(0) != 0x05)
            {
                error = 0x07;
                createAnswer();
                return;
            }

            if(buffer.get(1) != 0x01)
            {
                error = 0x05;
                createAnswer();
                return;
            }

            if(buffer.get(2) != 0x00)
            {
                error = 0x07;
                createAnswer();
                return;
            }

            byte addressType = buffer.get(3);

            boolean isIp;
            if(addressType == 0x01)
            {
                isIp = true;
                addressBytes = new byte[4];
                System.arraycopy(buffer.array(), 4, addressBytes, 0, addressBytes.length);
                ip = parseIpFromBytes(addressBytes);

                if(ip == null)
                {
                    error = 0x07;
                    createAnswer();
                    return;
                }
            }
            else if(addressType == 0x03)
            {
                int length = buffer.get(4);
                isIp = false;
                addressBytes = new byte[length];
                System.arraycopy(buffer.array(), 5, addressBytes, 0, length);
            }
            else if(addressType == 0x04)
            {
                error = 0x08;
                createAnswer();
                return;
            }
            else
            {
                error = 0x07;
                createAnswer();
                return;
            }

            byte[] portBytes = new byte[2];
            System.arraycopy(buffer.array(), buffer.position() - 2, portBytes, 0, 2);

            port = bytesToPort(portBytes);

            if(isIp)
            {
                SocketChannel socketChannel = SocketChannel.open();
                socketChannel.configureBlocking(false);
                createPending(socketChannel, new InetSocketAddress(ip, port));

            }
            else
            {
                DNSConnection dnsConnection = DNSConnection.getInstance();
                dnsConnection.resolveAddress(addressBytes, this);
            }

//            connectionSelector.registerConnection(channel, this, 0);
            key.cancel();
        }

        if(key.isValid() && key.isWritable())
        {
            System.out.println("SECOND PHASE IS WRITABLE");
            int written = channel.write(ByteBuffer.wrap(answer));

            answerWrittenAll += written;

            if(answerWrittenAll != answer.length)
            {
                return;
            }

            System.out.println("SECOND PHASE ANSWER: " + Arrays.toString(answer));

            if(hasError())
            {
                terminate(key);
                return;
            }

            ConnectionBuffer firstBuffer = new ConnectionBuffer();
            ConnectionBuffer secondBuffer = new ConnectionBuffer();

            DirectConnection directConnection1 = new DirectConnection(connectionSelector, channel, firstBuffer,
                                                                                                     secondBuffer);
            DirectConnection directConnection2 = new DirectConnection(connectionSelector,
                                                        pendingConnection.getChannel(), secondBuffer, firstBuffer);

            connectionSelector.registerConnection(channel, directConnection1,
                                                                SelectionKey.OP_READ | SelectionKey.OP_WRITE);
            connectionSelector.registerConnection(pendingConnection.getChannel(), directConnection2,
                                                                SelectionKey.OP_READ | SelectionKey.OP_WRITE);

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

    private boolean checkReaden()
    {
        if(buffer.position() < 6) return false;

        byte addressType = buffer.get(3);

        if(addressType == 0x01)
        {
            return buffer.position() >= 4 + 4 + 2;
        }
        else if(addressType == 0x03)
        {
            int length = buffer.get(4);

            return buffer.position() >= 4 + length + 2;
        }

        return true;
    }

    public void setIsConnected(boolean flag, PendingConnection _pendingConnection) throws ClosedChannelException
    {
        if(!flag)
        {
            error = 0x04;
        }

        pendingConnection = _pendingConnection;

        createAnswer();
    }

    public void createPending(SocketChannel sc, InetSocketAddress isa) throws ClosedChannelException
    {
        try
        {
            new PendingConnection(connectionSelector, sc, this, isa);
        }
        catch(IOException ignored)
        {
            error = 0x03;
            createAnswer();
        }
    }

    public void setIpResolved(boolean ipResolved, InetAddress resolvedIp) throws IOException
    {

        if(isResolved) return;

        if(!ipResolved)
        {
            error = 0x04;
            createAnswer();
            return;
        }

        isResolved = true;

        System.out.println("RESOLVED: " + resolvedIp.toString());

        SocketChannel socketChannel = SocketChannel.open();
        socketChannel.configureBlocking(false);

        createPending(socketChannel, new InetSocketAddress(resolvedIp, port));
//        PendingConnection pendingConnection = new PendingConnection(connectionSelector, socketChannel, this, new InetSocketAddress(resolvedIp, port));
    }

    private boolean hasError()
    {
        return error != (byte) 0x00;
    }

    private void createAnswer() throws ClosedChannelException
    {
        System.out.println("SECOND PHASE CREATE ANSWER");
        connectionSelector.registerConnection(channel, this, SelectionKey.OP_WRITE);

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
            return;
        }

        answer[3] = (byte) 0x01;

        InetAddress localIP = remoteAddress.getAddress();
        int localPort = remoteAddress.getPort();

        byte[] ipBytes = inetAddressToBytes(localIP);

        System.arraycopy(ipBytes, 0, answer, 4, 4);

        byte[] portBytes = portToBytes(localPort);

        System.arraycopy(portBytes, 0, answer, 8, 2);

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

        ByteBuffer.wrap(tmp).order(ByteOrder.BIG_ENDIAN).putInt(port);

        byte[] result = new byte[2];

        System.arraycopy(tmp, 2, result, 0, 2);

        return result;
    }

    private int bytesToPort(byte[] portBytes)
    {
        byte[] newPortBytes = new byte[4];
        System.arraycopy(portBytes, 0, newPortBytes, 2, portBytes.length);

        System.out.println(Arrays.toString(newPortBytes));
        return ByteBuffer.wrap(newPortBytes).order(ByteOrder.BIG_ENDIAN).getInt();
    }

    public byte[] getAddressBytes()
    {
        return addressBytes;
    }

    public int getPort()
    {
        return port;
    }

    @Override
    public void terminate(SelectionKey key)
    {
        super.terminate(key);
        try
        {
            channel.close();
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }
    }
}



//            if(buffer.position() >= 5)
//            {
//                if(buffer.get(0) != 0x05)
//                {
//                    error = 0x07;
//                    createAnswer();
//                }
//
//                else if(buffer.get(1) != 0x01 || buffer.get(2) != 0x00)
//                {
//                    error = 0x07;
//                    createAnswer();
//                }
//
//                else
//                {
//                    byte addressType = buffer.get(3);
//
//                    if(addressType == 0x04)
//                    {
//                        error = 0x08;
//                        createAnswer();
//                    }
//
//                    else if (addressType == 0x01)
//                    {
//                        if(buffer.position() > 10)
//                        {
//                            error = 0x07;
//                            createAnswer();
//                        }
//                        else if( buffer.position() == 10)
//                        {
//                            isIp = true;
//                            addressBytes = new byte[4];
//                            System.arraycopy(buffer.array(), 4, addressBytes, 0, addressBytes.length);
//                            ip = parseIpFromBytes(addressBytes);
//                        }
//                    }
//                    else if(addressType == 0x03)
//                    {
//                        int length = buffer.get(4);
//                        if(buffer.position() > 4 + 1 + length + 2)
//                        {
//                            error = 0x07;
//                            createAnswer();
//                        }
//                        else if(buffer.position() == 4 + 1 + length + 2)
//                        {
//                            isIp = false;
//                            addressBytes = new byte[length];
//                            System.arraycopy(buffer.array(), 5, addressBytes, 0, length);
//                            DNSConnection dnsConnection = DNSConnection.getInstance();
//                            dnsConnection.resolveAddress(addressBytes, this);
//                        }
//                    }
//                    else
//                    {
//                        error = 0x07;
//                        createAnswer();
//                    }
//
//                }
//
//                byte[] portBytes = new byte[2];
//                System.arraycopy(buffer.array(), buffer.position() - 2, portBytes, 0, 2);
//
//                port = bytesToPort(portBytes);
//
//                if(isIp)
//                {
//                    SocketChannel socketChannel = SocketChannel.open();
//                    socketChannel.configureBlocking(false);
//                    PendingConnection pendingConnection = new PendingConnection(connectionSelector, socketChannel, this, new InetSocketAddress(ip, port));
//                }
//                else
//                {
//                    DNSConnection dnsConnection = DNSConnection.getInstance();
//                    dnsConnection.resolveAddress(addressBytes, this);
//                }
//            }