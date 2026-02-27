import java.io.*;
import java.net.*;

public class FileTaskUDPServer extends Thread {
    private InetAddress clientIP;
    private int clientPort;

    public FileTaskUDPServer(InetAddress clientIP, int clientPort) {
        this.clientIP = clientIP;
        this.clientPort = clientPort;
    }

    @Override
    public void run() {
        try (DatagramSocket socket = new DatagramSocket()) {

            File file = new File("TestFile");

            if (file.exists()) {
                FileInputStream fileIn = new FileInputStream(file);
                byte[] buffer = new byte[1024];
                int bytesRead;

                System.out.println("Start sending to " + clientIP + ":" + clientPort);

                while ((bytesRead = fileIn.read(buffer)) != -1) {
                    DatagramPacket sendPacket = new DatagramPacket(buffer, bytesRead, clientIP, clientPort);
                    socket.send(sendPacket);

                    // Als je te snel stuurt, raakt de buffer van de ontvanger vol en
                    // gooit hij pakketjes weg. Een kleine sleep helpt enorm.
                    try { Thread.sleep(5); } catch (InterruptedException e) {}
                }
                fileIn.close();

                DatagramPacket endPacket = new DatagramPacket(new byte[0], 0, clientIP, clientPort);
                socket.send(endPacket);

                System.out.println("File send. " + clientIP);
            } else {
                System.out.println("File not found");
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}