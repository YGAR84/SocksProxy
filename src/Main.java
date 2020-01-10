import ru.nsu.g.a.lyamin.socksProxy.Server;

public class Main
{
    public static void main(String[] args)
    {
        Thread proxyServer = new Thread(new Server(8090));

        proxyServer.start();

    }
}
