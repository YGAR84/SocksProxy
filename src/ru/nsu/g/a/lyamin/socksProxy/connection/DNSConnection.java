package ru.nsu.g.a.lyamin.socksProxy.connection;

import org.xbill.DNS.*;
import ru.nsu.g.a.lyamin.socksProxy.ConnectionSelector;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DNSConnection extends Connection
{
    private DatagramChannel channel;

    private ByteBuffer buffer = ByteBuffer.allocate(4096);

    private Map<Integer, SecondPhaseConnection> needAck = new HashMap<>();

    private Map<Integer, Message> messageSendMap = new HashMap<>();
    private Map<Integer, Message> messageWaitMap = new HashMap<>();

    private SocketAddress dnsRequestAddress;

    private static DNSConnection instance;

    private int counter = 0;

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

            ResolverConfig resolverConfig = new ResolverConfig();

            dnsRequestAddress = resolverConfig.servers().get(0);

            channel.connect(dnsRequestAddress);

            System.out.println("Finish initialization");
        }
        catch (IOException e)
        {
            e.printStackTrace();
        }

    }

    private void reregister() throws ClosedChannelException
    {
        int opts = 0;

        if(messageSendMap.size() > 0) opts |= SelectionKey.OP_WRITE;
        if(messageWaitMap.size() > 0) opts |= SelectionKey.OP_READ;

        System.out.println("DNS OPTS CHANGES: " + opts);

        connectionSelector.registerConnection(channel, this, opts);
    }

    public void resolveAddress(byte[] address, SecondPhaseConnection connection) throws org.xbill.DNS.TextParseException, ClosedChannelException
    {
        needAck.put(counter, connection);

        Message message = new Message();
        Header header = message.getHeader();
        header.setOpcode(Opcode.QUERY);
        header.setID(counter);
        header.setRcode(Rcode.NOERROR);
        header.setFlag(Flags.RD);


        String hostAddress = new String(address);
        String newHostAddress = hostAddress + ".";
        System.out.println("HOST ADDRESS: " + newHostAddress);
        Name name = new Name(newHostAddress);
        Record record = null;
        try
        {
            record = Record.newRecord(name, Type.A, DClass.IN);
        }
        catch(RelativeNameException e)
        {
            System.out.println(e.getMessage());
            needAck.remove(counter);
            try
            {
                connection.setIpResolved(false, null);
            }
            catch (IOException ignored2)
            {
                System.out.println("ioexception in setIpResolved");
            }
            return;
        }

        message.addRecord(record, Section.QUESTION);
        messageSendMap.put(counter, message);
        ++counter;

        reregister();
    }

    @Override
    public void perform(SelectionKey key) throws IOException
    {

        if(key.isValid() && key.isWritable())
        {
            System.out.println(messageSendMap.size());
            Map.Entry<Integer, Message> record = messageSendMap.entrySet().iterator().next();
            int sended = channel.send(ByteBuffer.wrap(record.getValue().toWire()), dnsRequestAddress);

            if(sended > 0)
            {
                messageWaitMap.put(record.getKey(), record.getValue());
                messageSendMap.remove(record.getKey());
            }
        }

        if(key.isValid() && key.isReadable())
        {
            int readen = channel.read(buffer);

            byte[] resp = new byte[buffer.position()];

            System.arraycopy(buffer.array(), 0, resp, 0, resp.length);
            buffer.clear();
            Message response = new Message(resp);

            int id = response.getHeader().getID();
            if(!needAck.containsKey(id)) return;

            SecondPhaseConnection connection = needAck.get(id);
            System.out.println("DNS READ FOR CONNECTION: " + (connection == null));
            needAck.remove(id);
            messageSendMap.remove(id);

            List<Record> records = response.getSection(Section.ANSWER);

            InetAddress resolverIp = null;

            for(Record record : records)
            {
                if(record.getType() == Type.A)
                {
                    try
                    {
                        resolverIp = InetAddress.getByAddress(record.rdataToWireCanonical());
                    }
                    catch(UnknownHostException ignored)
                    {
                        continue;
                    }
                    break;
                }
            }

            connection.setIpResolved( resolverIp != null, resolverIp);
        }

        reregister();

    }

    @Override
    public void terminate(SelectionKey key)
    {
        super.terminate(key);
        connectionSelector.shutdown();
    }
}