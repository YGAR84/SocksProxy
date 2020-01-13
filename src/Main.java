import ru.nsu.g.a.lyamin.socksProxy.Server;

public class Main
{
    public static void main(String[] args)
    {
        Thread proxyServer = new Thread(new Server(60088));

        proxyServer.setName("PROXY JIJA");

        proxyServer.start();

    }
}
