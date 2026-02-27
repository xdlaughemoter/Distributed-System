import java.io.*;
import java.net.*;

public class UDPClient {
    public static void main(String[] args) {
        try (DatagramSocket socket = new DatagramSocket()) {

            InetAddress serverIP = InetAddress.getByName("localhost");
            int serverPort = 3000;


            String fileName = "TestFile";
            byte[] sendData = fileName.getBytes() ;
            DatagramPacket requestPacket = new DatagramPacket(sendData, sendData.length, serverIP, serverPort);
            socket.send(requestPacket);
            System.out.println("Request send. ");

            // 2. Ontvang bestand
            FileOutputStream fileOut = new FileOutputStream("UDP_Download.txt");
            byte[] receiveData = new byte[1024];

            System.out.println("downloading...");

            while (true) {
                DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
                socket.receive(receivePacket);


                if (receivePacket.getLength() == 0) {
                    System.out.println("end file.");
                    break; // Breek uit de loop
                }


                fileOut.write(receivePacket.getData(), 0, receivePacket.getLength());
            }

            fileOut.close();
            System.out.println("Done!");

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}