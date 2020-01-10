package ru.nsu.g.a.lyamin.socksProxy.connection;

import org.xbill.DNS.*;
import ru.nsu.g.a.lyamin.socksProxy.ConnectionSelector;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DNSConnection extends Connection
{
    private DatagramChannel channel;

    private ByteBuffer buffer = ByteBuffer.allocate(1024);

    private Map<Integer, SecondPhaseConnection> needAck = new HashMap<>();

    private Map<Integer, Message> messageMap = new HashMap<>();

    private SocketAddress dnsRequestAddres;

    private static DNSConnection instance;

    public static void createInstance(ConnectionSelector _connectionSelector)
    {
        if(instance == null)
        {
            instance = new DNSConnection(_connectionSelector);
        }
    }

    public static DNSConnection getInstance()
    {
        if(instance == null)
        {
            throw new NullPointerException("DNSConnection is null");
        }
        return instance;
    }

    private DNSConnection(ConnectionSelector _connectionSelector)
    {
        super(_connectionSelector);

        try
        {
            channel = DatagramChannel.open();
            channel.configureBlocking(false);

            connectionSelector.registerConnection(channel, this, SelectionKey.OP_READ | SelectionKey.OP_WRITE);

            ResolverConfig resolverConfig = new ResolverConfig();

//            System.out.println("Size: " + resolverConfig.servers().size());
//            for(InetSocketAddress addr : resolverConfig.servers())
//            {
//                System.out.println(addr.getAddress().getHostName() + ":" + addr.getPort());
//            }
//            ResolverConfig resolverConfig = new ResolverConfig();


            dnsRequestAddres = resolverConfig.servers().get(0);
//            System.out.println(.getHostName());
//            channel.bind(resolverConfig.servers().get(0));
            System.out.println("Finish initialization");
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

    }

    public void resolveAddress(byte[] address, SecondPhaseConnection connection) throws org.xbill.DNS.TextParseException
    {
        int id = 1;
        while(true)
        {
            if(!needAck.containsKey(id))
            {
                needAck.put(id, connection);
                break;
            }
            ++id;
        }

        Message message = new Message();
        Header header = message.getHeader();
        header.setOpcode(Opcode.QUERY);
        header.setID(id);
        header.setRcode(Rcode.NOERROR);
        header.setFlag(Flags.RD);

        Name name = new Name(new String(address));
        message.addRecord(Record.newRecord(name, Type.A, DClass.IN), Section.QUESTION);
        messageMap.put(id, message);
    }

    @Override
    public void perform(SelectionKey key) throws IOException
    {

        if(!key.isValid()) return;

        if(key.isWritable() && (messageMap.size() != 0))
        {
            Map.Entry<Integer, Message> record = messageMap.entrySet().iterator().next();
            channel.send(ByteBuffer.wrap(record.getValue().toWire()), dnsRequestAddres);
        }

        if(key.isReadable() && (messageMap.size() != 0))
        {
            channel.read(buffer);
            byte[] resp = new byte[buffer.position() + 1];
            System.arraycopy(buffer.array(), 0, resp, 0, resp.length);
            Message response = new Message(resp);

            int id = response.getHeader().getID();
            SecondPhaseConnection connection = needAck.get(id);
            needAck.remove(id);
            messageMap.remove(id);

            List<Record> records = response.getSection(Section.ANSWER);

            for(Record record : records)
            {
                if(record.getType() == Type.A)
                {
                    InetAddress addr = InetAddress.getByName(record.getName().toString());
                    connection.setIp(addr);

                }
            }
        }
    }

    @Override
    public void terminate(SelectionKey key)
    {
        super.terminate(key);
        connectionSelector.shutdown();
    }
}