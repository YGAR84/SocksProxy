package ru.nsu.g.a.lyamin.socksProxy.connection;

import ru.nsu.g.a.lyamin.socksProxy.ConnectionSelector;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;

public abstract class PhaseConnection extends Connection
{
    protected SocketChannel channel;
    protected ArrayList<Byte> buffer = new ArrayList<>();

    public PhaseConnection(ConnectionSelector connectionSelector, SocketChannel _channel)
    {
        super(connectionSelector);
        channel = _channel;
    }
}
