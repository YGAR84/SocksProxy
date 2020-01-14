package ru.nsu.g.a.lyamin.socksProxy.connection;

import ru.nsu.g.a.lyamin.socksProxy.ConnectionSelector;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.nio.channels.spi.AbstractSelectableChannel;

public abstract class Connection
{
    protected ConnectionSelector connectionSelector;

    public Connection(ConnectionSelector _connectionSelector)
    {
        connectionSelector = _connectionSelector;
    }

    public abstract void perform(SelectionKey key) throws IOException;

    public void terminate(SelectionKey key)
    {
        key.cancel();
        connectionSelector.deleteConnection((AbstractSelectableChannel) key.channel());
    }

    public abstract void terminate();
}
