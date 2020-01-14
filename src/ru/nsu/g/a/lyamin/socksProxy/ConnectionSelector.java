package ru.nsu.g.a.lyamin.socksProxy;

import ru.nsu.g.a.lyamin.socksProxy.connection.Connection;

import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.spi.AbstractSelectableChannel;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class ConnectionSelector
{
    private HashMap<AbstractSelectableChannel, Connection> connectionMap = new HashMap<>();
    private Selector selector;

    public ConnectionSelector() throws IOException
    {
        selector = Selector.open();
    }

    public void addConnection(AbstractSelectableChannel channel, Connection connection)
    {
        connectionMap.put(channel, connection);
    }

    public void deleteConnection(AbstractSelectableChannel channel)
    {
        connectionMap.remove(channel);
    }

    public void registerConnection(AbstractSelectableChannel channel, Connection connection, int opts) throws ClosedChannelException
    {
//        System.out.println("is registered:" + channel.isRegistered() + " " + channel.isOpen());
//        if(!channel.isRegistered())
//        {
            channel.register(selector, opts);
//        }

        connectionMap.put(channel, connection);
    }

    public void iterateOverConnections() throws IOException
    {
        selector.select();


        //System.out.println("KEY SIZE: " + selector.keys().size());
        //System.out.println("CONNECTION MAP SIZE: " + connectionMap.size());
//        for(var v : selector.keys())
//        {
//            System.out.println("v: " + v.isValid() +"; a: " + v.isAcceptable() + "; r: " + v.isReadable() +
//                    "; w: " + v.isWritable() + "; c: " + v.isConnectable());
//        }

        Set<SelectionKey> selectedKeys = selector.selectedKeys();

//        int size = selectedKeys.size();
//        if(size >= 2)
//            System.out.println("size: " + size);

        Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

        while(keyIterator.hasNext())
        {
            SelectionKey key = keyIterator.next();

//            if(size >= 2)
//                System.out.println("a: " + key.isAcceptable() + "; r: " + key.isReadable() + " ; w: " + key.isWritable() + " ; v: " + key.isValid());

            Connection connection = connectionMap.get(key.channel());
            if(connection != null)
            {
                connection.perform(key);
            }

            keyIterator.remove();
        }

        //System.out.println("iter is over");
    }

    public void shutdown()
    {
        if(selector != null)
        {
            try
            {
                selector.close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        for(Map.Entry<AbstractSelectableChannel, Connection> entry : connectionMap.entrySet())
        {
            try
            {
                entry.getKey().close();
            }
            catch (IOException e)
            {
                e.printStackTrace();
            }
        }

        connectionMap.clear();
    }
}
