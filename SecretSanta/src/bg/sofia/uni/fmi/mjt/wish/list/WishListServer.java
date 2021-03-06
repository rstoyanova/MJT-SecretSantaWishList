package bg.sofia.uni.fmi.mjt.wish.list;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;
import java.util.Set;

public class WishListServer {
    private static final String SERVER_HOST = "localhost";
    private static int SERVER_PORT;
    private static final int BUFFER_SIZE = 1024;
    private static boolean flag = true;
    private static final String NL = System.lineSeparator();

    private static Selector selector;
    private final CommandExecutor commandExecutor;

    public WishListServer(int port) {
        SERVER_PORT = port;
        commandExecutor = new CommandExecutor();
    }

    public void start() {
        Thread customThread = new Thread(() -> {
            try {
                startServer();
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        customThread.start();
    }

    private void startServer() throws IOException {
        try (ServerSocketChannel serverSocketChannel = ServerSocketChannel.open()) {
            serverSocketChannel.bind(new InetSocketAddress(SERVER_HOST, SERVER_PORT));
            serverSocketChannel.configureBlocking(false);

            selector = Selector.open();
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            runServer();

        } catch (IOException e) {
            System.out.println("There is a problem with the server socket" + NL);
            e.printStackTrace();
        }
    }

    private void runServer() throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(BUFFER_SIZE);
        while (flag) {
            int readyChannels = selector.select();
            if (readyChannels == 0) {
                // select() is blocking but may still return with 0, check javadoc
                continue;
            }
            Set<SelectionKey> selectedKeys = selector.selectedKeys();
            Iterator<SelectionKey> keyIterator = selectedKeys.iterator();

            while (keyIterator.hasNext()) {
                SelectionKey key = keyIterator.next();

                if (key.isReadable()) {
                    SocketChannel sc = (SocketChannel) key.channel();
                    buffer.clear();
                    int r = sc.read(buffer);
                    if (r < 0) {
                        System.out.println("Client has closed the connection" + NL);
                        sc.close();
                        continue;
                    }
                    String message = messageToString(buffer);
                    message = message.trim();

                    System.out.println(message);
                    String reply = commandExecutor.executeClientCommand(message, sc);

                    buffer.clear();
                    buffer.put(reply.getBytes());
                    buffer.flip();
                    sc.write(buffer);
                } else if (key.isAcceptable()) {
                    acceptClient(key);
                }
                keyIterator.remove();
            }
        }
    }

    private void acceptClient(SelectionKey key) throws IOException {
        if (key.isAcceptable()) {
            ServerSocketChannel sockChannel = (ServerSocketChannel) key.channel();
            SocketChannel accept = sockChannel.accept();
            accept.configureBlocking(false);
            accept.register(selector, SelectionKey.OP_READ);
        }
    }

    private String messageToString(ByteBuffer buffer) {
        if (buffer == null) {
            throw new IllegalArgumentException("Buffer must not be null!" + NL);
        }
        buffer.flip();
        byte[] byteArray = new byte[buffer.remaining()];
        buffer.get(byteArray);
        return new String(byteArray);
    }

    public void stop() {
        flag = false;
        try {
            if (selector != null) {
                selector.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        WishListServer server = new WishListServer(4444);
        server.start();
    }
}
