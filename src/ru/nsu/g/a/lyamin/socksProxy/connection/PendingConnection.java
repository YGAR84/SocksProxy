package ru.nsu.g.a.lyamin.socksProxy.connection;

import ru.nsu.g.a.lyamin.socksProxy.ConnectionSelector;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;

public class PendingConnection extends Connection
{

    private SocketChannel channel;
    private SecondPhaseConnection secondPhaseConnection;

    public PendingConnection(ConnectionSelector _connectionSelector, SocketChannel _channel, SecondPhaseConnection _secondPhaseConnection, SocketAddress address) throws IOException
    {
        super(_connectionSelector);
        channel = _channel;
        secondPhaseConnection = _secondPhaseConnection;

        channel.connect(address);

        connectionSelector.registerConnection(channel, this, SelectionKey.OP_CONNECT);
    }

    @Override
    public void perform(SelectionKey key) throws IOException
    {
        if(key.isValid() && key.isConnectable())
        {
            boolean result = channel.finishConnect();

            key.cancel();

            secondPhaseConnection.setIsConnected(result);
        }
    }
}
