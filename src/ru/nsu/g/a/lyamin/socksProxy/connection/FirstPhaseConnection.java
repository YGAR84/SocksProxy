package ru.nsu.g.a.lyamin.socksProxy.connection;

import ru.nsu.g.a.lyamin.socksProxy.ConnectionSelector;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;

public class FirstPhaseConnection extends PhaseConnection
{
    private boolean answerIsReady = false;
    private ByteBuffer buffer = ByteBuffer.wrap(new byte[257]);

    private byte[] answer = new byte[2];

    public FirstPhaseConnection(ConnectionSelector connectionSelector, SocketChannel channel)
    {
        super(connectionSelector, channel);
    }

    @Override
    public void perform(SelectionKey key) throws IOException
    {
        if(key.isValid() && key.isReadable())
        {
            channel.read(buffer);
            System.out.println(Arrays.toString(buffer.array()));
            //System.out.println(buffer.position());
            if(buffer.position() >= 2)
            {
                if(buffer.get(0) != 0x05) { terminate(key); }

//                System.out.println("I'm here");

                int numOfModes = (int)buffer.get(1);
//                System.out.println("Num of modes: " + numOfModes);
                if(buffer.position() > 2 + numOfModes) { terminate(key); }
                else if(buffer.position() == 2 + numOfModes)
                {
                    boolean hasAuth = false;
                    for(int i = 2; i <= buffer.position(); ++i)
                    {
                        if(buffer.get(i) == 0x00)
                        {
                            hasAuth = true;
                        }
                    }

                    System.out.println("Has auth: " + hasAuth);
                    createAnswer(hasAuth);

                    key.cancel();
                    connectionSelector.registerConnection(channel, this, SelectionKey.OP_WRITE);
                }
            }
        }

        if(key.isValid() && key.isWritable())
        {

            channel.write(ByteBuffer.wrap(answer));

            key.cancel();

            SecondPhaseConnection secondPhaseConnection = new SecondPhaseConnection(connectionSelector, channel);
            connectionSelector.registerConnection(channel, secondPhaseConnection, SelectionKey.OP_READ);

//            connectionSelector.addConnection(channel, secondPhaseConnection);
        }

        //connectionSelector.registerConnection(channel, this, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
    }

    private void createAnswer(boolean hasAuth)
    {
        answerIsReady = true;
        answer[0] = 0x05;
        answer[1] = (byte) ( (hasAuth) ? 0x00 : 0xFF );
    }
}
