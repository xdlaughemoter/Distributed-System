import java.io.*;
import java.net.*;

public class UDPServer {
    public static void main(String[] args) {
        try (DatagramSocket socket = new DatagramSocket(3000)) {
            System.out.println("UDP Server is started and is waiting for connection..");

            byte[] receiveData = new byte[1024];

            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);
                Thread thread =  new FileTaskUDPServer(receivePacket.getAddress(),receivePacket.getPort());
                thread.start();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}