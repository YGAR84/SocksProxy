package ru.nsu.g.a.lyamin.socksProxy.connection;

import ru.nsu.g.a.lyamin.socksProxy.ConnectionSelector;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;

public class ServerConnection extends Connection
{

    public ServerConnection(ConnectionSelector connectionSelector, int port) throws IOException
    {
        super(connectionSelector);

        ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
        serverSocketChannel.configureBlocking(false);
        serverSocketChannel.bind(new InetSocketAddress(port));

        System.out.println("WRITABLE: " + SelectionKey.OP_WRITE);
        System.out.println("READABLE: " + SelectionKey.OP_READ);
        System.out.println("ACCEPTABLE: " + SelectionKey.OP_ACCEPT);
        System.out.println("CONNECTIBLE: " + SelectionKey.OP_CONNECT);

        connectionSelector.registerConnection(serverSocketChannel, this, SelectionKey.OP_ACCEPT);
    }

    @Override
    public void perform(SelectionKey key) throws IOException
    {
        if(key.isValid() && key.isAcceptable())
        {
            ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
            SocketChannel socketChannel = serverSocketChannel.accept();


            //System.out.println(socketChannel == null);

            if(socketChannel != null)
            {
                socketChannel.configureBlocking(false);

                connectionSelector.registerConnection(socketChannel,
                                    new FirstPhaseConnection(connectionSelector, socketChannel), SelectionKey.OP_READ);
            }
        }
    }
}
